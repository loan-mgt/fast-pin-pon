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

  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [eventTypes, setEventTypes] = useState<EventType[]>([])
  const [pendingLocation, setPendingLocation] = useState<{ latitude: number; longitude: number } | null>(null)

  const refreshData = useCallback(async () => {
    setIsSpinning(true)
    setError(null)

    const spinStart = Date.now()

    try {
      const [eventsData, unitsData] = await Promise.all([
        fastPinPonService.getEvents(),
        fastPinPonService.getUnits(),
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
  }, [])

  useEffect(() => {
    refreshData()
  }, [refreshData])

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
      refreshData()
    }, refreshInterval * 1000)

    return () => clearInterval(intervalId)
  }, [refreshData, refreshInterval])

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
  return (
    <div className="flex flex-col bg-slate-950 min-h-screen text-slate-100">
      <Navbar
        refreshInterval={refreshInterval}
        onIntervalChange={handleIntervalChange}
        onRefresh={refreshData}
        isSpinning={isSpinning}
        lastUpdated={lastUpdated}
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
        <EventPanel events={sortedEvents} error={error} />
        <EventDetailPanel event={selectedEvent} onClose={handleCloseDetail} />
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
