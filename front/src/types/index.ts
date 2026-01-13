export type GeoPoint = {
    latitude: number
    longitude: number
}

export type EventSummary = {
    id: string
    title: string
    description?: string
    report_source?: string
    address?: string
    location: GeoPoint
    severity: number
    auto_simulated: boolean
    intervention_id?: string
    intervention_status?: string
    event_type_code: string
    event_type_name: string
    reported_at: string
    updated_at: string
    closed_at?: string
    started_at?: string
    completed_at?: string
    assigned_units?: UnitSummary[]
}

export type UnitSummary = {
    id: string
    call_sign: string
    unit_type_code: string
    home_base?: string
    location_id?: string
    status: string
    microbit_id?: string
    location: GeoPoint
    distance_meters?: number
    last_contact_at: string
    created_at: string
    updated_at: string
}

export type EventLog = {
    id: number
    event_id: string
    event_title: string
    event_type_code: string
    created_at: string
    code: string
    actor?: string
    payload: unknown
}

export type ActivityLog = {
    id: number
    activity_type: string
    entity_type?: string
    entity_id?: string
    actor?: string
    old_value?: string
    new_value?: string
    created_at: string
    // Enriched fields from API joins
    unit_call_sign?: string
    event_title?: string
    event_id?: string
}

export type UnitType = {
    code: string
    name: string
    capabilities: string
    speed_kmh?: number
    max_crew?: number
    illustration?: string
}

export type { EventType, CreateEventRequest } from './eventTypes'

export type Building = {
    id: string
    name: string
    type: string
    location: GeoPoint
    created_at: string
    updated_at: string
}

export interface DetailedHealthResponse {
    services: {
        database: 'up' | 'down'
        simulation: 'up' | 'down'
        engine: 'up' | 'down'
    }
    mode: 'demo' | 'hybrid' | 'unknown'
    microbit_network: {
        status: 'active' | 'inactive'
        last_message_at?: string
        seconds_since_last: number
    }
    system_stats: {
        active_units: number
        active_incidents: number
    }
    uptime: string
}
