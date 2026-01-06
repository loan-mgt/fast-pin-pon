export type EventType = {
  code: string
  name: string
  description: string
  default_severity: number
  recommended_unit_types: string[]
}

export type CreateEventRequest = {
  title: string
  description?: string | null
  report_source?: string | null
  address?: string | null
  latitude: number
  longitude: number
  severity: number
  event_type_code: string
}
