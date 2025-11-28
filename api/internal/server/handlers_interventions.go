package server

import (
	"net/http"

	db "fast/pin/internal/db/sqlc"
)

type createInterventionRequest struct {
	EventID      string  `json:"event_id" validate:"required,uuid4"`
	Status       string  `json:"status" validate:"omitempty,oneof=planned en_route on_site completed cancelled"`
	Priority     int32   `json:"priority" validate:"omitempty,min=1,max=5"`
	DecisionMode string  `json:"decision_mode" validate:"omitempty,oneof=auto_suggested manual"`
	CreatedBy    *string `json:"created_by"`
	Notes        *string `json:"notes"`
}

type updateInterventionStatusRequest struct {
	Status string `json:"status" validate:"required,oneof=planned en_route on_site completed cancelled"`
}

type createAssignmentRequest struct {
	UnitID string  `json:"unit_id" validate:"required,uuid4"`
	Role   *string `json:"role"`
	Status string  `json:"status" validate:"omitempty,oneof=dispatched arrived released cancelled"`
}

type updateAssignmentStatusRequest struct {
	Status string `json:"status" validate:"required,oneof=dispatched arrived released cancelled"`
}

func (s *Server) handleCreateIntervention(w http.ResponseWriter, r *http.Request) {
	var req createInterventionRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid payload", err.Error())
		return
	}

	eventID, err := pgUUIDFromString(req.EventID)
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid event id", err.Error())
		return
	}

	priority := req.Priority
	if priority == 0 {
		priority = 3
	}

	params := db.CreateInterventionParams{
		EventID:      eventID,
		Status:       db.InterventionStatus(req.Status),
		Priority:     priority,
		DecisionMode: db.DecisionMode(req.DecisionMode),
		CreatedBy:    req.CreatedBy,
		Notes:        req.Notes,
	}
	if params.Status == "" {
		params.Status = db.InterventionStatusPlanned
	}
	if params.DecisionMode == "" {
		params.DecisionMode = db.DecisionModeManual
	}

	row, err := s.queries.CreateIntervention(r.Context(), params)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to create intervention", err.Error())
		return
	}

	s.writeJSON(w, http.StatusCreated, mapIntervention(row))
}

func (s *Server) handleGetIntervention(w http.ResponseWriter, r *http.Request) {
	interventionID, err := s.parseUUIDParam(r, "interventionID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid intervention id", err.Error())
		return
	}

	row, err := s.queries.GetIntervention(r.Context(), interventionID)
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, "intervention not found", nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to fetch intervention", err.Error())
		return
	}

	assignments, err := s.queries.ListAssignmentsByIntervention(r.Context(), interventionID)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to fetch assignments", err.Error())
		return
	}

	resp := mapIntervention(row)
	for _, a := range assignments {
		resp.Assignments = append(resp.Assignments, mapAssignmentRow(a))
	}

	s.writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleUpdateInterventionStatus(w http.ResponseWriter, r *http.Request) {
	interventionID, err := s.parseUUIDParam(r, "interventionID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid intervention id", err.Error())
		return
	}

	var req updateInterventionStatusRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid payload", err.Error())
		return
	}

	row, err := s.queries.UpdateInterventionStatus(r.Context(), db.UpdateInterventionStatusParams{
		ID:     interventionID,
		Status: db.InterventionStatus(req.Status),
	})
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, "intervention not found", nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to update intervention", err.Error())
		return
	}

	s.writeJSON(w, http.StatusOK, mapIntervention(row))
}

