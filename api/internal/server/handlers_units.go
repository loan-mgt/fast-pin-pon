package server

import (
	"net/http"
	"strconv"
	"strings"
	"time"

	db "fast/pin/internal/db/sqlc"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgtype"
)

const errUnitNotFound = "unit not found"

type CreateUnitRequest struct {
	CallSign     string  `json:"call_sign" validate:"required"`
	UnitTypeCode string  `json:"unit_type_code" validate:"required"`
	HomeBase     *string `json:"home_base"`
	LocationID   *string `json:"location_id"`
	Status       string  `json:"status" validate:"required,oneof=available available_hidden under_way on_site unavailable offline"`
	Latitude     float64 `json:"latitude" validate:"required,latitude"`
	Longitude    float64 `json:"longitude" validate:"required,longitude"`
}

type UpdateUnitStatusRequest struct {
	Status string `json:"status" validate:"required,oneof=available available_hidden under_way on_site unavailable offline"`
}

type UpdateUnitLocationRequest struct {
	Latitude   float64    `json:"latitude" validate:"required,latitude"`
	Longitude  float64    `json:"longitude" validate:"required,longitude"`
	RecordedAt *time.Time `json:"recorded_at"`
}

type UpdateUnitStationRequest struct {
	LocationID *string `json:"location_id"`
}

type AssignMicrobitRequest struct {
	MicrobitID string `json:"microbit_id" validate:"required,min=1,max=50"`
}

type UnitTelemetryRequest struct {
	Latitude  float64  `json:"latitude" validate:"required,latitude"`
	Longitude float64  `json:"longitude" validate:"required,longitude"`
	Heading   *int32   `json:"heading"`
	SpeedKMH  *float64 `json:"speed_kmh" validate:"omitempty,gte=0"`
	Status    RawJSON  `json:"status_snapshot"`
}

// handleListUnits godoc
// @Title List units
// @Description Returns all operational units with their current status and location.
// @Resource Units
// @Produce json
// @Success 200 {array} UnitResponse
// @Failure 500 {object} APIError
// @Route /v1/units [get]
func (s *Server) handleListUnits(w http.ResponseWriter, r *http.Request) {
	rows, err := s.queries.ListUnits(r.Context())
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list units", err.Error())
		return
	}

	resp := make([]UnitResponse, 0, len(rows))
	for _, row := range rows {
		resp = append(resp, mapUnitRow(unitRowData{
			ID:           row.ID,
			CallSign:     row.CallSign,
			UnitTypeCode: row.UnitTypeCode,
			HomeBase:     row.HomeBase,
			LocationID:   row.LocationID,
			Status:       row.Status,
			MicrobitID:   row.MicrobitID,
			Longitude:    row.Longitude,
			Latitude:     row.Latitude,
			LastContact:  row.LastContactAt,
			CreatedAt:    row.CreatedAt,
			UpdatedAt:    row.UpdatedAt,
		}))
	}

	s.writeJSON(w, http.StatusOK, resp)
}

// handleCreateUnit godoc
// @Title Create unit
// @Description Registers a new responder unit.
// @Resource Units
// @Accept json
// @Produce json
// @Param request body CreateUnitRequest true "Unit payload"
// @Success 201 {object} UnitResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/units [post]
func (s *Server) handleCreateUnit(w http.ResponseWriter, r *http.Request) {
	var req CreateUnitRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	now := time.Now().UTC()
	lastContact := timestamptzFromPtr(&now)

	// If no location_id is provided, find the nearest station
	var locationID pgtype.UUID
	if req.LocationID != nil && *req.LocationID != "" {
		locationID = pgUUIDFromStringOptional(req.LocationID)
	} else {
		// Find the nearest station based on the unit's position
		nearestStation, err := s.queries.GetNearestStation(r.Context(), db.GetNearestStationParams{
			Longitude: req.Longitude,
			Latitude:  req.Latitude,
		})
		if err == nil {
			locationID = nearestStation.ID
		}
		// If no station found, locationID remains empty (valid)
	}

	params := db.CreateUnitParams{
		CallSign:      req.CallSign,
		UnitTypeCode:  req.UnitTypeCode,
		HomeBase:      req.HomeBase,
		LocationID:    locationID,
		Status:        db.UnitStatus(req.Status),
		Longitude:     req.Longitude,
		Latitude:      req.Latitude,
		LastContactAt: lastContact,
	}

	row, err := s.queries.CreateUnit(r.Context(), params)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to create unit", err.Error())
		return
	}

	summary := mapCreateUnitRow(row)
	s.writeJSON(w, http.StatusCreated, summary)
}

