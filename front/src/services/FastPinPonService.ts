import type { EventSummary, UnitSummary } from '../types'
import type { CreateEventRequest, EventType } from '../types/eventTypes'

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
  async getEvents(limit = 25, token?: string): Promise<EventSummary[]> {
    const response = await fetch(`${this.API_BASE_URL}/events?limit=${limit}`, {
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
}

// Export a singleton instance (like Angular's 'providedIn: root')
export const fastPinPonService = new FastPinPonService()
