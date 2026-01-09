package server

import (
	"context"
	"net/http"

	"github.com/jackc/pgx/v5/pgtype"

	db "fast/pin/internal/db/sqlc"
)

type CreateInterventionRequest struct {
	EventID      string  `json:"event_id" validate:"required,uuid4"`
	Status       string  `json:"status" validate:"omitempty,oneof=created on_site completed cancelled"`
	Priority     int32   `json:"priority" validate:"omitempty,min=1,max=5"`
	DecisionMode string  `json:"decision_mode" validate:"omitempty,oneof=auto_suggested manual"`
	CreatedBy    *string `json:"created_by"`
	Notes        *string `json:"notes"`
}

type UpdateInterventionStatusRequest struct {
	Status string `json:"status" validate:"required,oneof=created on_site completed cancelled"`
}

type CreateAssignmentRequest struct {
	UnitID string  `json:"unit_id" validate:"required,uuid4"`
	Role   *string `json:"role"`
	Status string  `json:"status" validate:"omitempty,oneof=dispatched arrived released cancelled"`
}

type UpdateAssignmentStatusRequest struct {
	Status string `json:"status" validate:"required,oneof=dispatched arrived released cancelled"`
}

// handleCreateIntervention godoc
// @Title Create intervention
// @Description Starts a new intervention linked to an event.
// @Resource Interventions
// @Accept json
// @Produce json
// @Param request body CreateInterventionRequest true "Intervention payload"
// @Success 201 {object} InterventionResponse
// @Failure 400 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/interventions [post]
func (s *Server) handleCreateIntervention(w http.ResponseWriter, r *http.Request) {
	var req CreateInterventionRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
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
		params.Status = db.InterventionStatusCreated
	}
	if params.DecisionMode == "" {
		params.DecisionMode = db.DecisionModeManual
	}

	row, err := s.queries.CreateIntervention(r.Context(), params)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to create intervention", err.Error())
		return
	}

	// Trigger engine if in auto_suggested mode
	if params.DecisionMode == db.DecisionModeAutoSuggested {
		go s.notifyEngineDispatch(context.Background(), uuidString(row.ID))
	}

	s.writeJSON(w, http.StatusCreated, mapIntervention(row))
}