// handleDeleteUnit godoc
// @Title Delete unit
// @Description Deletes a responder unit by ID, including all related telemetry and assignments.
// @Resource Units
// @Produce json
// @Param unitID path string true "Unit UUID"
// @Success 204 {string} string "No Content"
// @Failure 400 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/units/{unitID} [delete]
func (s *Server) handleDeleteUnit(w http.ResponseWriter, r *http.Request) {
	unitID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid unit id", err.Error())
		return
	}

	ctx := r.Context()

	// Delete related data first (foreign key constraints)
	if err := s.queries.DeleteUnitAssignments(ctx, unitID); err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to delete unit assignments", err.Error())
		return
	}

	if err := s.queries.DeleteUnitTelemetry(ctx, unitID); err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to delete unit telemetry", err.Error())
		return
	}

	if err := s.queries.DeleteUnit(ctx, unitID); err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to delete unit", err.Error())
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// handleUpdateUnitStatus godoc
// @Title Update unit status
// @Description Updates the dispatch readiness of a unit.
// @Resource Units
// @Accept json
// @Produce json
// @Param unitID path string true "Unit ID"
// @Param request body UpdateUnitStatusRequest true "Status payload"
// @Success 200 {object} UnitResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/units/{unitID}/status [patch]
func (s *Server) handleUpdateUnitStatus(w http.ResponseWriter, r *http.Request) {
	unitID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidUnitID, err.Error())
		return
	}

	var req UpdateUnitStatusRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	row, err := s.queries.UpdateUnitStatus(r.Context(), db.UpdateUnitStatusParams{
		ID:     unitID,
		Status: db.UnitStatus(req.Status),
	})
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, errUnitNotFound, nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to update unit", err.Error())
		return
	}

	// Auto-delete route when unit status changes to something other than under_way
	// (route is deleted when unit arrives on_site or becomes available/unavailable/offline)
	if req.Status != "under_way" {
		_ = s.queries.DeleteUnitRoute(r.Context(), unitID) // Ignore error - route may not exist
	}

	s.writeJSON(w, http.StatusOK, mapUnitRow(unitRowData{
		ID:           row.ID,
		CallSign:     row.CallSign,
		UnitTypeCode: row.UnitTypeCode,
		HomeBase:     row.HomeBase,
		LocationID:   row.LocationID,
		Status:       row.Status,
		MicrobitID:   row.MicrobitID,
		Longitude:    row.Longitude,
		Latitude:     row.Latitude,
		LastContact:  row.LastContactAt,
		CreatedAt:    row.CreatedAt,
		UpdatedAt:    row.UpdatedAt,
	}))
}

// handleUpdateUnitLocation godoc
// @Title Update unit location
// @Description Updates the last known location for a unit.
// @Resource Units
// @Accept json
// @Produce json
// @Param unitID path string true "Unit ID"
// @Param request body UpdateUnitLocationRequest true "Location payload"
// @Success 200 {object} UnitResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/units/{unitID}/location [patch]
func (s *Server) handleUpdateUnitLocation(w http.ResponseWriter, r *http.Request) {
	unitID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidUnitID, err.Error())
		return
	}

	var req UpdateUnitLocationRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	row, err := s.queries.UpdateUnitLocation(r.Context(), db.UpdateUnitLocationParams{
		Longitude:   req.Longitude,
		Latitude:    req.Latitude,
		ContactTime: timestamptzFromPtr(req.RecordedAt),
		ID:          unitID,
	})
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, errUnitNotFound, nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to update unit location", err.Error())
		return
	}

	s.writeJSON(w, http.StatusOK, mapUnitRow(unitRowData{
		ID:           row.ID,
		CallSign:     row.CallSign,
		UnitTypeCode: row.UnitTypeCode,
		HomeBase:     row.HomeBase,
		LocationID:   row.LocationID,
		Status:       row.Status,
		MicrobitID:   row.MicrobitID,
		Longitude:    row.Longitude,
		Latitude:     row.Latitude,
		LastContact:  row.LastContactAt,
		CreatedAt:    row.CreatedAt,
		UpdatedAt:    row.UpdatedAt,
	}))
}

