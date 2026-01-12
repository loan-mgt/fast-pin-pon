package server

import (
	"context"
	"encoding/json"

	db "fast/pin/internal/db/sqlc"

	"github.com/jackc/pgx/v5/pgtype"
)

// mapActivityLog maps a database activity log row to the API response
func mapActivityLog(row db.ListRecentActivityLogsRow) ActivityLogResponse {
	resp := ActivityLogResponse{
		ID:           row.ID,
		ActivityType: row.ActivityType,
		EntityType:   row.EntityType,
		Actor:        row.Actor,
		OldValue:     row.OldValue,
		NewValue:     row.NewValue,
		CreatedAt:    row.CreatedAt.Time,
		UnitCallSign: row.UnitCallSign,
		EventTitle:   row.EventTitle,
	}

	if row.EntityID.Valid {
		s := uuidString(row.EntityID)
		resp.EntityID = &s
	}
	if row.EventID.Valid {
		s := uuidString(row.EventID)
		resp.EventID = &s
	}

	return resp
}

// logUnitStatusChange creates an activity log for unit status change
func (s *Server) logUnitStatusChange(ctx context.Context, unitID pgtype.UUID, callSign string, oldStatus, newStatus string, actor *string) error {
	metadata := map[string]string{"call_sign": callSign}
	metadataJSON, _ := json.Marshal(metadata)

	entityType := "unit"
	_, err := s.queries.CreateActivityLog(ctx, db.CreateActivityLogParams{
		ActivityType: "status_change",
		EntityType:   &entityType,
		EntityID:     unitID,
		Actor:        actor,
		OldValue:     &oldStatus,
		NewValue:     &newStatus,
		Metadata:     metadataJSON,
	})
	return err
}

// logInterventionStatusChange creates an activity log for intervention status change
func (s *Server) logInterventionStatusChange(ctx context.Context, interventionID pgtype.UUID, eventID pgtype.UUID, oldStatus, newStatus string, actor *string) error {
	eventIDStr := uuidString(eventID)
	metadata := map[string]string{"event_id": eventIDStr}
	metadataJSON, _ := json.Marshal(metadata)

	entityType := "intervention"
	_, err := s.queries.CreateActivityLog(ctx, db.CreateActivityLogParams{
		ActivityType: "status_change",
		EntityType:   &entityType,
		EntityID:     interventionID,
		Actor:        actor,
		OldValue:     &oldStatus,
		NewValue:     &newStatus,
		Metadata:     metadataJSON,
	})
	return err
}
