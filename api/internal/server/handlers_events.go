package server

import (
	"net/http"
	"strconv"
	"strings"

	db "fast/pin/internal/db/sqlc"
)

type CreateEventRequest struct {
	Title         string  `json:"title" validate:"required,min=3,max=140"`
	Description   *string `json:"description"`
	ReportSource  *string `json:"report_source"`
	Address       *string `json:"address"`
	Latitude      float64 `json:"latitude" validate:"required,latitude"`
	Longitude     float64 `json:"longitude" validate:"required,longitude"`
	Severity      int32   `json:"severity" validate:"required,min=1,max=5"`
	EventTypeCode string  `json:"event_type_code" validate:"required"`
}

type CreateEventLogRequest struct {
	Code    string  `json:"code" validate:"required"`
	Actor   *string `json:"actor"`
	Payload RawJSON `json:"payload"`
}

// handleListRecentEventLogs godoc
// @Title List recent activity logs
// @Description Returns the most recent activity logs.
// @Resource Events
// @Produce json
// @Param limit query int false "Maximum results" default(10)
// @Success 200 {array} EventLogWithEventResponse
// @Failure 500 {object} APIError
// @Route /v1/event-logs/recent [get]
func (s *Server) handleListRecentEventLogs(w http.ResponseWriter, r *http.Request) {
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	if limit <= 0 {
		limit = 10
	}
	if limit > 100 {
		limit = 100
	}

	// Fetch all recent activity logs (ActivityType nil = all)
	rows, err := s.queries.ListRecentActivityLogs(r.Context(), db.ListRecentActivityLogsParams{
		ActivityType: nil,
		Limit:        int32(limit),
	})
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list recent activity logs", err.Error())
		return
	}

	resp := make([]EventLogWithEventResponse, 0, len(rows))
	for _, row := range rows {
		resp = append(resp, mapActivityLogToEventLogResponse(row))
	}

	s.writeJSON(w, http.StatusOK, resp)
}

// handleListEventTypes godoc
// @Title List event types
// @Description Returns catalog of supported incident types.
// @Resource Metadata
// @Produce json
// @Success 200 {array} EventTypeResponse
// @Failure 500 {object} APIError
// @Route /v1/event-types [get]
func (s *Server) handleListEventTypes(w http.ResponseWriter, r *http.Request) {
	types, err := s.queries.ListEventTypes(r.Context())
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list event types", err.Error())
		return
	}

	resp := make([]EventTypeResponse, 0, len(types))
	for _, t := range types {
		resp = append(resp, EventTypeResponse{
			Code:                 t.Code,
			Name:                 t.Name,
			Description:          t.Description,
			DefaultSeverity:      t.DefaultSeverity,
			RecommendedUnitTypes: t.RecommendedUnitTypes,
		})
	}

	s.writeJSON(w, http.StatusOK, resp)
}

// handleListUnitTypes godoc
// @Title List unit types
// @Description Returns available responder unit categories.
// @Resource Metadata
// @Produce json
// @Success 200 {array} UnitTypeResponse
// @Failure 500 {object} APIError
// @Route /v1/unit-types [get]
func (s *Server) handleListUnitTypes(w http.ResponseWriter, r *http.Request) {
	types, err := s.queries.ListUnitTypes(r.Context())
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list unit types", err.Error())
		return
	}

	resp := make([]UnitTypeResponse, 0, len(types))
	for _, t := range types {
		resp = append(resp, UnitTypeResponse{
			Code:         t.Code,
			Name:         t.Name,
			Capabilities: t.Capabilities,
			SpeedKMH:     t.SpeedKmh,
			MaxCrew:      t.MaxCrew,
			Illustration: optionalString(t.Illustration),
		})
	}

	s.writeJSON(w, http.StatusOK, resp)
}

