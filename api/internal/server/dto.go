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
	ID                 string         `json:"id"`
	Title              string         `json:"title"`
	Description        string         `json:"description,omitempty"`
	ReportSource       string         `json:"report_source,omitempty"`
	Address            string         `json:"address,omitempty"`
	Location           GeoPoint       `json:"location"`
	Severity           int32          `json:"severity"`
	InterventionID     *string        `json:"intervention_id,omitempty"`
	InterventionStatus *string        `json:"intervention_status,omitempty"`
	EventTypeCode      string         `json:"event_type_code"`
	EventTypeName      string         `json:"event_type_name"`
	ReportedAt         time.Time      `json:"reported_at"`
	UpdatedAt          time.Time      `json:"updated_at"`
	ClosedAt           *time.Time     `json:"closed_at,omitempty"`
	StartedAt          *time.Time     `json:"started_at,omitempty"`
	CompletedAt        *time.Time     `json:"completed_at,omitempty"`
	AssignedUnits      []UnitResponse `json:"assigned_units,omitempty"`
}

type EventDetailResponse struct {
	EventSummaryResponse
	RecommendedUnitTypes []string              `json:"recommended_unit_types"`
	Intervention         *InterventionResponse `json:"intervention,omitempty"`
	Logs                 []EventLogResponse    `json:"logs,omitempty"`
}

type EventLogResponse struct {
	ID        int64     `json:"id"`
	EventID   string    `json:"event_id"`
	CreatedAt time.Time `json:"created_at"`
	Code      string    `json:"code"`
	Actor     string    `json:"actor,omitempty"`
	Payload   RawJSON   `json:"payload"`
}

type EventLogWithEventResponse struct {
	ID            int64     `json:"id"`
	EventID       string    `json:"event_id"`
	EventTitle    string    `json:"event_title"`
	EventTypeCode string    `json:"event_type_code"`
	CreatedAt     time.Time `json:"created_at"`
	Code          string    `json:"code"`
	Actor         string    `json:"actor,omitempty"`
	Payload       RawJSON   `json:"payload"`
	EntityType    string    `json:"entity_type,omitempty"`
	EntityID      string    `json:"entity_id,omitempty"`
	OldValue      string    `json:"old_value,omitempty"`
	NewValue      string    `json:"new_value,omitempty"`
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
	ID             string     `json:"id"`
	CallSign       string     `json:"call_sign"`
	UnitTypeCode   string     `json:"unit_type_code"`
	HomeBase       string     `json:"home_base,omitempty"`
	LocationID     string     `json:"location_id,omitempty"`
	Status         string     `json:"status"`
	MicrobitID     string     `json:"microbit_id,omitempty"`
	Location       GeoPoint   `json:"location"`
	DistanceMeters *float64   `json:"distance_meters,omitempty"`
	LastContact    *time.Time `json:"last_contact_at,omitempty"`
	CreatedAt      time.Time  `json:"created_at"`
	UpdatedAt      time.Time  `json:"updated_at"`
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

type ActivityLogResponse struct {
	ID           int64     `json:"id"`
	ActivityType string    `json:"activity_type"`
	EntityType   *string   `json:"entity_type,omitempty"`
	EntityID     *string   `json:"entity_id,omitempty"`
	Actor        *string   `json:"actor,omitempty"`
	OldValue     *string   `json:"old_value,omitempty"`
	NewValue     *string   `json:"new_value,omitempty"`
	CreatedAt    time.Time `json:"created_at"`
	// Enriched fields from joins
	UnitCallSign *string `json:"unit_call_sign,omitempty"`
	EventTitle   *string `json:"event_title,omitempty"`
	EventID      *string `json:"event_id,omitempty"`
}

type HealthResponse struct {
	Status string `json:"status"`
	Env    string `json:"env"`
	Uptime string `json:"uptime"`
}

type SyncResponse struct {
	Events     []EventSummaryResponse `json:"events"`
	Units      []UnitResponse         `json:"units"`
	RecentLogs []ActivityLogResponse  `json:"recent_logs"`
}

type LocationResponse struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	Type      string    `json:"type"`
	Location  GeoPoint  `json:"location"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}
