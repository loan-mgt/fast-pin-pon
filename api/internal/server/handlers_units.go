package server

import (
	"net/http"
	"time"

	db "fast/pin/internal/db/sqlc"

	"github.com/jackc/pgx/v5/pgtype"
)

type UpdateUnitStatusRequest struct {
	Status string `json:"status" validate:"required,oneof=available en_route on_site maintenance offline"`
}

type UpdateUnitLocationRequest struct {
	Latitude   float64    `json:"latitude" validate:"required,latitude"`
	Longitude  float64    `json:"longitude" validate:"required,longitude"`
	RecordedAt *time.Time `json:"recorded_at"`
}

type UnitTelemetryRequest struct {
	Latitude  float64  `json:"latitude" validate:"required,latitude"`
	Longitude float64  `json:"longitude" validate:"required,longitude"`
	Heading   *int32   `json:"heading"`
	SpeedKMH  *float64 `json:"speed_kmh" validate:"omitempty,gte=0"`
	Status    RawJSON  `json:"status_snapshot"`
}

// handleListUnits godoc
// @Summary List units
// @Description Returns all operational units with their current status and location.
// @Tags Units
// @Produce json
// @Success 200 {array} UnitResponse
// @Failure 500 {object} APIError
// @Router /v1/units [get]
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
			Status:       row.Status,
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
// @Summary Create unit
// @Description 
// @Tags Units
// @Produce json
// @Success 200 {object} UnitResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Router /v1/units [post]
func (s *Server) handleCreateUnit(w http.ResponseWriter, r *http.Request) {
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
			Status:       row.Status,
			Longitude:    row.Longitude,
			Latitude:     row.Latitude,
			LastContact:  row.LastContactAt,
			CreatedAt:    row.CreatedAt,
			UpdatedAt:    row.UpdatedAt,
		}))
	}

	s.writeJSON(w, http.StatusOK, resp)
}

// handleUpdateUnitStatus godoc
// @Summary Update unit status
// @Description Updates the dispatch readiness of a unit.
// @Tags Units
// @Accept json
// @Produce json
// @Param unitID path string true "Unit ID"
// @Param request body UpdateUnitStatusRequest true "Status payload"
// @Success 200 {object} UnitResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Router /v1/units/{unitID}/status [patch]
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
			s.writeError(w, http.StatusNotFound, "unit not found", nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to update unit", err.Error())
		return
	}

	s.writeJSON(w, http.StatusOK, mapUnitRow(unitRowData{
		ID:           row.ID,
		CallSign:     row.CallSign,
		UnitTypeCode: row.UnitTypeCode,
		HomeBase:     row.HomeBase,
		Status:       row.Status,
		Longitude:    row.Longitude,
		Latitude:     row.Latitude,
		LastContact:  row.LastContactAt,
		CreatedAt:    row.CreatedAt,
		UpdatedAt:    row.UpdatedAt,
	}))
}

// handleUpdateUnitLocation godoc
// @Summary Update unit location
// @Description Updates the last known location for a unit.
// @Tags Units
// @Accept json
// @Produce json
// @Param unitID path string true "Unit ID"
// @Param request body UpdateUnitLocationRequest true "Location payload"
// @Success 200 {object} UnitResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Router /v1/units/{unitID}/location [patch]
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
			s.writeError(w, http.StatusNotFound, "unit not found", nil)
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
		Status:       row.Status,
		Longitude:    row.Longitude,
		Latitude:     row.Latitude,
		LastContact:  row.LastContactAt,
		CreatedAt:    row.CreatedAt,
		UpdatedAt:    row.UpdatedAt,
	}))
}

// handleInsertTelemetry godoc
// @Summary Submit telemetry
// @Description Stores a telemetry snapshot for a unit.
// @Tags Units
// @Accept json
// @Produce json
// @Param unitID path string true "Unit ID"
// @Param request body UnitTelemetryRequest true "Telemetry payload"
// @Success 201 {object} TelemetryResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Router /v1/units/{unitID}/telemetry [post]
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
	ID           pgtype.UUID
	CallSign     string
	UnitTypeCode string
	HomeBase     *string
	Status       db.UnitStatus
	Longitude    float64
	Latitude     float64
	LastContact  pgtype.Timestamptz
	CreatedAt    pgtype.Timestamptz
	UpdatedAt    pgtype.Timestamptz
}

func mapUnitRow(data unitRowData) UnitResponse {
	return UnitResponse{
		ID:           uuidString(data.ID),
		CallSign:     data.CallSign,
		UnitTypeCode: data.UnitTypeCode,
		HomeBase:     optionalString(data.HomeBase),
		Status:       string(data.Status),
		Location:     GeoPoint{Latitude: data.Latitude, Longitude: data.Longitude},
		LastContact:  timestamptzPtr(data.LastContact),
		CreatedAt:    data.CreatedAt.Time,
		UpdatedAt:    data.UpdatedAt.Time,
	}
}
