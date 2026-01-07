import type { EventSummary, UnitSummary } from '../types'
import type { CreateEventRequest, EventType } from '../types/eventTypes'

type InterventionStatus = 'created' | 'on_site' | 'completed' | 'cancelled'
type UnitStatus = 'available' | 'under_way' | 'on_site' | 'unavailable' | 'offline'

/**
 * Service to handle data fetching from the Fast Pin Pon API.
 */
class FastPinPonService {
  private readonly API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'https://api.fast-pin-pon.4loop.org/v1'

  private buildHeaders(token?: string): HeadersInit {
    return token ? { Authorization: `Bearer ${token}` } : {}
  }

  /**
   * Fetches the latest events.
   * @param limit - Number of events to fetch (default 25)
   */
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

  /**
   * Fetches the latest units.
   */
  async getUnits(token?: string): Promise<UnitSummary[]> {
    const response = await fetch(`${this.API_BASE_URL}/units`, {
      headers: this.buildHeaders(token),
    })
    if (!response.ok) {
      throw new Error(`Failed to fetch units: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  // =========================
  // ADDED: fetch incident types
  // =========================
  async getEventTypes(): Promise<EventType[]> {
    const response = await fetch(`${this.API_BASE_URL}/event-types`)
    if (!response.ok) {
      throw new Error(`Failed to fetch event types: ${response.status} ${response.statusText}`)
    }
    return response.json()
  }

  // =========================
  // ADDED: create an incident
  // =========================
  async createEvent(payload: CreateEventRequest): Promise<void> {
    const response = await fetch(`${this.API_BASE_URL}/events`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })

    if (!response.ok) {
      const text = await response.text().catch(() => '')
      throw new Error(`Failed to create event: ${response.status} ${response.statusText} ${text}`)
    }
  }

  async assignMicrobit(unitId: string, microbitId: string, token?: string): Promise<void> {
    const response = await fetch(`${this.API_BASE_URL}/units/${unitId}/microbit`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        ...this.buildHeaders(token),
      },
      body: JSON.stringify({ microbit_id: microbitId }),
    })

    if (!response.ok) {
      const text = await response.text().catch(() => '')
      if (response.status === 409) {
        throw new Error('Ce microbit est déjà assigné à une autre unité.')
      }
      throw new Error(`Failed to assign microbit: ${response.status} ${response.statusText} ${text}`)
    }
  }

  async unassignMicrobit(unitId: string, token?: string): Promise<void> {
    const response = await fetch(`${this.API_BASE_URL}/units/${unitId}/microbit`, {
      method: 'DELETE',
      headers: {
        ...this.buildHeaders(token),
      },
    })

    if (!response.ok) {
      const text = await response.text().catch(() => '')
      throw new Error(`Failed to unassign microbit: ${response.status} ${response.statusText} ${text}`)
    }
  }

  async updateInterventionStatus(interventionId: string, status: InterventionStatus, token?: string): Promise<void> {
    const response = await fetch(`${this.API_BASE_URL}/interventions/${interventionId}/status`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        ...this.buildHeaders(token),
      },
      body: JSON.stringify({ status }),
    })

    if (!response.ok) {
      const text = await response.text().catch(() => '')
      throw new Error(`Failed to update intervention: ${response.status} ${response.statusText} ${text}`)
    }
  }

  async updateUnitStatus(unitId: string, status: UnitStatus, token?: string): Promise<void> {
    const response = await fetch(`${this.API_BASE_URL}/units/${unitId}/status`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        ...this.buildHeaders(token),
      },
      body: JSON.stringify({ status }),
    })

    if (!response.ok) {
      const text = await response.text().catch(() => '')
      throw new Error(`Failed to update unit: ${response.status} ${response.statusText} ${text}`)
    }
  }
}

// Export a singleton instance (like Angular's 'providedIn: root')
export const fastPinPonService = new FastPinPonService()