// handleUpdateUnitStation godoc
// @Title Update unit station
// @Description Updates the fire station (location) assignment for a unit.
// @Resource Units
// @Accept json
// @Produce json
// @Param unitID path string true "Unit ID"
// @Param request body UpdateUnitStationRequest true "Station payload"
// @Success 200 {object} UnitResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/units/{unitID}/station [patch]
func (s *Server) handleUpdateUnitStation(w http.ResponseWriter, r *http.Request) {
	unitID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidUnitID, err.Error())
		return
	}

	var req UpdateUnitStationRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	row, err := s.queries.UpdateUnitStation(r.Context(), db.UpdateUnitStationParams{
		ID:         unitID,
		LocationID: pgUUIDFromStringOptional(req.LocationID),
	})
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, errUnitNotFound, nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to update unit station", err.Error())
		return
	}

	s.writeJSON(w, http.StatusOK, mapUnitRow(unitRowData{
		ID:           row.ID,
		CallSign:     row.CallSign,
		UnitTypeCode: row.UnitTypeCode,
		HomeBase:     row.HomeBase,
		LocationID:   row.LocationID,
		Status:       row.Status,
		MicrobitID:   row.MicrobitID,
		Longitude:    row.Longitude,
		Latitude:     row.Latitude,
		LastContact:  row.LastContactAt,
		CreatedAt:    row.CreatedAt,
		UpdatedAt:    row.UpdatedAt,
	}))
}

// handleInsertTelemetry godoc
// @Title Submit telemetry
// @Description Stores a telemetry snapshot for a unit.
// @Resource Units
// @Accept json
// @Produce json
// @Param unitID path string true "Unit ID"
// @Param request body UnitTelemetryRequest true "Telemetry payload"
// @Success 201 {object} TelemetryResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/units/{unitID}/telemetry [post]
func (s *Server) handleInsertTelemetry(w http.ResponseWriter, r *http.Request) {
	unitID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidUnitID, err.Error())
		return
	}

	var req UnitTelemetryRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	row, err := s.queries.InsertUnitTelemetry(r.Context(), db.InsertUnitTelemetryParams{
		UnitID:         unitID,
		Longitude:      req.Longitude,
		Latitude:       req.Latitude,
		Heading:        req.Heading,
		SpeedKmh:       req.SpeedKMH,
		StatusSnapshot: rawJSONOrEmpty(req.Status),
	})
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to store telemetry", err.Error())
		return
	}

	resp := TelemetryResponse{
		ID:         row.ID,
		UnitID:     uuidString(row.UnitID),
		RecordedAt: row.RecordedAt.Time,
		Location:   GeoPoint{Latitude: row.Latitude, Longitude: row.Longitude},
		Heading:    row.Heading,
		SpeedKMH:   row.SpeedKmh,
		Status:     RawJSON(row.StatusSnapshot),
	}

	s.writeJSON(w, http.StatusCreated, resp)
}

type unitRowData struct {
	ID             pgtype.UUID
	CallSign       string
	UnitTypeCode   string
	HomeBase       *string
	LocationID     pgtype.UUID
	Status         db.UnitStatus
	MicrobitID     *string
	Longitude      float64
	Latitude       float64
	DistanceMeters *float64
	LastContact    pgtype.Timestamptz
	CreatedAt      pgtype.Timestamptz
	UpdatedAt      pgtype.Timestamptz
}

