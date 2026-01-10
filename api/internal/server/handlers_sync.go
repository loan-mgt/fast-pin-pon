package server

import (
	"context"
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

	// Parse parameters
	limitEvents, _ := strconv.Atoi(r.URL.Query().Get("limit_events"))
	if limitEvents <= 0 {
		limitEvents = 25
	}
	limitLogs, _ := strconv.Atoi(r.URL.Query().Get("limit_logs"))
	if limitLogs <= 0 {
		limitLogs = 10
	}

	// Fetch all data concurrently
	eventsResp, err := s.fetchEventsForSync(ctx, limitEvents, r.URL.Query().Get("deny_status"))
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to fetch events", err.Error())
		return
	}

	unitsResp, err := s.fetchUnitsForSync(ctx)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to fetch units", err.Error())
		return
	}

	logsResp, err := s.fetchActivityLogsForSync(ctx, limitLogs)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to fetch activity logs", err.Error())
		return
	}

	// Write consolidated response
	s.writeJSON(w, http.StatusOK, SyncResponse{
		Events:     eventsResp,
		Units:      unitsResp,
		RecentLogs: logsResp,
	})
}

// fetchEventsForSync retrieves events with assigned units, filtered by deny_status
func (s *Server) fetchEventsForSync(ctx context.Context, limit int, denyStatusParam string) ([]EventSummaryResponse, error) {
	eventRows, err := s.queries.ListEvents(ctx, db.ListEventsParams{Limit: int32(limit), Offset: 0})
	if err != nil {
		return nil, err
	}

	denySet := s.parseDenySet(denyStatusParam)
	if denySet == nil {
		// Default behavior: exclude completed/cancelled
		denySet = map[db.InterventionStatus]struct{}{
			db.InterventionStatusCompleted: {},
			db.InterventionStatusCancelled: {},
		}
	}

	eventsResp := make([]EventSummaryResponse, 0, len(eventRows))
	for _, row := range eventRows {
		// Skip if intervention status is denied
		if denySet != nil && row.InterventionStatus.Valid {
			if _, denied := denySet[row.InterventionStatus.InterventionStatus]; denied {
				continue
			}
		}

		// Fetch assigned units for this event
		assigned, err := s.queries.ListUnitsAssignedToEvent(ctx, row.ID)
		if err != nil {
			return nil, err
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

	return eventsResp, nil
}

// fetchUnitsForSync retrieves all units
func (s *Server) fetchUnitsForSync(ctx context.Context) ([]UnitResponse, error) {
	unitRows, err := s.queries.ListUnits(ctx)
	if err != nil {
		return nil, err
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

	return unitsResp, nil
}

// fetchActivityLogsForSync retrieves recent activity logs (status changes)
func (s *Server) fetchActivityLogsForSync(ctx context.Context, limit int) ([]ActivityLogResponse, error) {
	activityType := "status_change"
	logRows, err := s.queries.ListRecentActivityLogs(ctx, db.ListRecentActivityLogsParams{
		ActivityType: &activityType,
		Limit:        int32(limit),
	})
	if err != nil {
		return nil, err
	}

	logsResp := make([]ActivityLogResponse, 0, len(logRows))
	for _, row := range logRows {
		logsResp = append(logsResp, mapActivityLog(row))
	}

	return logsResp, nil
}
