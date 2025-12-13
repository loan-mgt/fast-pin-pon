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

type UnitSummary = {
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

const API_URL = 'https://api.fast-pin-pon.4loop.org/v1/events?limit=25'
const UNITS_API_URL = 'https://api.fast-pin-pon.4loop.org/v1/units'
const DEFAULT_CENTER: [number, number] = [4.8467, 45.7485]
const DEFAULT_ZOOM = 11
const MAP_STATE_KEY = 'mapState'
const REFRESH_INTERVAL_KEY = 'refreshInterval'
const REFRESH_OPTIONS = [5, 10, 20, 30] as const

const STATUS_COLORS: Record<string, string> = {
  available: '#22c55e',
  under_way: '#eab308',
  on_site: '#3b82f6',
  unavailable: '#ef4444',
  offline: '#6b7280',
}

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

const MIN_SPIN_DURATION = 500

export function App() {
  const [events, setEvents] = useState<EventSummary[]>([])
  const [units, setUnits] = useState<UnitSummary[]>([])
  const [isSpinning, setIsSpinning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<string>('—')
  const [isPanelCollapsed, setIsPanelCollapsed] = useState(false)
  const [panelPosition, setPanelPosition] = useState(() => ({
    x: globalThis.window === undefined ? 16 : globalThis.window.innerWidth - 400,
    y: 16
  }))
  const [isDragging, setIsDragging] = useState(false)
  const dragStartRef = useRef({ x: 0, y: 0, panelX: 0, panelY: 0 })
  const [refreshInterval, setRefreshInterval] = useState<number>(() => {
    const saved = localStorage.getItem(REFRESH_INTERVAL_KEY)
    return saved ? Number.parseInt(saved, 10) : 10
  })

  const refreshData = useCallback(async () => {
    setIsSpinning(true)
    setError(null)

    const spinStart = Date.now()

    try {
      const [eventsResponse, unitsResponse] = await Promise.all([
        fetch(API_URL),
        fetch(UNITS_API_URL),
      ])
      if (!eventsResponse.ok) throw new Error('Failed to fetch events')
      if (!unitsResponse.ok) throw new Error('Failed to fetch units')

      const eventsData: EventSummary[] = await eventsResponse.json()
      const unitsData: UnitSummary[] = await unitsResponse.json()

      setEvents(eventsData)
      setUnits(unitsData)
      setLastUpdated(
        new Date().toLocaleTimeString('en-US', {
          hour: 'numeric',
          minute: '2-digit',
        }),
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error')
    } finally {
      const elapsed = Date.now() - spinStart
      const remaining = MIN_SPIN_DURATION - elapsed
      if (remaining > 0) {
        setTimeout(() => setIsSpinning(false), remaining)
      } else {
        setIsSpinning(false)
      }
    }
  }, [])

  useEffect(() => {
    refreshData()
  }, [refreshData])

  useEffect(() => {
    const intervalId = setInterval(() => {
      refreshData()
    }, refreshInterval * 1000)

    return () => clearInterval(intervalId)
  }, [refreshData, refreshInterval])

  const handleIntervalChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    const value = Number.parseInt(event.target.value, 10)
    setRefreshInterval(value)
    localStorage.setItem(REFRESH_INTERVAL_KEY, value.toString())
  }

  const sortedEvents = useMemo(() => {
    return [...events].sort((a, b) => {
      const aTime = new Date(a.reported_at).getTime()
      const bTime = new Date(b.reported_at).getTime()
      return bTime - aTime
    })
  }, [events])

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
      for (const marker of eventMarkersRef.current) {
        marker.remove()
      }
      for (const marker of unitMarkersRef.current) {
        marker.remove()
      }
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
    const eventLocations = sortedEvents
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
      const el = document.createElement('div')
      el.className = 'event-marker'
      el.innerHTML = `
        <div style="
          position: relative;
          width: 16px;
          height: 16px;
        ">
          <div style="
            position: absolute;
            width: 100%;
            height: 100%;
            background-color: #ef4444;
            border: 2px solid white;
            border-radius: 50%;
            box-shadow: 0 2px 6px rgba(0,0,0,0.4);
          "></div>
          <div style="
            position: absolute;
            width: 100%;
            height: 100%;
            background-color: #ef4444;
            border-radius: 50%;
            animation: pulse 2s ease-out infinite;
            opacity: 0.6;
          "></div>
        </div>
      `

      const marker = new maplibregl.Marker({ element: el })
        .setLngLat([lng, lat])
        .setPopup(
          new maplibregl.Popup({ offset: 16, className: 'event-popup' }).setHTML(
            `<div style="font-family: system-ui, sans-serif;">
               <div style="font-weight: 600; font-size: 14px; margin-bottom: 4px;">${event.title}</div>
               <div style="font-size: 12px; color: #666;">${formatTimestamp(event.reported_at)}</div>
             </div>`,
          ),
        )
        .addTo(map)

      eventMarkersRef.current.push(marker)
    }
  }, [sortedEvents])

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
            className="space-y-2 bg-slate-800/50 backdrop-blur-sm p-3 border border-blue-500/10 rounded-xl hover:border-blue-500/30 transition-colors"
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
            <div className="flex items-center gap-2 text-slate-400 text-xs">
              <span className="hidden sm:inline">Auto-refresh:</span>
              <select
                value={refreshInterval}
                onChange={handleIntervalChange}
                className="bg-slate-800/80 border border-blue-500/20 rounded-lg px-2 py-1.5 text-xs text-slate-200 focus:outline-none focus:ring-2 focus:ring-blue-500/40 cursor-pointer"
                aria-label="Auto-refresh interval"
              >
                {REFRESH_OPTIONS.map((seconds) => (
                  <option key={seconds} value={seconds}>
                    {seconds}s
                  </option>
                ))}
              </select>
              <button
                onClick={refreshData}
                disabled={isSpinning}
                className="p-1 hover:bg-slate-700/50 rounded-md transition-colors disabled:opacity-50"
                aria-label="Refresh data"
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
                  className={isSpinning ? 'animate-spin' : 'hover:text-blue-400 transition-colors'}
                >
                  <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" />
                  <path d="M21 3v5h-5" />
                  <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" />
                  <path d="M8 16H3v5" />
                </svg>
              </button>
            </div>
            <Button variant="ghost">Docs</Button>
            <Button variant="ghost">Status</Button>
            <div className="text-slate-400 text-xs hidden sm:block">
              <span className="opacity-70">Updated </span>
              {lastUpdated}
            </div>
          </div>
        </div>
      </nav>

      <main className="relative flex flex-1 min-h-[calc(100vh-72px)]">
        <div
          ref={mapContainerRef}
          className="z-0 flex-1 min-h-0"
          aria-label="Event locations map"
        />

        <Card
          className={`z-10 absolute shadow-2xl shadow-slate-950/60 p-4 flex flex-col ${isDragging ? '' : 'transition-all duration-300'}`}
          style={{
            top: Math.max(16, Math.min(panelPosition.y, window.innerHeight - 100)),
            left: Math.max(16, Math.min(panelPosition.x, window.innerWidth - 400)),
            width: isPanelCollapsed ? 'auto' : `min(384px, calc(100vw - ${panelPosition.x + 32}px))`,
            maxWidth: isPanelCollapsed ? 'none' : '384px',
            maxHeight: isPanelCollapsed ? 'none' : `calc(100vh - ${panelPosition.y + 100}px)`,
            cursor: isDragging ? 'grabbing' : 'default',
          }}
        >
          <button
            type="button"
            className="flex justify-between items-center cursor-grab select-none panel-header flex-shrink-0 w-full bg-transparent border-none text-left"
            onClick={() => {
              if (!isDragging) setIsPanelCollapsed(!isPanelCollapsed)
            }}
            onMouseDown={(e) => {
              e.preventDefault()
              setIsDragging(true)
              dragStartRef.current = {
                x: e.clientX,
                y: e.clientY,
                panelX: panelPosition.x,
                panelY: panelPosition.y,
              }
              
              const handleMouseMove = (moveEvent: MouseEvent) => {
                const deltaX = moveEvent.clientX - dragStartRef.current.x
                const deltaY = moveEvent.clientY - dragStartRef.current.y
                setPanelPosition({
                  x: dragStartRef.current.panelX + deltaX,
                  y: dragStartRef.current.panelY + deltaY,
                })
              }
              
              const handleMouseUp = () => {
                setIsDragging(false)
                document.removeEventListener('mousemove', handleMouseMove)
                document.removeEventListener('mouseup', handleMouseUp)
              }
              
              document.addEventListener('mousemove', handleMouseMove)
              document.addEventListener('mouseup', handleMouseUp)
            }}
          >
            <div className="flex items-center gap-2">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-slate-500 flex-shrink-0">
                <circle cx="9" cy="12" r="1"/><circle cx="9" cy="5" r="1"/><circle cx="9" cy="19" r="1"/>
                <circle cx="15" cy="12" r="1"/><circle cx="15" cy="5" r="1"/><circle cx="15" cy="19" r="1"/>
              </svg>
              <p className="font-semibold text-white text-lg">Latest incidents</p>
              <span className="bg-blue-500/20 text-blue-400 text-xs font-medium px-2 py-0.5 rounded-full">
                {sortedEvents.length}
              </span>
            </div>
            <div className="flex items-center gap-3">
              {isPanelCollapsed ? (
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-slate-400">
                  <path d="m6 9 6 6 6-6"/>
                </svg>
              ) : (
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-slate-400">
                  <path d="m18 15-6-6-6 6"/>
                </svg>
              )}
            </div>
          </button>

          {!isPanelCollapsed && (
            <div className="mt-4 pb-4 panel-content flex-1 min-h-0 overflow-y-auto">
              {eventsDisplay}
            </div>
          )}
        </Card>
      </main>
    </div>
  )
}
