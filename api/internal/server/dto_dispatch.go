package server

import "time"

// =============================================================================
// Dispatch Configuration DTOs
// =============================================================================

// DispatchConfigItem represents a single configuration parameter.
type DispatchConfigItem struct {
	Key         string     `json:"key"`
	Value       float64    `json:"value"`
	Description string     `json:"description"`
	MinValue    *float64   `json:"min_value,omitempty"`
	MaxValue    *float64   `json:"max_value,omitempty"`
	UpdatedAt   time.Time  `json:"updated_at"`
}

// DispatchConfigResponse is the response for GET /v1/dispatch/config.
type DispatchConfigResponse struct {
	Items []DispatchConfigItem `json:"items"`
}

// UpdateDispatchConfigRequest is the request for PUT /v1/dispatch/config.
type UpdateDispatchConfigRequest struct {
	Key   string  `json:"key" validate:"required"`
	Value float64 `json:"value" validate:"required"`
}

// BatchUpdateDispatchConfigRequest is the request for batch config updates.
type BatchUpdateDispatchConfigRequest struct {
	Items []UpdateDispatchConfigRequest `json:"items" validate:"required,min=1,dive"`
}

// =============================================================================
// Static Data DTOs (for engine startup)
// =============================================================================

// BaseInfo represents a unit parking base with coverage info.
type BaseInfo struct {
	Name           string `json:"name"`
	AvailableUnits int64  `json:"available_units"`
	TotalUnits     int64  `json:"total_units"`
}

// StaticDataResponse is the response for GET /v1/dispatch/static.
type StaticDataResponse struct {
	Config     []DispatchConfigItem `json:"config"`
	UnitTypes  []UnitTypeResponse   `json:"unit_types"`
	EventTypes []EventTypeResponse  `json:"event_types"`
	Bases      []BaseInfo           `json:"bases"`
}

// =============================================================================
// Dispatch Candidate DTOs
// =============================================================================

// DispatchCandidate represents a candidate unit for dispatch with scoring info.
type DispatchCandidate struct {
	ID                           string   `json:"id"`
	CallSign                     string   `json:"call_sign"`
	UnitTypeCode                 string   `json:"unit_type_code"`
	HomeBase                     string   `json:"home_base"`
	Status                       string   `json:"status"`
	Location                     GeoPoint `json:"location"`
	TravelTimeSeconds            float64  `json:"travel_time_seconds"`
	DistanceMeters               float64  `json:"distance_meters"`
	OtherUnitsAtBase             int      `json:"other_units_at_base"`
	CurrentAssignmentID          *string  `json:"current_assignment_id,omitempty"`
	CurrentInterventionID        *string  `json:"current_intervention_id,omitempty"`
	CurrentInterventionSeverity  *int32   `json:"current_intervention_severity,omitempty"`
	CurrentInterventionPriority  *int32   `json:"current_intervention_priority,omitempty"`
}

// DispatchCandidatesResponse is the response for GET /v1/interventions/{id}/candidates.
type DispatchCandidatesResponse struct {
	InterventionID       string              `json:"intervention_id"`
	EventSeverity        int32               `json:"event_severity"`
	RecommendedUnitTypes []string            `json:"recommended_unit_types"`
	Candidates           []DispatchCandidate `json:"candidates"`
}

// =============================================================================
// Pending Interventions DTOs
// =============================================================================

// PendingIntervention represents an intervention awaiting dispatch.
type PendingIntervention struct {
	InterventionID      string    `json:"intervention_id"`
	EventID             string    `json:"event_id"`
	Status              string    `json:"status"`
	Priority            int32     `json:"priority"`
	EventSeverity       int32     `json:"event_severity"`
	EventTypeCode       string    `json:"event_type_code"`
	RecommendedTypes    []string  `json:"recommended_unit_types"`
	Location            GeoPoint  `json:"location"`
	AssignedUnitsCount  int64     `json:"assigned_units_count"`
	CreatedAt           time.Time `json:"created_at"`
}

// PendingInterventionsResponse is the response for GET /v1/dispatch/pending.
type PendingInterventionsResponse struct {
	Interventions []PendingIntervention `json:"interventions"`
}

// =============================================================================
// Intervention Dispatch Info DTO
// =============================================================================

// InterventionDispatchInfo contains details needed for dispatch decision.
type InterventionDispatchInfo struct {
	InterventionID       string   `json:"intervention_id"`
	EventID              string   `json:"event_id"`
	Status               string   `json:"status"`
	Priority             int32    `json:"priority"`
	DecisionMode         string   `json:"decision_mode"`
	EventTitle           string   `json:"event_title"`
	EventSeverity        int32    `json:"event_severity"`
	EventTypeCode        string   `json:"event_type_code"`
	RecommendedUnitTypes []string `json:"recommended_unit_types"`
	Location             GeoPoint `json:"location"`
}
