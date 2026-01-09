package server

import (
	"context"
	"net/http"
	"strconv"
	"time"

	db "fast/pin/internal/db/sqlc"

	"github.com/jackc/pgx/v5/pgtype"
)

// Error message constants
const errRouteNotFound = "route not found for unit"

// =============================================================================
// Request/Response DTOs for Routing
// =============================================================================

// CalculateRouteRequest is the request body for route calculation
type CalculateRouteRequest struct {
	FromLat float64 `json:"from_lat" validate:"required,latitude"`
	FromLon float64 `json:"from_lon" validate:"required,longitude"`
	ToLat   float64 `json:"to_lat" validate:"required,latitude"`
	ToLon   float64 `json:"to_lon" validate:"required,longitude"`
}

// CalculateRouteResponse is the response from route calculation
type CalculateRouteResponse struct {
	RouteGeoJSON             string  `json:"route_geojson"`
	RouteLengthMeters        float64 `json:"route_length_meters"`
	EstimatedDurationSeconds float64 `json:"estimated_duration_seconds"`
}

// SaveUnitRouteRequest saves a calculated route for a unit
type SaveUnitRouteRequest struct {
	InterventionID           *string `json:"intervention_id"`
	RouteGeoJSON             string  `json:"route_geojson" validate:"required"`
	RouteLengthMeters        float64 `json:"route_length_meters" validate:"required,gte=0"`
	EstimatedDurationSeconds float64 `json:"estimated_duration_seconds" validate:"required,gte=0"`
}

// UnitRouteResponse is the response for unit route queries
type UnitRouteResponse struct {
	UnitID                   string   `json:"unit_id"`
	InterventionID           *string  `json:"intervention_id,omitempty"`
	RouteGeoJSON             string   `json:"route_geojson"`
	RouteLengthMeters        float64  `json:"route_length_meters"`
	EstimatedDurationSeconds float64  `json:"estimated_duration_seconds"`
	ProgressPercent          float64  `json:"progress_percent"`
	CurrentLat               *float64 `json:"current_lat,omitempty"`
	CurrentLon               *float64 `json:"current_lon,omitempty"`
	RemainingMeters          *float64 `json:"remaining_meters,omitempty"`
	RemainingSeconds         *float64 `json:"remaining_seconds,omitempty"`
	Severity                 *int32   `json:"severity,omitempty"`
}

// UpdateProgressRequest updates the progress percentage
type UpdateProgressRequest struct {
	ProgressPercent float64 `json:"progress_percent" validate:"required,gte=0,lte=100"`
}

// UpdateProgressResponse returns the new position after progress update
type UpdateProgressResponse struct {
	UnitID           string  `json:"unit_id"`
	ProgressPercent  float64 `json:"progress_percent"`
	CurrentLat       float64 `json:"current_lat"`
	CurrentLon       float64 `json:"current_lon"`
	RemainingMeters  float64 `json:"remaining_meters"`
	RemainingSeconds float64 `json:"remaining_seconds"`
}

// PositionResponse is a simple lat/lon response
type PositionResponse struct {
	Lat float64 `json:"lat"`
	Lon float64 `json:"lon"`
}

// =============================================================================
// Route Calculation (Raw SQL for pgRouting)
// =============================================================================

const calculateRouteSQL = `
WITH 
-- Only consider vertices in the main connected component (component with most vertices)
main_component AS (
    SELECT component FROM (
        SELECT source AS node FROM routing_ways
        UNION
        SELECT target FROM routing_ways
    ) nodes
    JOIN (
        SELECT node, component FROM pgr_connectedComponents(
            'SELECT gid AS id, source, target, cost_s AS cost FROM routing_ways WHERE cost_s > 0'
        )
    ) cc ON cc.node = nodes.node
    GROUP BY component
    ORDER BY COUNT(*) DESC
    LIMIT 1
),
connected_vertices AS (
    SELECT v.id, v.the_geom
    FROM routing_ways_vertices_pgr v
    JOIN (
        SELECT node FROM pgr_connectedComponents(
            'SELECT gid AS id, source, target, cost_s AS cost FROM routing_ways WHERE cost_s > 0'
        ) WHERE component = (SELECT component FROM main_component)
    ) cc ON cc.node = v.id
),
start_vertex AS (
    SELECT id FROM connected_vertices
    ORDER BY the_geom <-> ST_SetSRID(ST_MakePoint($1, $2), 4326)
    LIMIT 1
),
end_vertex AS (
    SELECT id FROM connected_vertices
    ORDER BY the_geom <-> ST_SetSRID(ST_MakePoint($3, $4), 4326)
    LIMIT 1
),
route_segments AS (
    SELECT 
        rw.geom,
        rw.length_m,
        rw.cost_s,
        path.seq
    FROM pgr_dijkstra(
        'SELECT gid AS id, source, target, cost_s AS cost, reverse_cost_s AS reverse_cost FROM routing_ways',
        (SELECT id FROM start_vertex),
        (SELECT id FROM end_vertex),
        directed := true
    ) AS path
    JOIN routing_ways rw ON rw.gid = path.edge
    WHERE path.edge > 0
)
SELECT 
    COALESCE(ST_AsGeoJSON(ST_Simplify(ST_MakeLine(geom ORDER BY seq), 0.0005))::text, '') AS route_geojson,
    COALESCE(SUM(length_m), 0)::double precision AS route_length_meters,
    COALESCE(SUM(cost_s), 0)::double precision AS estimated_duration_seconds
FROM route_segments
`

