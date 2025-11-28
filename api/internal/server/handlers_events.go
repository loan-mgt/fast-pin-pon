package server

import (
	"net/http"

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

type UpdateEventStatusRequest struct {
	Status string `json:"status" validate:"required,oneof=open acknowledged contained closed"`
}

type CreateEventLogRequest struct {
	Code    string  `json:"code" validate:"required"`
	Actor   *string `json:"actor"`
	Payload RawJSON `json:"payload"`
}

// handleListEventTypes godoc
// @Summary List event types
// @Description Returns catalog of supported incident types.
// @Tags Metadata
// @Produce json
// @Success 200 {array} EventTypeResponse
// @Failure 500 {object} APIError
// @Router /v1/event-types [get]
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
// @Summary List unit types
// @Description Returns available responder unit categories.
// @Tags Metadata
// @Produce json
// @Success 200 {array} UnitTypeResponse
// @Failure 500 {object} APIError
// @Router /v1/unit-types [get]
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
// @Summary List events
// @Description Retrieves paginated incident list ordered by creation date.
// @Tags Events
// @Produce json
// @Param limit query int false "Maximum results" default(25)
// @Param offset query int false "Results offset" default(0)
// @Success 200 {array} EventSummaryResponse
// @Failure 500 {object} APIError
// @Router /v1/events [get]
func (s *Server) handleListEvents(w http.ResponseWriter, r *http.Request) {
	limit, offset := s.paginate(r, 25)
	rows, err := s.queries.ListEvents(r.Context(), db.ListEventsParams{Limit: limit, Offset: offset})
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list events", err.Error())
		return
	}
	resp := make([]EventSummaryResponse, 0, len(rows))
	for _, row := range rows {
		resp = append(resp, mapEventSummary(row))
	}
	s.writeJSON(w, http.StatusOK, resp)
}

// handleCreateEvent godoc
// @Summary Create event
// @Description Registers a new incident in the system.
// @Tags Events
// @Accept json
// @Produce json
// @Param request body CreateEventRequest true "Event payload"
// @Success 201 {object} EventSummaryResponse
// @Failure 400 {object} APIError
// @Failure 500 {object} APIError
// @Router /v1/events [post]
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

	summary := mapCreateEventRow(row)
	s.writeJSON(w, http.StatusCreated, summary)
}

// handleGetEvent godoc
// @Summary Get event
// @Description Returns a detailed view of a specific incident.
// @Tags Events
// @Produce json
// @Param eventID path string true "Event ID"
// @Success 200 {object} EventDetailResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Router /v1/events/{eventID} [get]
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

	logs, err := s.queries.ListEventLogs(r.Context(), db.ListEventLogsParams{EventID: eventID, Limit: 50, Offset: 0})
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to fetch event logs", err.Error())
		return
	}

	resp := mapEventDetail(eventRow, interventions, logs)
	s.writeJSON(w, http.StatusOK, resp)
}

// handleUpdateEventStatus godoc
// @Summary Update event status
// @Description Sets the workflow status of an incident.
// @Tags Events
// @Accept json
// @Produce json
// @Param eventID path string true "Event ID"
// @Param request body UpdateEventStatusRequest true "Status payload"
// @Success 200 {object} EventSummaryResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Router /v1/events/{eventID}/status [patch]
func (s *Server) handleUpdateEventStatus(w http.ResponseWriter, r *http.Request) {
	eventID, err := s.parseUUIDParam(r, "eventID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidEventID, err.Error())
		return
	}

	var req UpdateEventStatusRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	row, err := s.queries.UpdateEventStatus(r.Context(), db.UpdateEventStatusParams{
		ID:     eventID,
		Status: db.EventStatus(req.Status),
	})
	if err != nil {
		if isNotFound(err) {
			s.writeError(w, http.StatusNotFound, "event not found", nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to update status", err.Error())
		return
	}

	s.writeJSON(w, http.StatusOK, mapUpdateEventRow(row))
}

// handleCreateEventLog godoc
// @Summary Append event log entry
// @Description Adds an entry to an incident timeline.
// @Tags Events
// @Accept json
// @Produce json
// @Param eventID path string true "Event ID"
// @Param request body CreateEventLogRequest true "Log payload"
// @Success 201 {object} EventLogResponse
// @Failure 400 {object} APIError
// @Failure 500 {object} APIError
// @Router /v1/events/{eventID}/logs [post]
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

	params := db.CreateEventLogParams{
		EventID: eventID,
		Code:    req.Code,
		Actor:   req.Actor,
		Payload: rawJSONOrEmpty(req.Payload),
	}

	logRow, err := s.queries.CreateEventLog(r.Context(), params)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to create log", err.Error())
		return
	}

	s.writeJSON(w, http.StatusCreated, mapEventLog(logRow))
}