func (s *Server) handleListInterventionsForEvent(w http.ResponseWriter, r *http.Request) {
	eventID, err := s.parseUUIDParam(r, "eventID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid event id", err.Error())
		return
	}

	rows, err := s.queries.ListInterventionsByEvent(r.Context(), eventID)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list interventions", err.Error())
		return
	}

	resp := make([]InterventionResponse, 0, len(rows))
	for _, row := range rows {
		resp = append(resp, mapIntervention(row))
	}

	s.writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleCreateAssignment(w http.ResponseWriter, r *http.Request) {
	interventionID, err := s.parseUUIDParam(r, "interventionID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid intervention id", err.Error())
		return
	}

	var req createAssignmentRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid payload", err.Error())
		return
	}

	unitID, err := pgUUIDFromString(req.UnitID)
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid unit id", err.Error())
		return
	}

	status := db.AssignmentStatus(req.Status)
	if status == "" {
		status = db.AssignmentStatusDispatched
	}

	params := db.CreateAssignmentParams{
		InterventionID: interventionID,
		UnitID:         unitID,
		Role:           req.Role,
		Status:         status,
	}

	row, err := s.queries.CreateAssignment(r.Context(), params)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to create assignment", err.Error())
		return
	}

	s.writeJSON(w, http.StatusCreated, mapAssignment(row))
}

func (s *Server) handleListAssignmentsForIntervention(w http.ResponseWriter, r *http.Request) {
	interventionID, err := s.parseUUIDParam(r, "interventionID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid intervention id", err.Error())
		return
	}

	rows, err := s.queries.ListAssignmentsByIntervention(r.Context(), interventionID)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list assignments", err.Error())
		return
	}

	resp := make([]AssignmentResponse, 0, len(rows))
	for _, row := range rows {
		resp = append(resp, mapAssignmentRow(row))
	}

	s.writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleUpdateAssignmentStatus(w http.ResponseWriter, r *http.Request) {
	assignmentID, err := s.parseUUIDParam(r, "assignmentID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid assignment id", err.Error())
		return
	}

	var req updateAssignmentStatusRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid payload", err.Error())
		return
	}

	row, err := s.queries.UpdateAssignmentStatus(r.Context(), db.UpdateAssignmentStatusParams{
		ID:     assignmentID,
		Status: db.AssignmentStatus(req.Status),
	})
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, "assignment not found", nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to update assignment", err.Error())
		return
	}

	s.writeJSON(w, http.StatusOK, mapAssignment(row))
}

func mapIntervention(row db.Intervention) InterventionResponse {
	return InterventionResponse{
		ID:           uuidString(row.ID),
		EventID:      uuidString(row.EventID),
		Status:       string(row.Status),
		Priority:     row.Priority,
		DecisionMode: string(row.DecisionMode),
		CreatedBy:    optionalString(row.CreatedBy),
		Notes:        optionalString(row.Notes),
		CreatedAt:    row.CreatedAt.Time,
		StartedAt:    timestamptzPtr(row.StartedAt),
		CompletedAt:  timestamptzPtr(row.CompletedAt),
	}
}

func mapAssignment(row db.InterventionAssignment) AssignmentResponse {
	return AssignmentResponse{
		ID:             uuidString(row.ID),
		InterventionID: uuidString(row.InterventionID),
		UnitID:         uuidString(row.UnitID),
		Role:           optionalString(row.Role),
		Status:         string(row.Status),
		DispatchedAt:   row.DispatchedAt.Time,
		ArrivedAt:      timestamptzPtr(row.ArrivedAt),
		ReleasedAt:     timestamptzPtr(row.ReleasedAt),
	}
}

func mapAssignmentRow(row db.ListAssignmentsByInterventionRow) AssignmentResponse {
	return AssignmentResponse{
		ID:             uuidString(row.ID),
		InterventionID: uuidString(row.InterventionID),
		UnitID:         uuidString(row.UnitID),
		UnitCallSign:   row.CallSign,
		UnitTypeCode:   row.UnitTypeCode,
		Role:           optionalString(row.Role),
		Status:         string(row.Status),
		DispatchedAt:   row.DispatchedAt.Time,
		ArrivedAt:      timestamptzPtr(row.ArrivedAt),
		ReleasedAt:     timestamptzPtr(row.ReleasedAt),
	}
}