// handleListEvents godoc
// @Title List events
// @Description Retrieves paginated incident list ordered by creation date.
// @Resource Events
// @Produce json
// @Param limit query int false "Maximum results" default(25)
// @Param offset query int false "Results offset" default(0)
// @Param deny_status query string false "Comma-separated intervention statuses to exclude (e.g., completed,cancelled)"
// @Success 200 {array} EventSummaryResponse
// @Failure 500 {object} APIError
// @Route /v1/events [get]
func (s *Server) handleListEvents(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	limit, offset := s.paginate(r, 25)
	rows, err := s.queries.ListEvents(ctx, db.ListEventsParams{Limit: limit, Offset: offset})
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list events", err.Error())
		return
	}

	denySet := s.parseDenySet(r.URL.Query().Get("deny_status"))

	resp := make([]EventSummaryResponse, 0, len(rows))
	for _, row := range rows {
		if denySet != nil && row.InterventionStatus.Valid {
			if _, denied := denySet[row.InterventionStatus.InterventionStatus]; denied {
				continue
			}
		}
		assigned, assignErr := s.queries.ListUnitsAssignedToEvent(ctx, row.ID)
		if assignErr != nil {
			s.writeError(w, http.StatusInternalServerError, "failed to list assigned units", assignErr.Error())
			return
		}

		assignedUnits := make([]UnitResponse, 0, len(assigned))
		for _, u := range assigned {
			assignedUnits = append(assignedUnits, mapUnitRow(unitRowData{
				ID:           u.ID,
				CallSign:     u.CallSign,
				UnitTypeCode: u.UnitTypeCode,
				HomeBaseName: u.HomeBaseName,
				LocationID:   u.LocationID,
				Status:       u.Status,
				MicrobitID:   u.MicrobitID,
				Longitude:    u.Longitude,
				Latitude:     u.Latitude,
				LastContact:  u.LastContactAt,
				CreatedAt:    u.CreatedAt,
				UpdatedAt:    u.UpdatedAt,
			}))
		}

		resp = append(resp, mapEventSummary(row, assignedUnits))
	}
	s.writeJSON(w, http.StatusOK, resp)
}

func (s *Server) parseDenySet(denyParam string) map[db.InterventionStatus]struct{} {
	if denyParam == "" {
		return nil
	}
	parts := splitCSV(denyParam)
	denySet := make(map[db.InterventionStatus]struct{}, len(parts))
	for _, p := range parts {
		st := db.InterventionStatus(p)
		switch st {
		case db.InterventionStatusCreated, db.InterventionStatusOnSite, db.InterventionStatusCompleted, db.InterventionStatusCancelled:
			denySet[st] = struct{}{}
		}
	}
	return denySet
}

// splitCSV trims and splits a comma-separated list.
func splitCSV(s string) []string {
	parts := strings.Split(s, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		trimmed := strings.TrimSpace(p)
		if trimmed != "" {
			out = append(out, trimmed)
		}
	}
	return out
}

// handleCreateEvent godoc
// @Title Create event
// @Description Registers a new incident in the system.
// @Resource Events
// @Accept json
// @Produce json
// @Param auto_intervention query boolean false "Automatically create an intervention for this event"
// @Param decision_mode query string false "Decision mode for the auto-created intervention" Enums(auto_suggested, manual) default(auto_suggested)
// @Param request body CreateEventRequest true "Event payload"
// @Success 201 {object} EventSummaryResponse
// @Failure 400 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/events [post]
func (s *Server) handleCreateEvent(w http.ResponseWriter, r *http.Request) {
	var req CreateEventRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	params := db.CreateEventParams{
		Title:         req.Title,
		Description:   req.Description,
		ReportSource:  req.ReportSource,
		Address:       req.Address,
		Longitude:     req.Longitude,
		Latitude:      req.Latitude,
		Severity:      req.Severity,
		EventTypeCode: req.EventTypeCode,
	}

	row, err := s.queries.CreateEvent(r.Context(), params)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to create event", err.Error())
		return
	}

	// Auto-create intervention if requested via query param
	if r.URL.Query().Get("auto_intervention") == "true" {
		decisionMode := db.DecisionModeAutoSuggested
		if dm := r.URL.Query().Get("decision_mode"); dm != "" {
			decisionMode = db.DecisionMode(dm)
		}

		intervention, err := s.queries.CreateIntervention(r.Context(), db.CreateInterventionParams{
			EventID:      row.ID,
			Status:       db.InterventionStatusCreated,
			Priority:     row.Severity,
			DecisionMode: decisionMode,
		})
		if err != nil {
			s.log.Error().Err(err).Msg("failed to auto-create intervention")
			// We don't fail the event creation, but maybe we should return a different status
			// or include it in the response. For now, just log the error.
		} else {
			// Log the creation
			s.logInterventionStatusChange(r.Context(), intervention.ID, row.ID, "", string(db.InterventionStatusCreated), nil)
			// Trigger engine dispatch
			s.notifyEngineDispatch(r.Context(), uuidString(intervention.ID))
		}
	}

	summary := mapCreateEventRow(row)
	s.writeJSON(w, http.StatusCreated, summary)
}

