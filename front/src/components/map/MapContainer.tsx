import type { JSX } from 'react'
import { useEffect, useRef } from 'react'
import maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import type { EventSummary, UnitSummary } from '../../types'
import { STATUS_COLORS } from '../../utils/format'

const DEFAULT_CENTER: [number, number] = [4.8467, 45.7485]
const DEFAULT_ZOOM = 11
const MAP_STATE_KEY = 'mapState'

interface MapContainerProps {
    events: EventSummary[]
    units: UnitSummary[]
    onEventSelect?: (eventId: string) => void
    selectedEventId?: string | null
}

export function MapContainer({ events, units, onEventSelect, selectedEventId }: Readonly<MapContainerProps>): JSX.Element {
    const mapContainerRef = useRef<HTMLDivElement>(null)
    const mapRef = useRef<maplibregl.Map | null>(null)
    const eventMarkersRef = useRef<maplibregl.Marker[]>([])
    const unitMarkersRef = useRef<maplibregl.Marker[]>([])
    const isFirstLoad = useRef(true)

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
        map.on('load', () => map.resize())

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

            map.remove()
            window.removeEventListener('resize', handleResize)
        }
    }, [])

    // Effect for event markers
    useEffect(() => {
        const map = mapRef.current
        if (!map) return

        // Remove old event markers
        for (const marker of eventMarkersRef.current) marker.remove()
        eventMarkersRef.current = []

        // Prepare event marker locations
        const eventLocations = events
            .map((event) => ({
                event,
                lng: event.location?.longitude,
                lat: event.location?.latitude,
            }))
            .filter((item) => item.lng !== 0 && item.lat !== 0)

        // Only fly to location on first load
        if (isFirstLoad.current && eventLocations.length) {
            const savedState = localStorage.getItem(MAP_STATE_KEY)
            const center = savedState
                ? JSON.parse(savedState).center
                : [
                    eventLocations.reduce((sum, loc) => sum + loc.lng, 0) / eventLocations.length,
                    eventLocations.reduce((sum, loc) => sum + loc.lat, 0) / eventLocations.length,
                ]
            const zoom = savedState ? JSON.parse(savedState).zoom : DEFAULT_ZOOM

            map.flyTo({ center, zoom, duration: 1200 })
            isFirstLoad.current = false
        }

        // Add event markers (pulsing red circles for incidents)
        for (const location of eventLocations) {
            const { event, lng, lat } = location

            // Create custom pulsing element for events
                        const isSelected = selectedEventId === event.id

                        const el = document.createElement('div')
                        el.className = 'event-marker'
                        el.style.cursor = 'pointer'
                        el.innerHTML = `
                <div style="
                    position: relative;
                    width: ${isSelected ? '20px' : '16px'};
                    height: ${isSelected ? '20px' : '16px'};
                ">
                    <div style="
                        position: absolute;
                        width: 100%;
                        height: 100%;
                        background-color: ${isSelected ? '#22d3ee' : '#ef4444'};
                        border: ${isSelected ? '3px solid #0ea5e9' : '2px solid white'};
                        border-radius: 50%;
                        box-shadow: ${isSelected ? '0 0 14px rgba(59,130,246,0.9)' : '0 2px 8px rgba(0,0,0,0.6)'};
                    "></div>
                    <div style="
                        position: absolute;
                        width: 100%;
                        height: 100%;
                        background-color: ${isSelected ? '#3b82f6' : '#ef4444'};
                        border-radius: 50%;
                        animation: pulse 2s ease-out infinite;
                        opacity: 0.6;
                    "></div>
                </div>
            `

            const marker = new maplibregl.Marker({ element: el })
                .setLngLat([lng, lat])
                .addTo(map)

            el.addEventListener('click', () => {
                onEventSelect?.(event.id)
            })

            eventMarkersRef.current.push(marker)
        }
    }, [events, onEventSelect, selectedEventId])

    // Effect for unit markers
    useEffect(() => {
        const map = mapRef.current
        if (!map) return

        // Remove old unit markers
        for (const marker of unitMarkersRef.current) marker.remove()
        unitMarkersRef.current = []

        // Prepare unit marker locations
        const unitLocations = units
            .filter((unit) => unit.location?.longitude && unit.location?.latitude)
            .filter((unit) => unit.location.longitude !== 0 && unit.location.latitude !== 0)

        // Add unit markers with custom HTML elements (squares instead of circles)
        for (const unit of unitLocations) {
            const color = STATUS_COLORS[unit.status] ?? STATUS_COLORS.offline

            // Create wrapper for proper positioning
            const wrapper = document.createElement('div')
            wrapper.style.cssText = 'width: 24px; height: 24px; cursor: pointer;'

            // Create custom HTML element for unit marker (square/diamond shape)
            const el = document.createElement('div')
            el.style.cssText = `
        width: 100%;
        height: 100%;
        background-color: ${color};
        border: 2px solid white;
        border-radius: 4px;
        transform: rotate(45deg);
        box-shadow: 0 2px 6px rgba(0,0,0,0.4);
        transition: transform 0.2s ease;
      `
            wrapper.appendChild(el)

            wrapper.addEventListener('mouseenter', () => {
                el.style.transform = 'rotate(45deg) scale(1.2)'
            })
            wrapper.addEventListener('mouseleave', () => {
                el.style.transform = 'rotate(45deg)'
            })

            const marker = new maplibregl.Marker({ element: wrapper, anchor: 'center' })
                .setLngLat([unit.location.longitude, unit.location.latitude])
                .setPopup(
                    new maplibregl.Popup({ offset: 18, className: 'unit-popup' }).setHTML(
                        `<div style="font-family: system-ui, sans-serif;">
               <div style="font-weight: 600; font-size: 14px; margin-bottom: 4px;">${unit.call_sign}</div>
               <div style="font-size: 12px; color: #666; margin-bottom: 6px;">${unit.unit_type_code} • ${unit.home_base}</div>
               <div style="display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 11px; font-weight: 500; color: white; background-color: ${color};">${unit.status.replace('_', ' ')}</div>
             </div>`,
                    ),
                )
                .addTo(map)

            unitMarkersRef.current.push(marker)
        }
    }, [units])

    return (
        <div
            ref={mapContainerRef}
            className="z-0 flex-1 min-h-0"
            aria-label="Event locations map"
        />
    )
}