// handleGetIntervention godoc
// @Title Get intervention
// @Description Returns detailed information about a specific intervention.
// @Resource Interventions
// @Produce json
// @Param interventionID path string true "Intervention ID"
// @Success 200 {object} InterventionResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/interventions/{interventionID} [get]
func (s *Server) handleGetIntervention(w http.ResponseWriter, r *http.Request) {
	interventionID, err := s.parseUUIDParam(r, "interventionID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidInterventionID, err.Error())
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

// handleUpdateInterventionStatus godoc
// @Title Update intervention status
// @Description Updates the operational status of an intervention.
// @Resource Interventions
// @Accept json
// @Produce json
// @Param interventionID path string true "Intervention ID"
// @Param request body UpdateInterventionStatusRequest true "Status payload"
// @Success 200 {object} InterventionResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/interventions/{interventionID}/status [patch]
func (s *Server) handleUpdateInterventionStatus(w http.ResponseWriter, r *http.Request) {
	interventionID, err := s.parseUUIDParam(r, "interventionID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidInterventionID, err.Error())
		return
	}

	var req UpdateInterventionStatusRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	row, err := s.queries.UpdateInterventionStatus(r.Context(), db.UpdateInterventionStatusParams{
		ID:      interventionID,
		Column2: db.InterventionStatus(req.Status),
	})
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, "intervention not found", nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to update intervention", err.Error())
		return
	}

	// When an intervention is completed, release all assignments and set units available.
	if req.Status == string(db.InterventionStatusCompleted) {
		if err := s.releaseInterventionUnits(r.Context(), interventionID); err != nil {
			s.writeError(w, http.StatusInternalServerError, "failed to release assignments", err.Error())
			return
		}

		s.observeEventResolution(r.Context(), interventionID, row.CompletedAt)
	}

	s.writeJSON(w, http.StatusOK, mapIntervention(row))
}

func (s *Server) releaseInterventionUnits(ctx context.Context, interventionID pgtype.UUID) error {
	assignments, err := s.queries.ListAssignmentsByIntervention(ctx, interventionID)
	if err != nil {
		return err
	}

	for _, a := range assignments {
		if a.Status != db.AssignmentStatusReleased {
			if _, err := s.queries.UpdateAssignmentStatus(ctx, db.UpdateAssignmentStatusParams{
				ID:      a.ID,
				Column2: db.AssignmentStatusReleased,
			}); err != nil {
				return err
			}
		}

		if _, err := s.queries.UpdateUnitStatus(ctx, db.UpdateUnitStatusParams{
			ID:     a.UnitID,
			Status: db.UnitStatusAvailable,
		}); err != nil {
			return err
		}

		s.observeAssignmentOnSite(ctx, a.ID)
	}
	return nil
}

// handleListInterventionsForEvent godoc
// @Title List event interventions
// @Description Lists interventions associated with an event.
// @Resource Interventions
// @Produce json
// @Param eventID path string true "Event ID"
// @Success 200 {array} InterventionResponse
// @Failure 400 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/events/{eventID}/interventions [get]
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

// handleCreateAssignment godoc
// @Title Create assignment
// @Description Assigns a unit to an intervention.
// @Resource Interventions
// @Accept json
// @Produce json
// @Param interventionID path string true "Intervention ID"
// @Param request body CreateAssignmentRequest true "Assignment payload"
// @Success 201 {object} AssignmentResponse
// @Failure 400 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/interventions/{interventionID}/assignments [post]
func (s *Server) handleCreateAssignment(w http.ResponseWriter, r *http.Request) {
	interventionID, err := s.parseUUIDParam(r, "interventionID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidInterventionID, err.Error())
		return
	}

	var req CreateAssignmentRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
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

	// Update unit status to 'under_way' when assigned
	if _, err := s.queries.UpdateUnitStatus(r.Context(), db.UpdateUnitStatusParams{
		ID:     unitID,
		Status: db.UnitStatusUnderWay,
	}); err != nil {
		s.log.Error().Err(err).Str("unit_id", req.UnitID).Msg("failed to update unit status after assignment")
		// We don't fail the whole request because the assignment was created
	}

	// Calculate and save route for the unit
	go s.calculateAndSaveRouteForAssignment(context.Background(), interventionID, unitID)

	s.writeJSON(w, http.StatusCreated, mapAssignment(row))
}

// handleReleaseAssignment godoc
// @Title Release assignment
// @Description Marks a unit as released from an intervention.
// @Resource Interventions
// @Param interventionID path string true "Intervention ID"
// @Param unitID path string true "Unit ID"
// @Success 204 {string} string "No Content"
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/interventions/{interventionID}/assignments/{unitID} [delete]
func (s *Server) handleReleaseAssignment(w http.ResponseWriter, r *http.Request) {
	interventionID, err := s.parseUUIDParam(r, "interventionID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidInterventionID, err.Error())
		return
	}

	unitID, err := s.parseUUIDParam(r, "unitID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidUnitID, err.Error())
		return
	}

	releasedID, err := s.queries.ReleaseUnitFromIntervention(r.Context(), db.ReleaseUnitFromInterventionParams{
		InterventionID: interventionID,
		UnitID:         unitID,
	})
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, "active assignment not found", nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to release unit", err.Error())
		return
	}

	// Set unit available again
	if _, err := s.queries.UpdateUnitStatus(r.Context(), db.UpdateUnitStatusParams{
		ID:     unitID,
		Status: db.UnitStatusAvailable,
	}); err != nil {
		s.log.Error().Err(err).Str("unit_id", uuidString(unitID)).Msg("failed to update unit status after release")
	}

	s.observeAssignmentOnSite(r.Context(), releasedID)

	w.WriteHeader(http.StatusNoContent)
}

// handleListAssignmentsForIntervention godoc
// @Title List assignments
// @Description Lists unit assignments for an intervention.
// @Resource Interventions
// @Produce json
// @Param interventionID path string true "Intervention ID"
// @Success 200 {array} AssignmentResponse
// @Failure 400 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/interventions/{interventionID}/assignments [get]
func (s *Server) handleListAssignmentsForIntervention(w http.ResponseWriter, r *http.Request) {
	interventionID, err := s.parseUUIDParam(r, "interventionID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidInterventionID, err.Error())
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

// handleUpdateAssignmentStatus godoc
// @Title Update assignment status
// @Description Updates the lifecycle state of a dispatched unit.
// @Resource Interventions
// @Accept json
// @Produce json
// @Param assignmentID path string true "Assignment ID"
// @Param request body UpdateAssignmentStatusRequest true "Status payload"
// @Success 200 {object} AssignmentResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/assignments/{assignmentID}/status [patch]
func (s *Server) handleUpdateAssignmentStatus(w http.ResponseWriter, r *http.Request) {
	assignmentID, err := s.parseUUIDParam(r, "assignmentID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid assignment id", err.Error())
		return
	}

	var req UpdateAssignmentStatusRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	row, err := s.queries.UpdateAssignmentStatus(r.Context(), db.UpdateAssignmentStatusParams{
		ID:      assignmentID,
		Column2: db.AssignmentStatus(req.Status),
	})
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, "assignment not found", nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to update assignment", err.Error())
		return
	}

	if row.Status == db.AssignmentStatusArrived {
		s.observeAssignmentTravel(r.Context(), assignmentID)
	}

	if row.Status == db.AssignmentStatusReleased {
		s.observeAssignmentOnSite(r.Context(), assignmentID)
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
