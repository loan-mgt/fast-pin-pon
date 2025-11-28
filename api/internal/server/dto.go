package server

import "time"

type RawJSON []byte

func (r RawJSON) MarshalJSON() ([]byte, error) {
	if r == nil {
		return []byte("null"), nil
	}
	return []byte(r), nil
}

func (r *RawJSON) UnmarshalJSON(data []byte) error {
	if r == nil {
		return nil
	}
	*r = append((*r)[:0], data...)
	return nil
}

type GeoPoint struct {
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
}

type EventSummaryResponse struct {
	ID            string     `json:"id"`
	Title         string     `json:"title"`
	Description   string     `json:"description,omitempty"`
	ReportSource  string     `json:"report_source,omitempty"`
	Address       string     `json:"address,omitempty"`
	Location      GeoPoint   `json:"location"`
	Severity      int32      `json:"severity"`
	Status        string     `json:"status"`
	EventTypeCode string     `json:"event_type_code"`
	EventTypeName string     `json:"event_type_name"`
	ReportedAt    time.Time  `json:"reported_at"`
	UpdatedAt     time.Time  `json:"updated_at"`
	ClosedAt      *time.Time `json:"closed_at,omitempty"`
}

type EventDetailResponse struct {
	EventSummaryResponse
	RecommendedUnitTypes []string               `json:"recommended_unit_types"`
	Interventions        []InterventionResponse `json:"interventions,omitempty"`
	Logs                 []EventLogResponse     `json:"logs,omitempty"`
}

type EventLogResponse struct {
	ID        int64     `json:"id"`
	EventID   string    `json:"event_id"`
	CreatedAt time.Time `json:"created_at"`
	Code      string    `json:"code"`
	Actor     string    `json:"actor,omitempty"`
	Payload   RawJSON   `json:"payload"`
}

type EventTypeResponse struct {
	Code                 string   `json:"code"`
	Name                 string   `json:"name"`
	Description          string   `json:"description"`
	DefaultSeverity      int32    `json:"default_severity"`
	RecommendedUnitTypes []string `json:"recommended_unit_types"`
}

type UnitTypeResponse struct {
	Code         string `json:"code"`
	Name         string `json:"name"`
	Capabilities string `json:"capabilities"`
	SpeedKMH     *int32 `json:"speed_kmh,omitempty"`
	MaxCrew      *int32 `json:"max_crew,omitempty"`
	Illustration string `json:"illustration,omitempty"`
}

type InterventionResponse struct {
	ID           string               `json:"id"`
	EventID      string               `json:"event_id"`
	Status       string               `json:"status"`
	Priority     int32                `json:"priority"`
	DecisionMode string               `json:"decision_mode"`
	CreatedBy    string               `json:"created_by,omitempty"`
	Notes        string               `json:"notes,omitempty"`
	CreatedAt    time.Time            `json:"created_at"`
	StartedAt    *time.Time           `json:"started_at,omitempty"`
	CompletedAt  *time.Time           `json:"completed_at,omitempty"`
	Assignments  []AssignmentResponse `json:"assignments,omitempty"`
}

type AssignmentResponse struct {
	ID             string     `json:"id"`
	InterventionID string     `json:"intervention_id"`
	UnitID         string     `json:"unit_id"`
	UnitCallSign   string     `json:"unit_call_sign,omitempty"`
	UnitTypeCode   string     `json:"unit_type_code,omitempty"`
	Role           string     `json:"role,omitempty"`
	Status         string     `json:"status"`
	DispatchedAt   time.Time  `json:"dispatched_at"`
	ArrivedAt      *time.Time `json:"arrived_at,omitempty"`
	ReleasedAt     *time.Time `json:"released_at,omitempty"`
}

type UnitResponse struct {
	ID           string     `json:"id"`
	CallSign     string     `json:"call_sign"`
	UnitTypeCode string     `json:"unit_type_code"`
	HomeBase     string     `json:"home_base,omitempty"`
	Status       string     `json:"status"`
	Location     GeoPoint   `json:"location"`
	LastContact  *time.Time `json:"last_contact_at,omitempty"`
	CreatedAt    time.Time  `json:"created_at"`
	UpdatedAt    time.Time  `json:"updated_at"`
}

type TelemetryResponse struct {
	ID         int64     `json:"id"`
	UnitID     string    `json:"unit_id"`
	RecordedAt time.Time `json:"recorded_at"`
	Location   GeoPoint  `json:"location"`
	Heading    *int32    `json:"heading,omitempty"`
	SpeedKMH   *float64  `json:"speed_kmh,omitempty"`
	Status     RawJSON   `json:"status_snapshot"`
}

type HealthResponse struct {
	Status string `json:"status"`
	Env    string `json:"env"`
	Uptime string `json:"uptime"`
}
