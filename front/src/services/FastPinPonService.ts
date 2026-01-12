import type { EventSummary, UnitSummary, UnitType, Building, EventLog, ActivityLog, DetailedHealthResponse } from '../types'
import type { CreateEventRequest, EventType } from '../types/eventTypes'

class FastPinPonService {
  private readonly API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'https://api.fast-pin-pon.4loop.org/v1'

  private buildHeaders(token?: string): HeadersInit {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
    }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
    return headers
  }

  async getEvents(limit = 25, token?: string, denyStatuses?: string[]): Promise<EventSummary[]> {
    const params = new URLSearchParams({ limit: String(limit) })
    if (denyStatuses && denyStatuses.length > 0) {
      params.set('deny_status', denyStatuses.join(','))
    }
    const response = await fetch(`${this.API_BASE_URL}/events?${params.toString()}`, {
      headers: this.buildHeaders(token),
    })
    if (!response.ok) {
      throw new Error(`Failed to fetch events: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  async getEventTypes(token?: string): Promise<EventType[]> {
    const response = await fetch(`${this.API_BASE_URL}/event-types`, {
      headers: this.buildHeaders(token),
    })
    if (!response.ok) {
      throw new Error(`Failed to fetch event types: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  async getUnitTypes(token?: string): Promise<UnitType[]> {
    const response = await fetch(`${this.API_BASE_URL}/unit-types`, {
      headers: this.buildHeaders(token),
    })
    if (!response.ok) {
      throw new Error(`Failed to fetch unit types: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  async getBuildings(token?: string): Promise<Building[]> {
    const response = await fetch(`${this.API_BASE_URL}/buildings`, {
      headers: this.buildHeaders(token),
    })
    if (!response.ok) {
      throw new Error(`Failed to fetch buildings: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  async createEvent(payload: CreateEventRequest, autoIntervention = true, token?: string): Promise<EventSummary> {
    const url = new URL(`${this.API_BASE_URL}/events`)
    if (autoIntervention) {
      url.searchParams.set('auto_intervention', 'true')
    }
    const response = await fetch(url.toString(), {
      method: 'POST',
      headers: this.buildHeaders(token),
      body: JSON.stringify(payload),
    })
    if (!response.ok) {
      throw new Error(`Failed to create event: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  async getRecentEventLogs(limit = 10, token?: string): Promise<EventLog[]> {
    const params = new URLSearchParams({ limit: String(limit) })
    const response = await fetch(`${this.API_BASE_URL}/event-logs/recent?${params.toString()}`, {
      headers: this.buildHeaders(token),
    })
    if (!response.ok) {
      throw new Error(`Failed to fetch recent event logs: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  async updateInterventionStatus(interventionId: string, status: string, token?: string): Promise<void> {
    const response = await fetch(`${this.API_BASE_URL}/interventions/${interventionId}/status`, {
      method: 'PATCH',
      headers: this.buildHeaders(token),
      body: JSON.stringify({ status }),
    })
    if (!response.ok) {
      throw new Error(`Failed to update intervention status: ${response.status} ${response.statusText}`)
    }
  }

  async getNearbyUnits(lat: number, lon: number, unitTypes: string[] = [], token?: string): Promise<UnitSummary[]> {
    const params = new URLSearchParams({
      lat: String(lat),
      lon: String(lon),
    })
    if (unitTypes.length > 0) {
      params.set('unit_types', unitTypes.join(','))
    }
    const response = await fetch(`${this.API_BASE_URL}/units/nearby?${params.toString()}`, {
      headers: this.buildHeaders(token),
    })
    if (!response.ok) {
      throw new Error(`Failed to fetch nearby units: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  async assignUnitToIntervention(interventionId: string, unitId: string, token?: string): Promise<void> {
    const response = await fetch(`${this.API_BASE_URL}/interventions/${interventionId}/assignments`, {
      method: 'POST',
      headers: this.buildHeaders(token),
      body: JSON.stringify({ unit_id: unitId }),
    })
    if (!response.ok) {
      throw new Error(`Failed to assign unit: ${response.status} ${response.statusText}`)
    }
  }

  async unassignUnitFromIntervention(interventionId: string, unitId: string, token?: string): Promise<void> {
    const response = await fetch(`${this.API_BASE_URL}/interventions/${interventionId}/assignments/${unitId}`, {
      method: 'DELETE',
      headers: this.buildHeaders(token),
    })
    if (!response.ok) {
      throw new Error(`Failed to unassign unit: ${response.status} ${response.statusText}`)
    }
  }

  async createUnit(callSign: string, unitTypeCode: string, latitude: number, longitude: number, token?: string, locationId?: string): Promise<UnitSummary> {
    const response = await fetch(`${this.API_BASE_URL}/units`, {
      method: 'POST',
      headers: this.buildHeaders(token),
      body: JSON.stringify({
        call_sign: callSign,
        unit_type_code: unitTypeCode,
        location_id: locationId,
        latitude,
        longitude,
        status: 'available',
      }),
    })
    if (!response.ok) {
      throw new Error(`Failed to create unit: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  async updateUnitStatus(unitId: string, status: string, token?: string): Promise<UnitSummary> {
    const response = await fetch(`${this.API_BASE_URL}/units/${unitId}/status`, {
      method: 'PATCH',
      headers: this.buildHeaders(token),
      body: JSON.stringify({ status }),
    })
    if (!response.ok) {
      throw new Error(`Failed to update unit status: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  async updateUnitStation(unitId: string, locationId: string | null, token?: string): Promise<UnitSummary> {
    const response = await fetch(`${this.API_BASE_URL}/units/${unitId}/station`, {
      method: 'PATCH',
      headers: this.buildHeaders(token),
      body: JSON.stringify({ location_id: locationId }),
    })
    if (!response.ok) {
      throw new Error(`Failed to update unit station: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  async deleteUnit(unitId: string, token?: string): Promise<void> {
    const response = await fetch(`${this.API_BASE_URL}/units/${unitId}`, {
      method: 'DELETE',
      headers: this.buildHeaders(token),
    })
    if (!response.ok) {
      throw new Error(`Failed to delete unit: ${response.status} ${response.statusText}`)
    }
  }

  async assignMicrobit(unitId: string, microbitId: string, token?: string): Promise<UnitSummary> {
    const response = await fetch(`${this.API_BASE_URL}/units/${unitId}/microbit`, {
      method: 'PUT',
      headers: this.buildHeaders(token),
      body: JSON.stringify({ microbit_id: microbitId }),
    })
    if (!response.ok) {
      throw new Error(`Failed to assign microbit: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  async unassignMicrobit(unitId: string, token?: string): Promise<UnitSummary> {
    const response = await fetch(`${this.API_BASE_URL}/units/${unitId}/microbit`, {
      method: 'DELETE',
      headers: this.buildHeaders(token),
    })
    if (!response.ok) {
      throw new Error(`Failed to unassign microbit: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  async getUnits(token?: string): Promise<UnitSummary[]> {
    const response = await fetch(`${this.API_BASE_URL}/units`, {
      headers: this.buildHeaders(token),
    })
    if (!response.ok) {
      throw new Error(`Failed to fetch units: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  /**
   * Fetches consolidated status (events, units, recent logs).
   */
  async getSync(limitEvents = 25, limitLogs = 3, token?: string, denyStatuses?: string[]): Promise<{
    events: EventSummary[],
    units: UnitSummary[],
    recent_logs: ActivityLog[]
  }> {
    const params = new URLSearchParams({
      limit_events: String(limitEvents),
      limit_logs: String(limitLogs)
    })
    if (denyStatuses && denyStatuses.length > 0) {
      params.set('deny_status', denyStatuses.join(','))
    }
    const response = await fetch(`${this.API_BASE_URL}/sync?${params.toString()}`, {
      headers: this.buildHeaders(token),
    })
    if (!response.ok) {
      throw new Error(`Failed to sync: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  /**
   * Filter units by status - useful for excluding available_hidden from map display
   */
  getVisibleUnits(units: UnitSummary[]): UnitSummary[] {
    return units.filter((unit) => unit.status !== 'available_hidden')
  }

  /**
   * Filter units by location (caserne)
   */
  getUnitsByLocation(units: UnitSummary[], locationId: string): UnitSummary[] {
    return units.filter((unit) => unit.location_id === locationId)
  }

  async getAdminHealth(token?: string): Promise<DetailedHealthResponse> {
    const response = await fetch(`${this.API_BASE_URL}/admin/health`, {
      headers: this.buildHeaders(token),
    })
    if (!response.ok) {
      throw new Error(`Failed to fetch health: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }
}

export const fastPinPonService = new FastPinPonService()