// handleGetEvent godoc
// @Title Get event
// @Description Returns a detailed view of a specific incident.
// @Resource Events
// @Produce json
// @Param eventID path string true "Event ID"
// @Success 200 {object} EventDetailResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/events/{eventID} [get]
func (s *Server) handleGetEvent(w http.ResponseWriter, r *http.Request) {
	eventID, err := s.parseUUIDParam(r, "eventID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidEventID, err.Error())
		return
	}

	eventRow, err := s.queries.GetEvent(r.Context(), eventID)
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, "event not found", nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to fetch event", err.Error())
		return
	}

	interventions, err := s.queries.ListInterventionsByEvent(r.Context(), eventID)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to fetch interventions", err.Error())
		return
	}

	assigned, err := s.queries.ListUnitsAssignedToEvent(r.Context(), eventID)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list assigned units", err.Error())
		return
	}

	assignedUnits := make([]UnitResponse, 0, len(assigned))
	for _, u := range assigned {
		assignedUnits = append(assignedUnits, mapUnitRow(unitRowData{
			ID:           u.ID,
			CallSign:     u.CallSign,
			UnitTypeCode: u.UnitTypeCode,
			HomeBaseName: u.HomeBaseName,
			LocationID:   u.LocationID,
			Status:       u.Status,
			MicrobitID:   u.MicrobitID,
			Longitude:    u.Longitude,
			Latitude:     u.Latitude,
			LastContact:  u.LastContactAt,
			CreatedAt:    u.CreatedAt,
			UpdatedAt:    u.UpdatedAt,
		}))
	}

	logs, err := s.queries.ListActivityLogsForEvent(r.Context(), db.ListActivityLogsForEventParams{
		EventID: eventID,
		Limit:   50,
		Offset:  0,
	})
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to fetch event logs", err.Error())
		return
	}

	resp := mapEventDetail(eventRow, interventions, logs)
	resp.AssignedUnits = assignedUnits
	s.writeJSON(w, http.StatusOK, resp)
}

// handleCreateEventLog godoc
// @Title Append event log entry
// @Description Adds an entry to an incident timeline (as an activity log).
// @Resource Events
// @Accept json
// @Produce json
// @Param eventID path string true "Event ID"
// @Param request body CreateEventLogRequest true "Log payload"
// @Success 201 {object} EventLogResponse
// @Failure 400 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/events/{eventID}/logs [post]
func (s *Server) handleCreateEventLog(w http.ResponseWriter, r *http.Request) {
	eventID, err := s.parseUUIDParam(r, "eventID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidEventID, err.Error())
		return
	}

	var req CreateEventLogRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	entityType := "event"
	params := db.CreateActivityLogParams{
		ActivityType: req.Code,
		EntityType:   &entityType,
		EntityID:     eventID,
		Actor:        req.Actor,
		Metadata:     rawJSONOrEmpty(req.Payload),
	}

	logRow, err := s.queries.CreateActivityLog(r.Context(), params)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to create log", err.Error())
		return
	}

	s.writeJSON(w, http.StatusCreated, EventLogResponse{
		ID:        logRow.ID,
		EventID:   uuidStringOptional(logRow.EntityID),
		CreatedAt: logRow.CreatedAt.Time,
		Code:      logRow.ActivityType,
		Actor:     optionalString(logRow.Actor),
		Payload:   RawJSON(logRow.Metadata),
	})
}

// handleListEventLogs godoc
// @Title List event logs
// @Description Retrieves paginated timeline entries for an incident.
// @Resource Events
// @Produce json
// @Param eventID path string true "Event ID"
// @Param limit query int false "Maximum results" default(50)
// @Param offset query int false "Results offset" default(0)
// @Success 200 {array} EventLogResponse
// @Failure 400 {object} APIError
// @Failure 500 {object} APIError
// @Route /v1/events/{eventID}/logs [get]
func (s *Server) handleListEventLogs(w http.ResponseWriter, r *http.Request) {
	eventID, err := s.parseUUIDParam(r, "eventID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidEventID, err.Error())
		return
	}
	limit, offset := s.paginate(r, 50)

	rows, err := s.queries.ListActivityLogsForEvent(r.Context(), db.ListActivityLogsForEventParams{
		EventID: eventID,
		Limit:   int32(limit),
		Offset:  int32(offset),
	})
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list logs", err.Error())
		return
	}

	resp := make([]EventLogResponse, 0, len(rows))
	for _, row := range rows {
		resp = append(resp, EventLogResponse{
			ID:        row.ID,
			EventID:   uuidString(eventID),
			CreatedAt: row.CreatedAt.Time,
			Code:      row.ActivityType,
			Actor:     optionalString(row.Actor),
			Payload:   RawJSON(row.Metadata),
		})
	}

	s.writeJSON(w, http.StatusOK, resp)
}

