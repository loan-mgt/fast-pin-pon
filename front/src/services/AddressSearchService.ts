/**
 * Service for geocoding addresses using the French government API (api-adresse.data.gouv.fr).
 * This API is free, requires no API key, and provides accurate French address data.
 */

const API_BASE_URL = 'https://api-adresse.data.gouv.fr'

/**
 * Map bounds matching the MapContainer maxBounds configuration.
 * Only addresses within these bounds will be returned.
 */
const MAP_BOUNDS = {
    southwest: { lng: 4.290161, lat: 45.375302 },
    northeast: { lng: 5.386047, lat: 46.0999 },
}

/** Represents a single address suggestion from the API */
export type AddressSuggestion = {
    /** Full formatted address label */
    label: string
    /** Latitude coordinate */
    latitude: number
    /** Longitude coordinate */
    longitude: number
    /** Address score/confidence (0-1) */
    score: number
    /** City name */
    city: string
    /** Postal code */
    postcode: string
    /** Street name */
    street: string
    /** House number */
    housenumber: string
}

/** Raw API response structure */
type ApiResponse = {
    features: Array<{
        properties: {
            label: string
            score: number
            city: string
            postcode: string
            street: string
            housenumber: string
        }
        geometry: {
            coordinates: [number, number] // [longitude, latitude]
        }
    }>
}

/**
 * Check if coordinates are within the map bounds
 */
function isWithinBounds(lng: number, lat: number): boolean {
    return (
        lng >= MAP_BOUNDS.southwest.lng &&
        lng <= MAP_BOUNDS.northeast.lng &&
        lat >= MAP_BOUNDS.southwest.lat &&
        lat <= MAP_BOUNDS.northeast.lat
    )
}

/**
 * Transform API response to our internal format, filtering out-of-bounds results
 */
function transformResponse(response: ApiResponse): AddressSuggestion[] {
    return response.features
        .map((feature) => ({
            label: feature.properties.label,
            latitude: feature.geometry.coordinates[1],
            longitude: feature.geometry.coordinates[0],
            score: feature.properties.score,
            city: feature.properties.city ?? '',
            postcode: feature.properties.postcode ?? '',
            street: feature.properties.street ?? '',
            housenumber: feature.properties.housenumber ?? '',
        }))
        .filter((suggestion) => isWithinBounds(suggestion.longitude, suggestion.latitude))
}

class AddressSearchService {
    private abortController: AbortController | null = null

    /**
     * Search for addresses matching the given query.
     * Automatically cancels any pending request when a new one is made.
     * 
     * @param query - The address search query (minimum 3 characters)
     * @param limit - Maximum number of results (default: 5)
     * @returns Promise resolving to an array of address suggestions
     */
    async searchAddress(query: string, limit = 5): Promise<AddressSuggestion[]> {
        // Cancel any pending request
        if (this.abortController) {
            this.abortController.abort()
        }

        // Skip if query is too short
        if (query.trim().length < 3) {
            return []
        }

        this.abortController = new AbortController()

        try {
            const params = new URLSearchParams({
                q: query.trim(),
                limit: String(limit),
                // Restrict to Rhône department (69) for better relevance
                // Remove this if you want nationwide search
                lat: '45.7485',
                lon: '4.8467',
            })

            const response = await fetch(`${API_BASE_URL}/search/?${params.toString()}`, {
                signal: this.abortController.signal,
            })

            if (!response.ok) {
                throw new Error(`Address search failed: ${response.status}`)
            }

            const data: ApiResponse = await response.json()
            return transformResponse(data)
        } catch (error) {
            // Don't throw on abort - it's intentional
            if (error instanceof Error && error.name === 'AbortError') {
                return []
            }
            throw error
        } finally {
            this.abortController = null
        }
    }

    /**
     * Cancel any pending search request
     */
    cancel(): void {
        if (this.abortController) {
            this.abortController.abort()
            this.abortController = null
        }
    }
}

export const addressSearchService = new AddressSearchService()
