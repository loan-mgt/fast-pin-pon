import { useEffect, useRef } from 'react'
import type { JSX } from 'react'
import type { EventSummary } from '../../types'
import { formatTimestamp, severityLabel } from '../../utils/format'
import { getEventIconWithSeverity } from '../map/mapIcons'

interface EventListProps {
    events: EventSummary[]
    error: string | null
    onSelect?: (eventId: string) => void
    selectedEventId?: string | null
}

export function EventList({ events, error, onSelect, selectedEventId }: Readonly<EventListProps>): JSX.Element {
    const selectedRef = useRef<HTMLButtonElement | null>(null)

    useEffect(() => {
        if (selectedRef.current) {
            selectedRef.current.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }
    }, [selectedEventId])
    if (error) {
        return <p className="text-rose-300 text-sm">{error}</p>
    }

    if (events.length === 0) {
        return <p className="text-slate-300 text-sm">Awaiting events…</p>
    }

    return (
        <div className="space-y-3">
            {events.map((event) => (
                <button
                    key={event.id}
                    type="button"
                    ref={selectedEventId === event.id ? selectedRef : null}
                    onClick={() => onSelect?.(event.id)}
                    className={`w-full text-left space-y-2 backdrop-blur-sm p-3 border rounded-xl transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400/70 cursor-pointer ${
                        selectedEventId === event.id
                            ? 'bg-blue-900/60 border-blue-400/60'
                            : 'bg-slate-800/50 border-blue-500/10 hover:border-blue-500/30'
                    }`}
                >
                    <div className="flex justify-between items-start gap-2">
                        <h3 className="font-semibold text-white text-sm">{event.title}</h3>
                        <span className="text-[0.55rem] text-cyan-300/90 uppercase tracking-[0.4em]">
                            {severityLabel(event.severity)}
                        </span>
                    </div>
                    <p className="text-slate-400 text-xs">{event.intervention_status ?? 'Status unknown'}</p>
                    <div className="flex justify-between items-end gap-2">
                        <div className="flex flex-wrap items-center gap-2 text-[0.65rem] text-slate-400">
                            <span>{formatTimestamp(event.reported_at)}</span>
                            {event.location?.latitude != null && event.location?.longitude != null && (
                                <span>
                                    {event.location.latitude.toFixed(3)}, {event.location.longitude.toFixed(3)}
                                </span>
                            )}
                        </div>
                        <div
                            className="w-8 h-8 flex-shrink-0"
                            dangerouslySetInnerHTML={{
                                __html: getEventIconWithSeverity(event.event_type_code, event.severity),
                            }}
                        />
                    </div>
                </button>
            ))}
        </div>
    )
}