func mapUnitRow(data unitRowData) UnitResponse {
	return UnitResponse{
		ID:             uuidString(data.ID),
		CallSign:       data.CallSign,
		UnitTypeCode:   data.UnitTypeCode,
		HomeBase:       optionalString(data.HomeBase),
		LocationID:     uuidStringOptional(data.LocationID),
		Status:         string(data.Status),
		MicrobitID:     optionalString(data.MicrobitID),
		Location:       GeoPoint{Latitude: data.Latitude, Longitude: data.Longitude},
		DistanceMeters: data.DistanceMeters,
		LastContact:    timestamptzPtr(data.LastContact),
		CreatedAt:      data.CreatedAt.Time,
		UpdatedAt:      data.UpdatedAt.Time,
	}
}

func mapCreateUnitRow(row db.CreateUnitRow) UnitResponse {
	return UnitResponse{
		ID:           uuidString(row.ID),
		CallSign:     row.CallSign,
		UnitTypeCode: row.UnitTypeCode,
		HomeBase:     optionalString(row.HomeBase),
		LocationID:   uuidStringOptional(row.LocationID),
		Status:       string(row.Status),
		MicrobitID:   optionalString(row.MicrobitID),
		Location: GeoPoint{
			Latitude:  row.Latitude,
			Longitude: row.Longitude,
		},
		LastContact: timestamptzPtr(row.LastContactAt),
		CreatedAt:   row.CreatedAt.Time,
		UpdatedAt:   row.UpdatedAt.Time,
	}
}

// handleAssignMicrobit godoc
// @Title Assign microbit to unit
// @Description Assigns a micro:bit device to control a unit's status.
// @Resource Units
// @Accept json
// @Produce json
// @Param unitID path string true "Unit ID"
// @Param request body AssignMicrobitRequest true "Microbit assignment payload"
// @Success 200 {object} UnitResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 409 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/units/{unitID}/microbit [put]
func (s *Server) handleAssignMicrobit(w http.ResponseWriter, r *http.Request) {
	unitID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidUnitID, err.Error())
		return
	}

	var req AssignMicrobitRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	row, err := s.queries.AssignMicrobit(r.Context(), db.AssignMicrobitParams{
		ID:         unitID,
		MicrobitID: &req.MicrobitID,
	})
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, errUnitNotFound, nil)
			return
		}
		if isUniqueViolation(err) {
			s.writeError(w, http.StatusConflict, "microbit already assigned to another unit", nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to assign microbit", err.Error())
		return
	}

	s.writeJSON(w, http.StatusOK, mapUnitRow(unitRowData{
		ID:           row.ID,
		CallSign:     row.CallSign,
		UnitTypeCode: row.UnitTypeCode,
		HomeBase:     row.HomeBase,
		LocationID:   row.LocationID,
		Status:       row.Status,
		MicrobitID:   row.MicrobitID,
		Longitude:    row.Longitude,
		Latitude:     row.Latitude,
		LastContact:  row.LastContactAt,
		CreatedAt:    row.CreatedAt,
		UpdatedAt:    row.UpdatedAt,
	}))
}

// handleUnassignMicrobit godoc
// @Title Unassign microbit from unit
// @Description Removes the micro:bit device assignment from a unit.
// @Resource Units
// @Produce json
// @Param unitID path string true "Unit ID"
// @Success 200 {object} UnitResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/units/{unitID}/microbit [delete]
func (s *Server) handleUnassignMicrobit(w http.ResponseWriter, r *http.Request) {
	unitID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidUnitID, err.Error())
		return
	}

	row, err := s.queries.UnassignMicrobit(r.Context(), unitID)
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, errUnitNotFound, nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to unassign microbit", err.Error())
		return
	}

	s.writeJSON(w, http.StatusOK, mapUnitRow(unitRowData{
		ID:           row.ID,
		CallSign:     row.CallSign,
		UnitTypeCode: row.UnitTypeCode,
		HomeBase:     row.HomeBase,
		LocationID:   row.LocationID,
		Status:       row.Status,
		MicrobitID:   row.MicrobitID,
		Longitude:    row.Longitude,
		Latitude:     row.Latitude,
		LastContact:  row.LastContactAt,
		CreatedAt:    row.CreatedAt,
		UpdatedAt:    row.UpdatedAt,
	}))
}

