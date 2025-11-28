package server

import (
	"encoding/json"
	"net/http"
	"time"

	db "fast/pin/internal/db/sqlc"

	"github.com/jackc/pgx/v5/pgtype"
)

type updateUnitStatusRequest struct {
	Status string `json:"status" validate:"required,oneof=available en_route on_site maintenance offline"`
}

type updateUnitLocationRequest struct {
	Latitude   float64    `json:"latitude" validate:"required,latitude"`
	Longitude  float64    `json:"longitude" validate:"required,longitude"`
	RecordedAt *time.Time `json:"recorded_at"`
}

type unitTelemetryRequest struct {
	Latitude  float64         `json:"latitude" validate:"required,latitude"`
	Longitude float64         `json:"longitude" validate:"required,longitude"`
	Heading   *int32          `json:"heading"`
	SpeedKMH  *float64        `json:"speed_kmh" validate:"omitempty,gte=0"`
	Status    json.RawMessage `json:"status_snapshot"`
}

func (s *Server) handleListUnits(w http.ResponseWriter, r *http.Request) {
	rows, err := s.queries.ListUnits(r.Context())
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list units", err.Error())
		return
	}

	resp := make([]UnitResponse, 0, len(rows))
	for _, row := range rows {
		resp = append(resp, mapUnitRow(row.ID, row.CallSign, row.UnitTypeCode, row.HomeBase, row.Status, row.Longitude, row.Latitude, row.LastContactAt, row.CreatedAt, row.UpdatedAt))
	}

	s.writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleUpdateUnitStatus(w http.ResponseWriter, r *http.Request) {
	unitID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid unit id", err.Error())
		return
	}

	var req updateUnitStatusRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid payload", err.Error())
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

	s.writeJSON(w, http.StatusOK, mapUnitRow(row.ID, row.CallSign, row.UnitTypeCode, row.HomeBase, row.Status, row.Longitude, row.Latitude, row.LastContactAt, row.CreatedAt, row.UpdatedAt))
}

func (s *Server) handleUpdateUnitLocation(w http.ResponseWriter, r *http.Request) {
	unitID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid unit id", err.Error())
		return
	}

	var req updateUnitLocationRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid payload", err.Error())
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

	s.writeJSON(w, http.StatusOK, mapUnitRow(row.ID, row.CallSign, row.UnitTypeCode, row.HomeBase, row.Status, row.Longitude, row.Latitude, row.LastContactAt, row.CreatedAt, row.UpdatedAt))
}

func (s *Server) handleInsertTelemetry(w http.ResponseWriter, r *http.Request) {
	unitID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid unit id", err.Error())
		return
	}

	var req unitTelemetryRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid payload", err.Error())
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
		Status:     json.RawMessage(row.StatusSnapshot),
	}

	s.writeJSON(w, http.StatusCreated, resp)
}

func mapUnitRow(id pgtype.UUID, callSign string, unitTypeCode string, homeBase *string, status db.UnitStatus, longitude, latitude float64, lastContact, createdAt, updatedAt pgtype.Timestamptz) UnitResponse {
	return UnitResponse{
		ID:           uuidString(id),
		CallSign:     callSign,
		UnitTypeCode: unitTypeCode,
		HomeBase:     optionalString(homeBase),
		Status:       string(status),
		Location:     GeoPoint{Latitude: latitude, Longitude: longitude},
		LastContact:  timestamptzPtr(lastContact),
		CreatedAt:    createdAt.Time,
		UpdatedAt:    updatedAt.Time,
	}
}