func mapEventSummary(row db.ListEventsRow, assignedUnits []UnitResponse) EventSummaryResponse {
	var intID *string
	if row.InterventionID.Valid {
		s := uuidString(row.InterventionID)
		intID = &s
	}
	var intStatus *string
	if row.InterventionStatus.Valid {
		s := string(row.InterventionStatus.InterventionStatus)
		intStatus = &s
	}

	return EventSummaryResponse{
		ID:                 uuidString(row.ID),
		Title:              row.Title,
		Description:        optionalString(row.Description),
		ReportSource:       optionalString(row.ReportSource),
		Address:            optionalString(row.Address),
		Location:           GeoPoint{Latitude: row.Latitude, Longitude: row.Longitude},
		Severity:           row.Severity,
		InterventionID:     intID,
		InterventionStatus: intStatus,
		EventTypeCode:      row.EventTypeCode,
		EventTypeName:      row.EventTypeName,
		ReportedAt:         row.ReportedAt.Time,
		UpdatedAt:          row.UpdatedAt.Time,
		ClosedAt:           timestamptzPtr(row.ClosedAt),
		StartedAt:          timestamptzPtr(row.InterventionStartedAt),
		CompletedAt:        timestamptzPtr(row.InterventionCompletedAt),
		AssignedUnits:      assignedUnits,
	}
}

func mapCreateEventRow(row db.CreateEventRow) EventSummaryResponse {
	return EventSummaryResponse{
		ID:           uuidString(row.ID),
		Title:        row.Title,
		Description:  optionalString(row.Description),
		ReportSource: optionalString(row.ReportSource),
		Address:      optionalString(row.Address),
		Location: GeoPoint{
			Latitude:  row.Latitude,
			Longitude: row.Longitude,
		},
		Severity:      row.Severity,
		EventTypeCode: row.EventTypeCode,
		ReportedAt:    row.ReportedAt.Time,
		UpdatedAt:     row.UpdatedAt.Time,
		ClosedAt:      timestamptzPtr(row.ClosedAt),
	}
}

func mapEventDetail(event db.GetEventRow, interventions []db.Intervention, logs []db.ActivityLog) EventDetailResponse {
	var intID *string
	if event.InterventionID.Valid {
		s := uuidString(event.InterventionID)
		intID = &s
	}
	var intStatus *string
	if event.InterventionStatus.Valid {
		s := string(event.InterventionStatus.InterventionStatus)
		intStatus = &s
	}

	summary := EventSummaryResponse{
		ID:                 uuidString(event.ID),
		Title:              event.Title,
		Description:        optionalString(event.Description),
		ReportSource:       optionalString(event.ReportSource),
		Address:            optionalString(event.Address),
		Location:           GeoPoint{Latitude: event.Latitude, Longitude: event.Longitude},
		Severity:           event.Severity,
		InterventionID:     intID,
		InterventionStatus: intStatus,
		EventTypeCode:      event.EventTypeCode,
		EventTypeName:      event.EventTypeName,
		ReportedAt:         event.ReportedAt.Time,
		UpdatedAt:          event.UpdatedAt.Time,
		ClosedAt:           timestamptzPtr(event.ClosedAt),
	}

	var associatedIntervention *InterventionResponse
	if len(interventions) > 0 {
		i := mapIntervention(interventions[0])
		associatedIntervention = &i
	}

	resp := EventDetailResponse{
		EventSummaryResponse: summary,
		RecommendedUnitTypes: event.RecommendedUnitTypes,
		Intervention:         associatedIntervention,
	}

	for _, logRow := range logs {
		resp.Logs = append(resp.Logs, EventLogResponse{
			ID:        logRow.ID,
			EventID:   uuidString(event.ID),
			CreatedAt: logRow.CreatedAt.Time,
			Code:      logRow.ActivityType,
			Actor:     optionalString(logRow.Actor),
			Payload:   RawJSON(logRow.Metadata),
		})
	}

	return resp
}

func mapActivityLogToEventLogResponse(row db.ListRecentActivityLogsRow) EventLogWithEventResponse {
	return EventLogWithEventResponse{
		ID:            row.ID,
		EventID:       uuidStringOptional(row.EventID),
		EventTitle:    optionalString(row.EventTitle),
		EventTypeCode: "", // Not returned by ListRecentActivityLogsRow currently, can be added later if needed
		CreatedAt:     row.CreatedAt.Time,
		Code:          row.ActivityType,
		Actor:         optionalString(row.Actor),
		Payload:       RawJSON(row.Metadata),
		// Add new fields if front expects them for unit logs
		EntityType: optionalString(row.EntityType),
		EntityID:   uuidStringOptional(row.EntityID),
		OldValue:   optionalString(row.OldValue),
		NewValue:   optionalString(row.NewValue),
	}
}

func stringPtr(s string) *string {
	return &s
}
