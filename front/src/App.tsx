import type { ChangeEvent } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { fastPinPonService } from './services/FastPinPonService'
import { Navbar } from './components/layout/Navbar'
import { MapContainer } from './components/map/MapContainer'
import { EventPanel } from './components/events/EventPanel'
import { RecentLogsTicker } from './components/events/RecentLogsTicker'
import { UnitPanel } from './components/units/UnitPanel'
import { EventDetailPanel } from './components/events/EventDetailPanel'
import { CreateEventModal } from './components/events/CreateEventModal'
import { DashboardPage } from './components/dashboard/DashboardPage'
import { AddUnitModal } from './components/dashboard/AddUnitModal'
import { HistoryPage } from './components/history/HistoryPage'
import type { CreateEventRequest, EventType } from './types/eventTypes'
import type { EventSummary, UnitSummary, Building, UnitType, EventLog } from './types'
import { useAuth } from './auth/AuthProvider'

const REFRESH_INTERVAL_KEY = 'refreshInterval'
const MIN_SPIN_DURATION = 500
type ViewMode = 'live' | 'dashboard' | 'history'

export function App() {
  const [events, setEvents] = useState<EventSummary[]>([])
  const [units, setUnits] = useState<UnitSummary[]>([])
  const [recentLogs, setRecentLogs] = useState<EventLog[]>([])
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null)
  const [selectedStationId, setSelectedStationId] = useState<string | null>(null)
  const [isSpinning, setIsSpinning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<string>('—')
  const [isPaused, setIsPaused] = useState(false)
  const [refreshInterval, setRefreshInterval] = useState<number>(() => {
    const saved = localStorage.getItem(REFRESH_INTERVAL_KEY)
    return saved ? Number.parseInt(saved, 10) : 5
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
      const data = await fastPinPonService.getSync(25, 3, token ?? undefined, ['completed', 'cancelled'])
      const eventsData = data.events
      const unitsData = data.units
      const logsData = data.recent_logs

      // If we have a selected event that isn't in the fresh list yet (e.g. just created),
      // we keep it to avoid selection jumping or disappearing.
      setEvents((prev) => {
        if (!selectedEventId) return eventsData
        const existsInNew = eventsData.some((e: EventSummary) => e.id === selectedEventId)
        if (existsInNew) return eventsData

        const currentSelected = prev.find((e: EventSummary) => e.id === selectedEventId)
        if (currentSelected) {
          // If the selected event is missing from the list (usually because it's completed/cancelled),
          // we only keep it if it's very fresh (less than 2x refresh interval).
          // This ensures that "just created" events stay while indexing,
          // but "just closed" events correctly disappear.
          const reportedAt = new Date(currentSelected.reported_at).getTime()
          const now = Date.now()
          const isVeryRecent = now - reportedAt < (refreshInterval * 2 * 1000)

          if (isVeryRecent) {
            return [currentSelected, ...eventsData]
          }
        }
        return eventsData
      })

      setUnits(unitsData)
      setRecentLogs(logsData)
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
  }, [isAuthenticated, token, selectedEventId, refreshInterval])

  useEffect(() => {
    if (isAuthenticated) {
      refreshData()
    }
  }, [isAuthenticated, refreshData])

  useEffect(() => {
    ; (async () => {
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
    return [...events].sort((a: EventSummary, b: EventSummary) => {
      const aTime = new Date(a.reported_at).getTime()
      const bTime = new Date(b.reported_at).getTime()
      return bTime - aTime
    })
  }, [events])

  useEffect(() => {
    if (!isAuthenticated) {
      setEvents([])
      setUnits([])
      setSelectedEventId(null)
    }
  }, [isAuthenticated])

  const selectedEvent = useMemo(
    () => sortedEvents.find((event: EventSummary) => event.id === selectedEventId) ?? null,
    [sortedEvents, selectedEventId],
  )

  const handleCreateEvent = useCallback(
    async (payload: CreateEventRequest) => {
      const newEvent = await fastPinPonService.createEvent(payload, true)

      // Update local state immediately so selection works even before refresh completes
      // We look up the event type name to avoid showing an empty label
      const eventTypeName = eventTypes.find((t: EventType) => t.code === newEvent.event_type_code)?.name ?? ''
      const optimisticEvent = { ...newEvent, event_type_name: eventTypeName }

      setEvents((prev: EventSummary[]) => [optimisticEvent, ...prev])
      setSelectedEventId(optimisticEvent.id)
      setPendingLocation(null)
      setIsCreateOpen(false)

      await refreshData()
    },
    [refreshData, eventTypes],
  )

  const handleAddUnit = useCallback(
    async (callSign: string, unitTypeCode: string, locationId: string, lat: number, lon: number) => {
      await fastPinPonService.createUnit(callSign, unitTypeCode, lat, lon, token ?? undefined, locationId)
      await refreshData()
    },
    [refreshData, token],
  )

  const handleEventSelect = (eventId: string) => {
    setSelectedEventId(eventId)
  }

  const handleBuildingSelect = (buildingId: string) => {
    // Navigate to dashboard with station filter
    setSelectedStationId(buildingId)
    setView('dashboard')
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
    <div className={`flex flex-col bg-slate-950 min-h-screen text-slate-100 ${view === 'live' ? 'h-screen overflow-hidden' : ''}`}>
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

      {view === 'dashboard' && (
        <main className="flex flex-1 min-h-[calc(100vh-72px)] overflow-auto">
          <DashboardPage
            units={units}
            buildings={buildings}
            selectedStationId={selectedStationId}
            onStationChange={setSelectedStationId}
            onRefresh={refreshData}
          />
        </main>
      )}

      {view === 'history' && (
        <main className="flex flex-1 min-h-[calc(100vh-72px)] overflow-auto">
          <HistoryPage />
        </main>
      )}

      {view === 'live' && (
        <main className="relative flex flex-1 min-h-[calc(100vh-72px)] overflow-hidden">
          <RecentLogsTicker logs={recentLogs} error={error} />
          <MapContainer
            events={sortedEvents}
            units={units}
            buildings={buildings}
            onEventSelect={handleEventSelect}
            onBuildingSelect={handleBuildingSelect}
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
            onEventSelect={handleEventSelect}
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