// =============================================================================
// Handlers
// =============================================================================

// handleCalculateRoute calculates a route between two points using pgRouting
func (s *Server) handleCalculateRoute(w http.ResponseWriter, r *http.Request) {
	var req CalculateRouteRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	var result CalculateRouteResponse
	err := s.pool.QueryRow(r.Context(), calculateRouteSQL, req.FromLon, req.FromLat, req.ToLon, req.ToLat).
		Scan(&result.RouteGeoJSON, &result.RouteLengthMeters, &result.EstimatedDurationSeconds)

	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to calculate route", err.Error())
		return
	}

	// Check if route was found (empty geojson means no route)
	if result.RouteGeoJSON == "" || result.RouteLengthMeters == 0 {
		s.writeError(w, http.StatusNotFound, "no route found between points", nil)
		return
	}

	s.writeJSON(w, http.StatusOK, result)
}

// handleGetUnitRoute gets the stored route for a unit with current interpolated position
func (s *Server) handleGetUnitRoute(w http.ResponseWriter, r *http.Request) {
	unitUUID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidUnitID, err.Error())
		return
	}

	route, err := s.queries.GetUnitRoute(r.Context(), unitUUID)
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, errRouteNotFound, nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to get route", err.Error())
		return
	}

	currentLat := route.CurrentLat
	currentLon := route.CurrentLon
	remainingMeters := route.RemainingMeters
	remainingSeconds := route.RemainingSeconds
	resp := UnitRouteResponse{
		UnitID:                   uuidString(route.UnitID),
		RouteGeoJSON:             route.RouteGeojson,
		RouteLengthMeters:        route.RouteLengthMeters,
		EstimatedDurationSeconds: route.EstimatedDurationSeconds,
		ProgressPercent:          route.ProgressPercent,
		CurrentLat:               &currentLat,
		CurrentLon:               &currentLon,
		RemainingMeters:          &remainingMeters,
		RemainingSeconds:         &remainingSeconds,
		Severity:                 route.Severity,
	}

	if route.InterventionID.Valid {
		id := uuidString(route.InterventionID)
		resp.InterventionID = &id
	}

	s.writeJSON(w, http.StatusOK, resp)
}

// handleSaveUnitRoute saves a calculated route for a unit
func (s *Server) handleSaveUnitRoute(w http.ResponseWriter, r *http.Request) {
	unitUUID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidUnitID, err.Error())
		return
	}

	var req SaveUnitRouteRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	params := db.SaveUnitRouteParams{
		UnitID:                   unitUUID,
		RouteGeojson:             req.RouteGeoJSON,
		RouteLengthMeters:        req.RouteLengthMeters,
		EstimatedDurationSeconds: req.EstimatedDurationSeconds,
	}

	if req.InterventionID != nil {
		intUUID, err := pgUUIDFromString(*req.InterventionID)
		if err == nil {
			params.InterventionID = intUUID
		}
	}

	route, err := s.queries.SaveUnitRoute(r.Context(), params)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to save route", err.Error())
		return
	}

	resp := UnitRouteResponse{
		UnitID:                   uuidString(route.UnitID),
		RouteGeoJSON:             route.RouteGeojson,
		RouteLengthMeters:        route.RouteLengthMeters,
		EstimatedDurationSeconds: route.EstimatedDurationSeconds,
		ProgressPercent:          route.ProgressPercent,
	}

	if route.InterventionID.Valid {
		id := uuidString(route.InterventionID)
		resp.InterventionID = &id
	}

	s.writeJSON(w, http.StatusCreated, resp)
}

// handleUpdateRouteProgress updates the progress percentage and returns new position
func (s *Server) handleUpdateRouteProgress(w http.ResponseWriter, r *http.Request) {
	unitUUID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidUnitID, err.Error())
		return
	}

	var req UpdateProgressRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	result, err := s.queries.UpdateRouteProgress(r.Context(), db.UpdateRouteProgressParams{
		ProgressPercent: req.ProgressPercent,
		UnitID:          unitUUID,
	})
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, errRouteNotFound, nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to update progress", err.Error())
		return
	}

	s.writeJSON(w, http.StatusOK, UpdateProgressResponse{
		UnitID:           uuidString(result.UnitID),
		ProgressPercent:  result.ProgressPercent,
		CurrentLat:       result.CurrentLat,
		CurrentLon:       result.CurrentLon,
		RemainingMeters:  result.RemainingMeters,
		RemainingSeconds: result.RemainingSeconds,
	})
}

