import type { JSX } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'

import { Button } from './components/ui/button'
import { Card } from './components/ui/card'

type GeoPoint = {
  latitude: number
  longitude: number
}

type EventSummary = {
  id: string
  title: string
  description?: string
  report_source?: string
  address?: string
  location: GeoPoint
  severity: number
  status: string
  event_type_code: string
  event_type_name: string
  reported_at: string
  updated_at: string
  closed_at?: string
}

const API_URL = 'https://api.fast-pin-pon.4loop.org/v1/events?limit=25'
const DEFAULT_CENTER: [number, number] = [4.8467, 45.7485]
const DEFAULT_ZOOM = 11
const MAP_STATE_KEY = 'mapState'

const formatTimestamp = (value?: string): string =>
  value
    ? new Date(value).toLocaleString('en-US', {
        hour: 'numeric',
        minute: '2-digit',
        month: 'short',
        day: 'numeric',
      })
    : 'Unknown'

const severityLabel = (severity?: number): string => {
  if (severity === undefined) return 'Pending'
  if (severity <= 2) return 'Low'
  if (severity === 3) return 'Medium'
  return 'High'
}

export function App() {
  const [events, setEvents] = useState<EventSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<string>('—')

  const refreshEvents = useCallback(async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await fetch(API_URL)
      if (!response.ok) throw new Error('Failed to fetch events')
      const data: EventSummary[] = await response.json()
      setEvents(data)
      setLastUpdated(
        new Date().toLocaleTimeString('en-US', {
          hour: 'numeric',
          minute: '2-digit',
        }),
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refreshEvents()
  }, [refreshEvents])

  const sortedEvents = useMemo(() => {
    return [...events].sort((a, b) => {
      const aTime = new Date(a.reported_at).getTime()
      const bTime = new Date(b.reported_at).getTime()
      return bTime - aTime
    })
  }, [events])

  const mapContainerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<maplibregl.Map | null>(null)
  const markersRef = useRef<maplibregl.Marker[]>([])

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
      maxZoom: 19,
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
      for (const marker of markersRef.current) {
        marker.remove()
      }
      map.remove()
      window.removeEventListener('resize', handleResize)
    }
  }, [])

  useEffect(() => {
    const map = mapRef.current
    if (!map) return

    // Remove old markers
    for (const marker of markersRef.current) marker.remove()
    markersRef.current = []

    // Prepare marker locations
    const locations = sortedEvents
      .map((event) => ({
        event,
        lng: event.location?.longitude,
        lat: event.location?.latitude,
      }))
      .filter((item) => item.lng !== 0 && item.lat !== 0)

    if (locations.length) {
      // Use saved map state if available, otherwise default to zoom 11
      const savedState = localStorage.getItem(MAP_STATE_KEY)
      const center = savedState
        ? JSON.parse(savedState).center
        : [
            locations.reduce((sum, loc) => sum + loc.lng, 0) / locations.length,
            locations.reduce((sum, loc) => sum + loc.lat, 0) / locations.length,
          ]
      const zoom = savedState ? JSON.parse(savedState).zoom : DEFAULT_ZOOM

      map.flyTo({ center, zoom, duration: 1200 })
    }

    // Add new markers
    for (const location of locations) {
      const { event, lng, lat } = location
      const marker = new maplibregl.Marker({ color: '#22d3ee' })
        .setLngLat([lng, lat])
        .setPopup(
          new maplibregl.Popup({ offset: 24 }).setHTML(
            `<strong>${event.title}</strong><br/><small>${formatTimestamp(
              event.reported_at,
            )}</small>`,
          ),
        )
        .addTo(map)

      markersRef.current.push(marker)
    }
  }, [sortedEvents])

  let eventsDisplay: JSX.Element

  if (error) {
    eventsDisplay = <p className="text-rose-300 text-sm">{error}</p>
  } else if (sortedEvents.length === 0) {
    eventsDisplay = <p className="text-slate-300 text-sm">Awaiting events…</p>
  } else {
    eventsDisplay = (
      <div className="space-y-3">
        {sortedEvents.map((event) => (
          <article
            key={event.id}
            className="space-y-2 bg-slate-900/80 p-3 border border-slate-800/60 rounded-2xl"
          >
            <div className="flex justify-between items-center gap-2">
              <h3 className="font-semibold text-white text-sm">{event.title}</h3>
              <span className="text-[0.55rem] text-cyan-300/90 uppercase tracking-[0.4em]">
                {severityLabel(event.severity)}
              </span>
            </div>
            <p className="text-slate-400 text-xs">{event.status ?? 'Status unknown'}</p>
            <div className="flex flex-wrap items-center gap-2 text-[0.65rem] text-slate-400">
              <span>{formatTimestamp(event.reported_at)}</span>
              {event.location?.latitude && event.location?.longitude && (
                <span>
                  {event.location.latitude.toFixed(3)}, {event.location.longitude.toFixed(3)}
                </span>
              )}
            </div>
          </article>
        ))}
      </div>
    )
  }

  return (
    <div className="flex flex-col bg-slate-950 min-h-screen text-slate-100">
      <nav className="bg-slate-950/90 border-slate-900/70 border-b">
        <div className="flex justify-between items-center gap-4 mx-auto px-6 py-4 max-w-6xl">
          <div>
            <p className="text-cyan-300/70 text-xs uppercase tracking-[0.35em]">Fast Pin Pon</p>
            <p className="font-semibold text-white text-lg">Live events</p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Button variant="ghost">Docs</Button>
            <Button variant="ghost">Status</Button>
            <Button variant="outline" onClick={refreshEvents} disabled={loading}>
              {loading ? 'Refreshing…' : 'Refresh events'}
            </Button>
          </div>
        </div>
      </nav>

      <main className="relative flex flex-1 min-h-[calc(100vh-72px)]">
        <div
          ref={mapContainerRef}
          className="z-0 flex-1 min-h-0"
          aria-label="Event locations map"
        />

        <Card className="top-4 right-4 z-10 absolute space-y-5 shadow-2xl shadow-slate-950/60 p-4 w-full max-w-sm max-h-[70vh] overflow-auto">
          <div className="flex justify-between items-center">
            <div>
              <p className="text-slate-400 text-xs uppercase tracking-[0.35em]">Live events</p>
              <p className="font-semibold text-white text-lg">Latest incidents</p>
            </div>
            <div className="flex items-center gap-3">
              <div className="text-slate-400 text-xs text-right">
                <span className="block opacity-70 text-[10px] uppercase tracking-wider">Updated</span>
                {lastUpdated}
              </div>
              <button
                onClick={refreshEvents}
                disabled={loading}
                className="hover:bg-slate-800 disabled:opacity-50 p-2 rounded-full text-slate-400 hover:text-white transition-colors"
                aria-label="Refresh events"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className={loading ? 'animate-spin' : ''}
                >
                  <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" />
                  <path d="M21 3v5h-5" />
                  <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" />
                  <path d="M8 16H3v5" />
                </svg>
              </button>
            </div>
          </div>

          {eventsDisplay}
        </Card>
      </main>
    </div>
  )
}
