import type { JSX } from 'react'
import type { EventSummary } from '../../types'
import { formatTimestamp, severityLabel } from '../../utils/format'

interface EventListProps {
    events: EventSummary[]
    error: string | null
}

export function EventList({ events, error }: Readonly<EventListProps>): JSX.Element {
    if (error) {
        return <p className="text-rose-300 text-sm">{error}</p>
    }

    if (events.length === 0) {
        return <p className="text-slate-300 text-sm">Awaiting events…</p>
    }

    return (
        <div className="space-y-3">
            {events.map((event) => (
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
                        {event.location?.latitude != null && event.location?.longitude != null && (
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
