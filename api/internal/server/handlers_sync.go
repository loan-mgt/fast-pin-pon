package server

import (
	db "fast/pin/internal/db/sqlc"
	"net/http"
	"strconv"
)

// handleSync godoc
// @Title Sync status
// @Description Returns consolidated status including events, units and recent logs.
// @Resource Common
// @Produce json
// @Param limit_events query int false "Maximum events" default(25)
// @Param limit_logs query int false "Maximum logs" default(3)
// @Param deny_status query string false "Comma-separated intervention statuses to exclude (e.g., completed,cancelled)"
// @Success 200 {object} SyncResponse
// @Failure 500 {object} APIError
// @Route /v1/sync [get]
func (s *Server) handleSync(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()

	// 1. Fetch Events (similar to handleListEvents)
	limitEvents, _ := strconv.Atoi(r.URL.Query().Get("limit_events"))
	if limitEvents <= 0 {
		limitEvents = 25
	}
	eventRows, err := s.queries.ListEvents(ctx, db.ListEventsParams{Limit: int32(limitEvents), Offset: 0})
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list events", err.Error())
		return
	}

	denySet := s.parseDenySet(r.URL.Query().Get("deny_status"))
	if denySet == nil {
		// Default behavior for map: exclude completed/cancelled
		denySet = map[db.InterventionStatus]struct{}{
			db.InterventionStatusCompleted: {},
			db.InterventionStatusCancelled: {},
		}
	}

	eventsResp := make([]EventSummaryResponse, 0, len(eventRows))
	for _, row := range eventRows {
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
		eventsResp = append(eventsResp, mapEventSummary(row, assignedUnits))
	}

	// 2. Fetch Units (similar to handleListUnits)
	unitRows, err := s.queries.ListUnits(ctx)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list units", err.Error())
		return
	}
	unitsResp := make([]UnitResponse, 0, len(unitRows))
	for _, row := range unitRows {
		unitsResp = append(unitsResp, mapUnitRow(unitRowData{
			ID:           row.ID,
			CallSign:     row.CallSign,
			UnitTypeCode: row.UnitTypeCode,
			HomeBaseName: row.HomeBaseName,
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

	// 3. Fetch Recent Event Logs (similar to handleListRecentEventLogs)
	limitLogs, _ := strconv.Atoi(r.URL.Query().Get("limit_logs"))
	if limitLogs <= 0 {
		limitLogs = 3
	}
	logRows, err := s.queries.ListRecentEventLogs(ctx, int32(limitLogs))
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to list recent event logs", err.Error())
		return
	}
	logsResp := make([]EventLogWithEventResponse, 0, len(logRows))
	for _, row := range logRows {
		logsResp = append(logsResp, mapEventLogWithEvent(row))
	}

	// Aggregate and Write response
	s.writeJSON(w, http.StatusOK, SyncResponse{
		Events:     eventsResp,
		Units:      unitsResp,
		RecentLogs: logsResp,
	})
}
