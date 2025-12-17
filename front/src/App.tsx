import type { ChangeEvent } from 'react'
import { useCallback, useEffect, useMemo, useState } from 'react'

import { fastPinPonService } from './services/FastPinPonService'
import { Navbar } from './components/layout/Navbar'
import { MapContainer } from './components/map/MapContainer'
import { EventPanel } from './components/events/EventPanel'
import type { EventSummary, UnitSummary } from './types'

const REFRESH_INTERVAL_KEY = 'refreshInterval'
const MIN_SPIN_DURATION = 500

export function App() {
  const [events, setEvents] = useState<EventSummary[]>([])
  const [units, setUnits] = useState<UnitSummary[]>([])
  const [isSpinning, setIsSpinning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<string>('—')
  const [refreshInterval, setRefreshInterval] = useState<number>(() => {
    const saved = localStorage.getItem(REFRESH_INTERVAL_KEY)
    return saved ? Number.parseInt(saved, 10) : 10
  })

  // Fetching logic
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
        <MapContainer events={sortedEvents} units={units} />
        <EventPanel events={sortedEvents} error={error} />
      </main>
    </div>
  )
}
