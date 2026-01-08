import type { ChangeEvent } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { fastPinPonService } from './services/FastPinPonService'
import { Navbar } from './components/layout/Navbar'
import { MapContainer } from './components/map/MapContainer'
import { EventPanel } from './components/events/EventPanel'
import { UnitPanel } from './components/units/UnitPanel'
import { EventDetailPanel } from './components/events/EventDetailPanel'
import { CreateEventModal } from './components/events/CreateEventModal'
import { DashboardPage } from './components/dashboard/DashboardPage'
import { AddUnitModal } from './components/dashboard/AddUnitModal'
import type { CreateEventRequest, EventType } from './types/eventTypes'
import type { EventSummary, UnitSummary, Building, UnitType } from './types'
import { useAuth } from './auth/AuthProvider'

const REFRESH_INTERVAL_KEY = 'refreshInterval'
const MIN_SPIN_DURATION = 500
type ViewMode = 'live' | 'dashboard'

export function App() {
  const [events, setEvents] = useState<EventSummary[]>([])
  const [units, setUnits] = useState<UnitSummary[]>([])
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null)
  const [isSpinning, setIsSpinning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<string>('—')
  const [isPaused, setIsPaused] = useState(false)
  const [refreshInterval, setRefreshInterval] = useState<number>(() => {
    const saved = localStorage.getItem(REFRESH_INTERVAL_KEY)
    return saved ? Number.parseInt(saved, 10) : 10
  })
  const [view, setView] = useState<ViewMode>('live')
  const {
    isAuthenticated,
    initializing: isAuthLoading,
    token,
    profile,
    permissions,
    logout,
  } = useAuth()

  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [isAddUnitOpen, setIsAddUnitOpen] = useState(false)
  const [eventTypes, setEventTypes] = useState<EventType[]>([])
  const [unitTypes, setUnitTypes] = useState<UnitType[]>([])
  const [buildings, setBuildings] = useState<Building[]>([])
  const [pendingLocation, setPendingLocation] = useState<{ latitude: number; longitude: number } | null>(null)
  const flyToLocationRef = useRef<((lng: number, lat: number, zoom?: number) => void) | null>(null)

  const refreshData = useCallback(async () => {
    if (!isAuthenticated) return

    setIsSpinning(true)
    setError(null)

    const spinStart = Date.now()

    try {
      const [eventsData, unitsData] = await Promise.all([
        fastPinPonService.getEvents(25, token ?? undefined, ['completed']),
        fastPinPonService.getUnits(token ?? undefined),
      ])

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
  }, [isAuthenticated, token])

  useEffect(() => {
    if (isAuthenticated) {
      refreshData()
    }
  }, [isAuthenticated, refreshData])

  useEffect(() => {
    ;(async () => {
      try {
        const types = await fastPinPonService.getEventTypes()
        setEventTypes(types)
        const uTypes = await fastPinPonService.getUnitTypes()
        setUnitTypes(uTypes)
        const blds = await fastPinPonService.getBuildings()
        setBuildings(blds)
      } catch (err) {
        console.error(err)
      }
    })()
  }, [])

  useEffect(() => {
    const intervalId = setInterval(() => {
      if (isAuthenticated && !isPaused) refreshData()
    }, refreshInterval * 1000)

    return () => clearInterval(intervalId)
  }, [isAuthenticated, refreshData, refreshInterval, isPaused])

  const handleIntervalChange = (event: ChangeEvent<HTMLSelectElement>) => {
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

  useEffect(() => {
    if (!selectedEventId) return
    const exists = events.some((e) => e.id === selectedEventId)
    if (!exists) {
      setSelectedEventId(null)
    }
  }, [events, selectedEventId])

  useEffect(() => {
    if (!isAuthenticated) {
      setEvents([])
      setUnits([])
      setSelectedEventId(null)
    }
  }, [isAuthenticated])

  const selectedEvent = useMemo(
    () => sortedEvents.find((event) => event.id === selectedEventId) ?? null,
    [sortedEvents, selectedEventId],
  )

  const handleCreateEvent = useCallback(
    async (payload: CreateEventRequest) => {
      await fastPinPonService.createEvent(payload, true)
      await refreshData()
      setPendingLocation(null)
    },
    [refreshData],
  )

  const handleAddUnit = useCallback(
    async (callSign: string, unitTypeCode: string, homeBase: string, lat: number, lon: number) => {
      await fastPinPonService.createUnit(callSign, unitTypeCode, homeBase, lat, lon, token ?? undefined)
      await refreshData()
    },
    [refreshData, token],
  )

  const handleEventSelect = (eventId: string) => {
    setSelectedEventId(eventId)
  }

  const handleCloseDetail = () => {
    setSelectedEventId(null)
  }

  if (isAuthLoading) {
    return (
      <div className="flex flex-col justify-center items-center bg-slate-950 min-h-screen text-slate-100">
        <p className="text-slate-400 text-sm">Initialisation de la session…</p>
      </div>
    )
  }

  const userLabel = profile?.firstName
    ? `${profile.firstName} ${profile.lastName ?? ''}`.trim()
    : profile?.username ?? profile?.email ?? 'Utilisateur'
  return (
    <div className="flex flex-col bg-slate-950 min-h-screen text-slate-100">
      <Navbar
        refreshInterval={refreshInterval}
        onIntervalChange={handleIntervalChange}
        onRefresh={refreshData}
        isSpinning={isSpinning}
        lastUpdated={lastUpdated}
        currentView={view}
        onNavigate={setView}
        onLogout={logout}
        userLabel={userLabel}
        onAddUnit={() => setIsAddUnitOpen(true)}
      />

      {view === 'dashboard' ? (
        <main className="flex flex-1 min-h-[calc(100vh-72px)]">
          <DashboardPage units={units} onRefresh={refreshData} />
        </main>
      ) : (
        <main className="relative flex flex-1 min-h-[calc(100vh-72px)]">
          <MapContainer
            events={sortedEvents}
            units={units}
            buildings={buildings}
            onEventSelect={handleEventSelect}
            selectedEventId={selectedEventId}
            onCreateAtLocation={(coords) => {
              setPendingLocation(coords)
              setIsCreateOpen(true)
            }}
            onMapReady={(flyTo) => {
              flyToLocationRef.current = flyTo
            }}
          />
          <UnitPanel units={units} />
          <EventPanel
            events={sortedEvents}
            error={error}
            onEventSelect={handleEventSelect}
            selectedEventId={selectedEventId}
          />
          <EventDetailPanel
          event={selectedEvent}
          onClose={handleCloseDetail}
          permissions={permissions}
          onRefresh={refreshData}
          onTogglePauseRefresh={setIsPaused}
          onLocateEvent={(lng, lat) => flyToLocationRef.current?.(lng, lat, 14)}
        />
        </main>
      )}

      <CreateEventModal
        isOpen={isCreateOpen}
        onClose={() => {
          setIsCreateOpen(false)
          setPendingLocation(null)
        }}
        eventTypes={eventTypes}
        onSubmit={handleCreateEvent}
        initialLocation={pendingLocation}
      />

      <AddUnitModal
        isOpen={isAddUnitOpen}
        onClose={() => setIsAddUnitOpen(false)}
        onSubmit={handleAddUnit}
        unitTypes={unitTypes}
        buildings={buildings}
      />
    </div>
  )
}