// handleListEventLogs godoc
// @Summary List event logs
// @Description Retrieves paginated timeline entries for an incident.
// @Tags Events
// @Produce json
// @Param eventID path string true "Event ID"
// @Param limit query int false "Maximum results" default(50)
// @Param offset query int false "Results offset" default(0)
// @Success 200 {array} EventLogResponse
// @Failure 400 {object} APIError
// @Failure 500 {object} APIError
// @Router /v1/events/{eventID}/logs [get]
func (s *Server) handleListEventLogs(w http.ResponseWriter, r *http.Request) {
	eventID, err := s.parseUUIDParam(r, "eventID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidEventID, err.Error())
		return
	}
	limit, offset := s.paginate(r, 50)

	rows, err := s.queries.ListEventLogs(r.Context(), db.ListEventLogsParams{EventID: eventID, Limit: limit, Offset: offset})
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list logs", err.Error())
		return
	}

	resp := make([]EventLogResponse, 0, len(rows))
	for _, row := range rows {
		resp = append(resp, mapEventLog(row))
	}

	s.writeJSON(w, http.StatusOK, resp)
}

func mapEventSummary(row db.ListEventsRow) EventSummaryResponse {
	return EventSummaryResponse{
		ID:            uuidString(row.ID),
		Title:         row.Title,
		Description:   optionalString(row.Description),
		ReportSource:  optionalString(row.ReportSource),
		Address:       optionalString(row.Address),
		Location:      GeoPoint{Latitude: row.Latitude, Longitude: row.Longitude},
		Severity:      row.Severity,
		Status:        string(row.Status),
		EventTypeCode: row.EventTypeCode,
		EventTypeName: row.EventTypeName,
		ReportedAt:    row.ReportedAt.Time,
		UpdatedAt:     row.UpdatedAt.Time,
		ClosedAt:      timestamptzPtr(row.ClosedAt),
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
		Status:        string(row.Status),
		EventTypeCode: row.EventTypeCode,
		ReportedAt:    row.ReportedAt.Time,
		UpdatedAt:     row.UpdatedAt.Time,
		ClosedAt:      timestamptzPtr(row.ClosedAt),
	}
}

func mapUpdateEventRow(row db.UpdateEventStatusRow) EventSummaryResponse {
	return EventSummaryResponse{
		ID:            uuidString(row.ID),
		Title:         row.Title,
		Description:   optionalString(row.Description),
		ReportSource:  optionalString(row.ReportSource),
		Address:       optionalString(row.Address),
		Location:      GeoPoint{Latitude: row.Latitude, Longitude: row.Longitude},
		Severity:      row.Severity,
		Status:        string(row.Status),
		EventTypeCode: row.EventTypeCode,
		ReportedAt:    row.ReportedAt.Time,
		UpdatedAt:     row.UpdatedAt.Time,
		ClosedAt:      timestamptzPtr(row.ClosedAt),
	}
}

func mapEventDetail(event db.GetEventRow, interventions []db.Intervention, logs []db.EventLog) EventDetailResponse {
	summary := EventSummaryResponse{
		ID:            uuidString(event.ID),
		Title:         event.Title,
		Description:   optionalString(event.Description),
		ReportSource:  optionalString(event.ReportSource),
		Address:       optionalString(event.Address),
		Location:      GeoPoint{Latitude: event.Latitude, Longitude: event.Longitude},
		Severity:      event.Severity,
		Status:        string(event.Status),
		EventTypeCode: event.EventTypeCode,
		EventTypeName: event.EventTypeName,
		ReportedAt:    event.ReportedAt.Time,
		UpdatedAt:     event.UpdatedAt.Time,
		ClosedAt:      timestamptzPtr(event.ClosedAt),
	}

	resp := EventDetailResponse{
		EventSummaryResponse: summary,
		RecommendedUnitTypes: event.RecommendedUnitTypes,
	}

	for _, intervention := range interventions {
		resp.Interventions = append(resp.Interventions, mapIntervention(intervention))
	}

	for _, logRow := range logs {
		resp.Logs = append(resp.Logs, mapEventLog(logRow))
	}

	return resp
}

func mapEventLog(row db.EventLog) EventLogResponse {
	return EventLogResponse{
		ID:        row.ID,
		EventID:   uuidString(row.EventID),
		CreatedAt: row.CreatedAt.Time,
		Code:      row.Code,
		Actor:     optionalString(row.Actor),
		Payload:   RawJSON(row.Payload),
	}
}
