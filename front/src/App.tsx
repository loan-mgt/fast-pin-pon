import type { ChangeEvent } from 'react'
import { useCallback, useEffect, useMemo, useState } from 'react'

import { fastPinPonService } from './services/FastPinPonService'
import { Navbar } from './components/layout/Navbar'
import { MapContainer } from './components/map/MapContainer'
import { EventPanel } from './components/events/EventPanel'
import { UnitPanel } from './components/units/UnitPanel'
import { EventDetailPanel } from './components/events/EventDetailPanel'
import { CreateEventModal } from './components/events/CreateEventModal'
import type { CreateEventRequest, EventType } from './types/eventTypes'
import type { EventSummary, UnitSummary } from './types'
import { useAuth } from './auth/AuthProvider'
import { Button } from './components/ui/button'

const REFRESH_INTERVAL_KEY = 'refreshInterval'
const MIN_SPIN_DURATION = 500

export function App() {
  const [events, setEvents] = useState<EventSummary[]>([])
  const [units, setUnits] = useState<UnitSummary[]>([])
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null)
  const [isSpinning, setIsSpinning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<string>('—')
  const [refreshInterval, setRefreshInterval] = useState<number>(() => {
    const saved = localStorage.getItem(REFRESH_INTERVAL_KEY)
    return saved ? Number.parseInt(saved, 10) : 10
  })
  const {
    isAuthenticated,
    initializing: isAuthLoading,
    token,
    profile,
    permissions,
    error: authError,
    login,
    logout,
  } = useAuth()

  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [eventTypes, setEventTypes] = useState<EventType[]>([])
  const [pendingLocation, setPendingLocation] = useState<{ latitude: number; longitude: number } | null>(null)

  const refreshData = useCallback(async () => {
    if (!isAuthenticated) return

    setIsSpinning(true)
    setError(null)

    const spinStart = Date.now()

    try {
      const [eventsData, unitsData] = await Promise.all([
        fastPinPonService.getEvents(25, token ?? undefined),
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
      } catch (err) {
        console.error(err)
      }
    })()
  }, [])

  useEffect(() => {
    const intervalId = setInterval(() => {
      if (isAuthenticated) refreshData()
    }, refreshInterval * 1000)

    return () => clearInterval(intervalId)
  }, [isAuthenticated, refreshData, refreshInterval])

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
      await fastPinPonService.createEvent(payload)
      await refreshData()
      setPendingLocation(null)
    },
    [refreshData],
  )

  const handleEventSelect = (eventId: string) => {
    setSelectedEventId(eventId)
  }

  const handleCloseDetail = () => {
    setSelectedEventId(null)
  }

  if (isAuthLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-slate-950 text-slate-100">
        <p className="text-sm text-slate-400">Initialisation de la session…</p>
      </div>
    )
  }

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-slate-950 text-slate-100 px-6">
        <div className="max-w-md w-full space-y-4 text-center">
          <p className="text-cyan-300/70 text-xs uppercase tracking-[0.35em]">Fast Pin Pon</p>
          <h1 className="text-2xl font-semibold text-white">Connexion requise</h1>
          <p className="text-slate-400 text-sm">Authentifiez-vous via Keycloak pour accéder au tableau de bord.</p>
          {authError && <p className="text-red-400 text-sm">{authError}</p>}
          <div className="flex justify-center">
            <Button onClick={login} className="px-6">Se connecter</Button>
          </div>
        </div>
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
        onLogout={logout}
        userLabel={userLabel}
      />

      <main className="relative flex flex-1 min-h-[calc(100vh-72px)]">
        <MapContainer
          events={sortedEvents}
          units={units}
          onEventSelect={handleEventSelect}
          selectedEventId={selectedEventId}
          onCreateAtLocation={(coords) => {
            setPendingLocation(coords)
            setIsCreateOpen(true)
          }}
        />
        <UnitPanel units={units} />
        <EventPanel
          events={sortedEvents}
          error={error}
          onEventSelect={handleEventSelect}
          selectedEventId={selectedEventId}
        />
        <EventDetailPanel event={selectedEvent} onClose={handleCloseDetail} permissions={permissions} />
      </main>

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
    </div>
  )
}
