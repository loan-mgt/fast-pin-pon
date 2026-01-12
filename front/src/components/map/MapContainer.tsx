import type { JSX, MutableRefObject } from 'react'
import { useEffect, useRef } from 'react'
import maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import type { EventSummary, UnitSummary, Building } from '../../types'
import { STATUS_COLORS } from '../../utils/format'
import {
    createEventMarkerElement as createEventMarkerWithIcon,
    createUnitMarkerElement,
    createBuildingMarkerElement,
} from './mapIcons'

const DEFAULT_CENTER: [number, number] = [4.8467, 45.7485]
const DEFAULT_ZOOM = 11
const MAP_STATE_KEY = 'mapState'

type StoredMapState = {
    center: [number, number]
    zoom: number
}

type EventLocation = {
    event: EventSummary
    lng: number
    lat: number
}

function loadStoredMapState(): StoredMapState | null {
    const savedState = localStorage.getItem(MAP_STATE_KEY)
    if (savedState === null) return null

    try {
        const parsed = JSON.parse(savedState) as StoredMapState
        if (Array.isArray(parsed.center) && parsed.center.length === 2 && typeof parsed.zoom === 'number') {
            return parsed
        }
    } catch {
        return null
    }

    return null
}

function clearMarkers(markersRef: MutableRefObject<maplibregl.Marker[]>): void {
    for (const marker of markersRef.current) marker.remove()
    markersRef.current = []
}

function getEventLocations(events: EventSummary[]): EventLocation[] {
    return events
        .map((event) => ({
            event,
            lng: event.location?.longitude,
            lat: event.location?.latitude,
        }))
        .filter((item): item is EventLocation => Boolean(item.lng) && Boolean(item.lat) && item.lng !== 0 && item.lat !== 0)
}

function getInitialView(eventLocations: EventLocation[], savedState: StoredMapState | null): StoredMapState {
    if (savedState) return savedState

    const avgLng = eventLocations.reduce((sum, loc) => sum + loc.lng, 0) / eventLocations.length
    const avgLat = eventLocations.reduce((sum, loc) => sum + loc.lat, 0) / eventLocations.length

    return { center: [avgLng, avgLat], zoom: DEFAULT_ZOOM }
}

function flyToInitialLocation(
    map: maplibregl.Map,
    eventLocations: EventLocation[],
    isFirstLoad: MutableRefObject<boolean>,
): void {
    if (isFirstLoad.current === false) return
    if (eventLocations.length === 0) return

    const savedState = loadStoredMapState()
    const { center, zoom } = getInitialView(eventLocations, savedState)

    map.flyTo({ center, zoom, duration: 1200 })
    isFirstLoad.current = false
}

type UnitLocation = {
    unit: UnitSummary
    longitude: number
    latitude: number
}

function buildUnitEventMaps(events: EventSummary[]): {
    unitToEvent: Map<string, string>
    eventById: Map<string, EventSummary>
} {
    const unitToEvent = new Map<string, string>()
    const eventById = new Map<string, EventSummary>()
    for (const event of events) {
        eventById.set(event.id, event)
        for (const unit of event.assigned_units ?? []) {
            unitToEvent.set(unit.id, event.id)
        }
    }
    return { unitToEvent, eventById }
}

function getUnitLocations(units: UnitSummary[]): UnitLocation[] {
    return units
        // Exclude units with status 'available_hidden' from map display
        .filter((unit) => unit.status !== 'available_hidden')
        .filter((unit) => unit.location?.longitude && unit.location?.latitude)
        .filter((unit) => unit.location.longitude !== 0 && unit.location.latitude !== 0)
        .map((unit) => ({
            unit,
            longitude: unit.location.longitude,
            latitude: unit.location.latitude,
        }))
}