// handleGetUnitByMicrobit godoc
// @Title Get unit by microbit ID
// @Description Retrieves a unit by its assigned microbit ID.
// @Resource Units
// @Param microbitID path string true "Microbit ID"
// @Produce json
// @Success 200 {object} UnitResponse
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/units/by-microbit/{microbitID} [get]
func (s *Server) handleGetUnitByMicrobit(w http.ResponseWriter, r *http.Request) {
	microbitID := chi.URLParam(r, "microbitID")
	if microbitID == "" {
		s.writeError(w, http.StatusBadRequest, "microbit_id is required", nil)
		return
	}

	row, err := s.queries.GetUnitByMicrobitID(r.Context(), &microbitID)
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, "unit not found for this microbit", nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to get unit by microbit", err.Error())
		return
	}

	s.writeJSON(w, http.StatusOK, mapUnitRow(unitRowData{
		ID:           row.ID,
		CallSign:     row.CallSign,
		UnitTypeCode: row.UnitTypeCode,
		HomeBase:     row.HomeBase,
		LocationID:   row.LocationID,
		Status:       row.Status,
		MicrobitID:   row.MicrobitID,
		Longitude:    row.Longitude,
		Latitude:     row.Latitude,
		LastContact:  row.LastContactAt,
		CreatedAt:    row.CreatedAt,
		UpdatedAt:    row.UpdatedAt,
	}))
}

// handleListUnitsNearby godoc
// @Title List available units nearby
// @Description Returns available units sorted by distance to a given location.
// @Resource Units
// @Produce json
// @Param lat query number true "Latitude"
// @Param lon query number true "Longitude"
// @Param unit_types query string false "Comma-separated unit type codes"
// @Success 200 {array} UnitResponse
// @Failure 400 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/units/nearby [get]
func (s *Server) handleListUnitsNearby(w http.ResponseWriter, r *http.Request) {
	latStr := r.URL.Query().Get("lat")
	lonStr := r.URL.Query().Get("lon")

	if latStr == "" || lonStr == "" {
		s.writeError(w, http.StatusBadRequest, "latitude and longitude are required", nil)
		return
	}

	lat, err := strconv.ParseFloat(latStr, 64)
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid latitude", err.Error())
		return
	}

	lon, err := strconv.ParseFloat(lonStr, 64)
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid longitude", err.Error())
		return
	}

	var unitTypes []string
	if ut := r.URL.Query().Get("unit_types"); ut != "" {
		unitTypes = strings.Split(ut, ",")
	}

	params := db.ListAvailableUnitsNearbyParams{
		Latitude:  lat,
		Longitude: lon,
		UnitTypes: unitTypes,
	}

	rows, err := s.queries.ListAvailableUnitsNearby(r.Context(), params)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list nearby units", err.Error())
		return
	}

	resp := make([]UnitResponse, 0, len(rows))
	for _, row := range rows {
		dist := row.Distance
		resp = append(resp, mapUnitRow(unitRowData{
			ID:             row.ID,
			CallSign:       row.CallSign,
			UnitTypeCode:   row.UnitTypeCode,
			HomeBase:       row.HomeBase,
			LocationID:     row.LocationID,
			Status:         row.Status,
			MicrobitID:     row.MicrobitID,
			Longitude:      row.Longitude,
			Latitude:       row.Latitude,
			DistanceMeters: &dist,
			LastContact:    row.LastContactAt,
			CreatedAt:      row.CreatedAt,
			UpdatedAt:      row.UpdatedAt,
		}))
	}

	s.writeJSON(w, http.StatusOK, resp)
}