// handleDeleteUnitRoute deletes the route for a unit
func (s *Server) handleDeleteUnitRoute(w http.ResponseWriter, r *http.Request) {
	unitUUID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidUnitID, err.Error())
		return
	}

	err = s.queries.DeleteUnitRoute(r.Context(), unitUUID)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to delete route", err.Error())
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// handleGetRoutePosition gets the interpolated position at a specific progress percentage
func (s *Server) handleGetRoutePosition(w http.ResponseWriter, r *http.Request) {
	unitUUID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidUnitID, err.Error())
		return
	}

	progressStr := r.URL.Query().Get("progress")
	var progress float64
	if progressStr != "" {
		progress, err = strconv.ParseFloat(progressStr, 64)
		if err != nil {
			s.writeError(w, http.StatusBadRequest, "invalid progress value", err.Error())
			return
		}
	}

	pos, err := s.queries.GetRoutePosition(r.Context(), db.GetRoutePositionParams{
		ProgressPercent: progress,
		UnitID:          unitUUID,
	})
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, errRouteNotFound, nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to get position", err.Error())
		return
	}

	s.writeJSON(w, http.StatusOK, PositionResponse{
		Lat: pos.Lat,
		Lon: pos.Lon,
	})
}

// calculateAndSaveRouteForAssignment calculates a route from unit to event location and saves it.
// Called asynchronously when a unit is assigned to an intervention.
func (s *Server) calculateAndSaveRouteForAssignment(ctx context.Context, interventionID, unitID pgtype.UUID) {
	startTime := time.Now()
	s.log.Info().
		Str("unit_id", uuidString(unitID)).
		Str("intervention_id", uuidString(interventionID)).
		Msg("starting route calculation for assignment")

	// 1. Get all route calculation data in a single query (unit position + event destination)
	data, err := s.queries.GetRouteCalculationData(ctx, db.GetRouteCalculationDataParams{
		UnitID:         unitID,
		InterventionID: interventionID,
	})
	if err != nil {
		s.log.Error().Err(err).
			Str("intervention_id", uuidString(interventionID)).
			Str("unit_id", uuidString(unitID)).
			Dur("elapsed_ms", time.Since(startTime)).
			Msg("failed to get route calculation data")
		return
	}

	// 2. Calculate the route using pgRouting
	var routeResult struct {
		RouteGeoJSON             string  `db:"route_geojson"`
		RouteLengthMeters        float64 `db:"route_length_meters"`
		EstimatedDurationSeconds float64 `db:"estimated_duration_seconds"`
	}

	err = s.pool.QueryRow(ctx, calculateRouteSQL, data.UnitLon, data.UnitLat, data.EventLon, data.EventLat).
		Scan(&routeResult.RouteGeoJSON, &routeResult.RouteLengthMeters, &routeResult.EstimatedDurationSeconds)

	if err != nil {
		s.log.Error().Err(err).
			Str("unit_id", uuidString(unitID)).
			Float64("from_lat", data.UnitLat).
			Float64("from_lon", data.UnitLon).
			Float64("to_lat", data.EventLat).
			Float64("to_lon", data.EventLon).
			Dur("elapsed_ms", time.Since(startTime)).
			Msg("failed to calculate route")
		return
	}

	// Check if route was found
	if routeResult.RouteGeoJSON == "" || routeResult.RouteLengthMeters == 0 {
		s.log.Warn().
			Str("unit_id", uuidString(unitID)).
			Str("intervention_id", uuidString(interventionID)).
			Dur("elapsed_ms", time.Since(startTime)).
			Msg("no route found between unit and event location")
		return
	}

	// 3. Save the route for the unit
	_, err = s.queries.SaveUnitRoute(ctx, db.SaveUnitRouteParams{
		UnitID:                   unitID,
		InterventionID:           interventionID,
		RouteGeojson:             routeResult.RouteGeoJSON,
		RouteLengthMeters:        routeResult.RouteLengthMeters,
		EstimatedDurationSeconds: routeResult.EstimatedDurationSeconds,
	})

	if err != nil {
		s.log.Error().Err(err).
			Str("unit_id", uuidString(unitID)).
			Dur("elapsed_ms", time.Since(startTime)).
			Msg("failed to save route")
		return
	}

	elapsed := time.Since(startTime)
	s.log.Info().
		Str("unit_id", uuidString(unitID)).
		Str("intervention_id", uuidString(interventionID)).
		Float64("length_m", routeResult.RouteLengthMeters).
		Float64("duration_s", routeResult.EstimatedDurationSeconds).
		Dur("elapsed_ms", elapsed).
		Msg("route calculation completed for assignment")
}