function buildConnectionLines(
    selectedEventId: string | null | undefined,
    eventById: Map<string, EventSummary>,
    unitLocations: UnitLocation[],
    unitToEvent: Map<string, string>,
): GeoJSON.Feature<GeoJSON.LineString>[] {
    if (selectedEventId === null || selectedEventId === undefined) return []

    const selectedEvent = eventById.get(selectedEventId)
    const hasLon = selectedEvent?.location?.longitude !== undefined
    const hasLat = selectedEvent?.location?.latitude !== undefined
    if (!hasLon || !hasLat) return []

    return unitLocations
        .filter((loc) => unitToEvent.get(loc.unit.id) === selectedEventId)
        // Exclude on_site units - they are displayed under the event icon
        .filter((loc) => loc.unit.status.toLowerCase() !== 'on_site')
        .map((loc) => ({
            type: 'Feature' as const,
            properties: { unitId: loc.unit.id, eventId: selectedEventId },
            geometry: {
                type: 'LineString' as const,
                coordinates: [
                    [loc.longitude, loc.latitude],
                    [selectedEvent.location.longitude, selectedEvent.location.latitude],
                ],
            },
        }))
}

function isGeoJSONSource(source: unknown): source is maplibregl.GeoJSONSource {
    return Boolean(
        source &&
        typeof (source as maplibregl.GeoJSONSource).setData === 'function' &&
        (source as maplibregl.GeoJSONSource).type === 'geojson',
    )
}

function addConnectionLayers(
    map: maplibregl.Map,
    sourceId: string,
    lineLayerId: string,
    arrowLayerId: string,
): void {
    map.addLayer({
        id: lineLayerId,
        type: 'line',
        source: sourceId,
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: {
            'line-color': '#60a5fa',
            'line-width': 2,
            'line-opacity': 0.7,
            'line-dasharray': [2, 2],
        },
    })

    map.addLayer({
        id: arrowLayerId,
        type: 'symbol',
        source: sourceId,
        layout: {
            'symbol-placement': 'line',
            'symbol-spacing': 100,
            'text-field': '▶',
            'text-size': 12,
            'text-rotation-alignment': 'map',
            'text-keep-upright': false,
        },
        paint: {
            'text-color': '#60a5fa',
            'text-opacity': 0.9,
        },
    })
}

function addEventMarkers(
    map: maplibregl.Map,
    eventLocations: EventLocation[],
    selectedEventId: string | null | undefined,
    onEventSelect: ((eventId: string) => void) | undefined,
    eventMarkersRef: MutableRefObject<maplibregl.Marker[]>,
    units: UnitSummary[],
): void {
    // Build a map of units by ID for quick lookup (to get current status)
    const unitsById = new Map<string, UnitSummary>()
    for (const unit of units) {
        unitsById.set(unit.id, unit)
    }

    for (const location of eventLocations) {
        const isSelected = selectedEventId === location.event.id

        // Get on-site units for this event
        // Check status from the global units list (most up-to-date status)
        // Fallback to assigned_unit's status if not found in global list
        const onSiteUnits: UnitSummary[] = []
        for (const assignedUnit of location.event.assigned_units ?? []) {
            // First try to find the unit in the global units list (most up-to-date)
            const globalUnit = unitsById.get(assignedUnit.id)
            // Use global unit if found, otherwise use assigned unit data
            const currentUnit = globalUnit ?? assignedUnit
            const status = (currentUnit.status ?? '').toLowerCase().trim()

            // Debug: log unit matching
            if (!globalUnit) {
                console.warn(`[MapContainer] Unit ${assignedUnit.id} (${assignedUnit.call_sign}) not found in global units list`)
            }

            if (status === 'on_site') {
                // Make sure we have a valid unit_type_code
                if (currentUnit.unit_type_code) {
                    onSiteUnits.push(currentUnit)
                } else {
                    console.warn(`[MapContainer] Unit ${currentUnit.id} has no unit_type_code`)
                }
            }
        }

        const el = createEventMarkerWithIcon(
            location.event.event_type_code,
            isSelected,
            location.event.severity,
            onSiteUnits,
            !location.event.auto_simulated, // isManual = inverse of auto_simulated
        )

        // Anchor at center so connection lines point to the event icon center
        const marker = new maplibregl.Marker({ element: el, anchor: 'center' })
            .setLngLat([location.lng, location.lat])
            .addTo(map)

        el.addEventListener('click', () => onEventSelect?.(location.event.id))
        eventMarkersRef.current.push(marker)
    }
}

