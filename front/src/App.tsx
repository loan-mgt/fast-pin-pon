import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'

import { Button } from './components/ui/button'
import { Card } from './components/ui/card'

type EventSummary = {
  id: string
  title: string
  reported_at?: string
  status?: string
  severity?: number
  location?: {
    latitude?: number
    longitude?: number
  }
}

const API_URL = 'https://api.fast-pin-pon.4loop.org/v1/events?limit=25'
const DEFAULT_CENTER: [number, number] = [-98.5795, 39.8283]

const formatTimestamp = (value?: string) =>
  value
    ? new Date(value).toLocaleString('en-US', {
        hour: 'numeric',
        minute: '2-digit',
        month: 'short',
        day: 'numeric',
      })
    : 'Unknown'

const severityLabel = (severity?: number) => {
  if (!severity) return 'Pending'
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
      if (!response.ok) {
        throw new Error('Failed to fetch events')
      }
      const data: EventSummary[] = await response.json()
      setEvents(data)
      setLastUpdated(new Date().toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' }))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refreshEvents()
  }, [refreshEvents])

  const sortedEvents = useMemo(() => {
    return [...events].sort((a, b) => {
      const aTime = a.reported_at ? new Date(a.reported_at).getTime() : 0
      const bTime = b.reported_at ? new Date(b.reported_at).getTime() : 0
      return bTime - aTime
    })
  }, [events])

  const mapContainerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<maplibregl.Map | null>(null)
  const markersRef = useRef<maplibregl.Marker[]>([])

  useEffect(() => {
    if (!mapContainerRef.current) return

    const map = new maplibregl.Map({
      container: mapContainerRef.current,
      style: 'https://demotiles.maplibre.org/style.json',
      center: DEFAULT_CENTER,
      zoom: 3.5,
    })

    mapRef.current = map
    map.on('load', () => map.resize())
    const handleResize = () => map.resize()
    window.addEventListener('resize', handleResize)

    return () => {
      markersRef.current.forEach((marker) => marker.remove())
      map.remove()
      window.removeEventListener('resize', handleResize)
    }
  }, [])

  useEffect(() => {
    const map = mapRef.current
    if (!map) return

    markersRef.current.forEach((marker) => marker.remove())
    markersRef.current = []

    const locations = sortedEvents
      .map((event) => ({
        event,
        lng: event.location?.longitude,
        lat: event.location?.latitude,
      }))
      .filter(
        (item): item is { event: EventSummary; lng: number; lat: number } =>
          typeof item.lng === 'number' && typeof item.lat === 'number',
      )

    if (locations.length) {
      const avgLng = locations.reduce((sum, loc) => sum + loc.lng, 0) / locations.length
      const avgLat = locations.reduce((sum, loc) => sum + loc.lat, 0) / locations.length
      map.flyTo({ center: [avgLng, avgLat], zoom: 6, duration: 1200 })
    }

    locations.forEach(({ event, lng, lat }) => {
      const marker = new maplibregl.Marker({ color: '#22d3ee' })
        .setLngLat([lng, lat])
        .setPopup(
          new maplibregl.Popup({ offset: 24 }).setHTML(
            `<strong>${event.title}</strong><br/><small>${formatTimestamp(event.reported_at)}</small>`,
          ),
        )
        .addTo(map)

      markersRef.current.push(marker)
    })
  }, [sortedEvents])

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
            <div className="text-slate-400 text-xs">Updated {lastUpdated}</div>
          </div>

          {error ? (
            <p className="text-rose-300 text-sm">{error}</p>
          ) : sortedEvents.length === 0 ? (
            <p className="text-slate-300 text-sm">Awaiting events…</p>
          ) : (
            <div className="space-y-3">
              {sortedEvents.map((event) => (
                <article key={event.id} className="space-y-2 bg-slate-900/80 p-3 border border-slate-800/60 rounded-2xl">
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
          )}

          <Button variant="solid" className="w-full" onClick={refreshEvents} disabled={loading}>
            {loading ? 'Refreshing…' : 'Refresh list'}
          </Button>
        </Card>
      </main>
    </div>
  )
}
