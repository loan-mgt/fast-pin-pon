import type { JSX } from 'react'
import { useState } from 'react'
import { Card } from '../ui/card'
import { EventList } from './EventList'
import type { EventSummary } from '../../types'

interface EventPanelProps {
    events: EventSummary[]
    error: string | null
    onEventSelect?: (eventId: string) => void
    selectedEventId?: string | null
}

export function EventPanel({ events, error, onEventSelect, selectedEventId }: Readonly<EventPanelProps>): JSX.Element {
    const [isPanelCollapsed, setIsPanelCollapsed] = useState(true)

    return (
        <Card
            className={
                `z-10 fixed bottom-0 right-0 shadow-2xl shadow-slate-950/60 p-0 flex flex-col overflow-hidden rounded-none ` +
                (isPanelCollapsed
                    ? 'w-auto max-w-[90vw]'
                    : 'w-[320px] min-w-[260px] max-w-[90vw] max-h-[60vh]')
            }
        >
            <button
                type="button"
                className="flex justify-between items-center select-none panel-header flex-shrink-0 w-full text-left bg-transparent border-none whitespace-nowrap gap-2 px-2 py-1"
                aria-label={isPanelCollapsed ? 'Open incidents panel' : 'Close incidents panel'}
                onClick={() => setIsPanelCollapsed(!isPanelCollapsed)}
            >
                <div className="flex items-center gap-2 whitespace-nowrap">
                    <p className="font-semibold text-white text-lg leading-none">Latest incidents</p>
                    <span className="bg-blue-500/20 text-blue-400 text-xs font-medium px-2 py-0.5 rounded-full leading-none">
                        {events.length}
                    </span>
                </div>
            </button>

            {!isPanelCollapsed && (
                <div className="mt-1 panel-content flex-1 min-h-0 overflow-y-auto">
                    <EventList
                        events={events}
                        error={error}
                        onSelect={onEventSelect}
                        selectedEventId={selectedEventId}
                    />
                </div>
            )}
        </Card>
    )
}