interface MapContainerProps {
    events: EventSummary[]
    units: UnitSummary[]
    buildings?: Building[]
    onEventSelect?: (eventId: string) => void
    onBuildingSelect?: (buildingId: string) => void
    selectedEventId?: string | null
    onCreateAtLocation?: (coords: { latitude: number; longitude: number }) => void
    onMapReady?: (flyTo: (lng: number, lat: number, zoom?: number) => void) => void
}

export function MapContainer({
    events,
    units,
    onEventSelect,
    onBuildingSelect,
    selectedEventId,
    onCreateAtLocation,
    onMapReady,
    buildings = [],
}: Readonly<MapContainerProps>): JSX.Element {
    const mapContainerRef = useRef<HTMLDivElement>(null)
    const mapRef = useRef<maplibregl.Map | null>(null)
    const eventMarkersRef = useRef<maplibregl.Marker[]>([])
    const unitMarkersRef = useRef<maplibregl.Marker[]>([])
    const buildingMarkersRef = useRef<maplibregl.Marker[]>([])
    const isFirstLoad = useRef(true)
    const connectionLayerId = 'unit-event-connections'
    const connectionSourceId = 'unit-event-connections-source'
    const arrowLayerId = 'unit-event-arrows'

    useEffect(() => {
        if (!mapContainerRef.current) return

        // Load saved state from localStorage if available
        const savedState = localStorage.getItem(MAP_STATE_KEY)
        const initialState = savedState
            ? JSON.parse(savedState)
            : { center: DEFAULT_CENTER, zoom: DEFAULT_ZOOM }

        const map = new maplibregl.Map({
            container: mapContainerRef.current,
            style: '/style.json',
            center: initialState.center,
            zoom: initialState.zoom,
            minZoom: 10,
            maxZoom: 19,
            maxBounds: [
                [4.290161, 45.375302],   // southwest
                [5.386047, 46.0999]    // northeast
            ]
        })

        mapRef.current = map
        map.on('load', () => {
            map.resize()
            // Expose flyTo function to parent component
            if (onMapReady) {
                onMapReady((lng: number, lat: number, zoom?: number) => {
                    const targetZoom = zoom ?? map.getZoom()
                    const duration = zoom !== undefined ? 1600 : 1000
                    map.easeTo({ center: [lng, lat], zoom: targetZoom, duration, essential: true })
                })
            }
        })

        if (onCreateAtLocation) {
            map.on('contextmenu', (e) => {
                onCreateAtLocation({ latitude: e.lngLat.lat, longitude: e.lngLat.lng })
            })
        }

        // Persist map center & zoom when moved
        map.on('moveend', () => {
            const center = map.getCenter()
            const zoom = map.getZoom()
            localStorage.setItem(
                MAP_STATE_KEY,
                JSON.stringify({ center: [center.lng, center.lat], zoom }),
            )
        })

        const handleResize = () => map.resize()
        window.addEventListener('resize', handleResize)

        return () => {
            // Cleanup markers
            for (const marker of eventMarkersRef.current) marker.remove()
            for (const marker of unitMarkersRef.current) marker.remove()
            for (const marker of buildingMarkersRef.current) marker.remove()

            map.remove()
            window.removeEventListener('resize', handleResize)
        }
    }, [])

    // Effect for event markers
    useEffect(() => {
        const map = mapRef.current
        if (map === null) return

        clearMarkers(eventMarkersRef)

        const eventLocations = getEventLocations(events)
        if (eventLocations.length === 0) return

        flyToInitialLocation(map, eventLocations, isFirstLoad)
        addEventMarkers(map, eventLocations, selectedEventId, onEventSelect, eventMarkersRef, units)
    }, [events, onEventSelect, selectedEventId, units])

    // Effect for building markers (casernes)
    useEffect(() => {
        const map = mapRef.current
        if (map === null) return

        // Clear existing
        for (const marker of buildingMarkersRef.current) marker.remove()
        buildingMarkersRef.current = []

        if (!buildings || buildings.length === 0) return

        for (const b of buildings) {
            if (!b.location?.longitude || !b.location?.latitude) continue
            const el = createBuildingMarkerElement()

            // Add click handler to navigate to dashboard with station filter
            el.addEventListener('click', () => onBuildingSelect?.(b.id))
            el.style.cursor = 'pointer'

            const marker = new maplibregl.Marker({ element: el, anchor: 'center' })
                .setLngLat([b.location.longitude, b.location.latitude])
                .setPopup(new maplibregl.Popup({ offset: 18 }).setHTML(
                    `<div style="font-family: system-ui, sans-serif;">
                        <div style="font-weight: 600; font-size: 14px; margin-bottom: 4px;">${b.name}</div>
                        <div style="font-size: 12px; color: #666;">Cliquer pour voir les unités</div>
                    </div>`
                ))
                .addTo(map)
            buildingMarkersRef.current.push(marker)
        }
    }, [buildings, onBuildingSelect])

    // Effect for unit markers and connection lines
    useEffect(() => {
        const map = mapRef.current
        if (map === null) return

        // Wait for style to be loaded before adding sources/layers
        const updateUnitsAndConnections = () => {
            // Remove old unit markers
            for (const marker of unitMarkersRef.current) marker.remove()
            unitMarkersRef.current = []

            const { unitToEvent, eventById } = buildUnitEventMaps(events)
            const unitLocations = getUnitLocations(units)

            // Build set of unit IDs that are on_site and assigned to an event (displayed under event marker)
            const unitsDisplayedUnderEvent = new Set<string>()
            for (const event of events) {
                for (const assignedUnit of event.assigned_units ?? []) {
                    // Find the actual unit to check its current status
                    const fullUnit = units.find((u) => u.id === assignedUnit.id)
                    if (fullUnit && fullUnit.status.toLowerCase() === 'on_site') {
                        unitsDisplayedUnderEvent.add(assignedUnit.id)
                    }
                }
            }

            const connectionLines = buildConnectionLines(selectedEventId, eventById, unitLocations, unitToEvent)

            // Update or create the connection lines source and layer
            const geojsonData: GeoJSON.FeatureCollection<GeoJSON.LineString> = {
                type: 'FeatureCollection',
                features: connectionLines,
            }

            const source = map.getSource(connectionSourceId)
            if (isGeoJSONSource(source)) {
                source.setData(geojsonData)
            } else {
                map.addSource(connectionSourceId, { type: 'geojson', data: geojsonData })
                addConnectionLayers(map, connectionSourceId, connectionLayerId, arrowLayerId)
            }

            // Add unit markers with custom SVG icons based on unit type
            // Skip units that are on_site and assigned to an event (they're shown under the event marker)
            for (const loc of unitLocations) {
                if (unitsDisplayedUnderEvent.has(loc.unit.id)) {
                    continue
                }

                const color = STATUS_COLORS[loc.unit.status] ?? STATUS_COLORS.offline
                const eventIdForUnit = unitToEvent.get(loc.unit.id)

                const wrapper = createUnitMarkerElement(loc.unit.unit_type_code, loc.unit.status)
                wrapper.addEventListener('click', () => {
                    if (eventIdForUnit) onEventSelect?.(eventIdForUnit)
                })

                const marker = new maplibregl.Marker({ element: wrapper, anchor: 'center' })
                    .setLngLat([loc.longitude, loc.latitude])
                    .setPopup(
                        new maplibregl.Popup({ offset: 18, className: 'unit-popup' }).setHTML(
                            `<div style="font-family: system-ui, sans-serif;">
                                <div style="font-weight: 600; font-size: 14px; margin-bottom: 4px;">${loc.unit.call_sign}</div>
                                <div style="font-size: 12px; color: #666; margin-bottom: 6px;">${loc.unit.unit_type_code}</div>
                                <div style="display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 11px; font-weight: 500; color: white; background-color: ${color};">${loc.unit.status.replace('_', ' ')}</div>
                            </div>`,
                        ),
                    )
                    .addTo(map)

                unitMarkersRef.current.push(marker)
            }
        }

        // If style is already loaded, run immediately; otherwise wait for 'load' event
        if (map.isStyleLoaded()) {
            updateUnitsAndConnections()
        } else {
            map.once('load', updateUnitsAndConnections)
        }
    }, [events, onEventSelect, selectedEventId, units])

    return (
        <div
            ref={mapContainerRef}
            className="z-0 flex-1 min-h-0"
            aria-label="Event locations map"
        />
    )
}
