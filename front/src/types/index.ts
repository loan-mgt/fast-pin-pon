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
    intervention_id?: string
    intervention_status?: string
    event_type_code: string
    event_type_name: string
    reported_at: string
    updated_at: string
    closed_at?: string
}

export type UnitSummary = {
    id: string
    call_sign: string
    unit_type_code: string
    home_base: string
    status: string
    microbit_id?: string
    location: GeoPoint
    last_contact_at: string
    created_at: string
    updated_at: string
}

export type { EventType, CreateEventRequest } from './eventTypes'
