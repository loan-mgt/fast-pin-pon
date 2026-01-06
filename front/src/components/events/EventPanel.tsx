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
                `z-10 fixed bottom-0 right-0 shadow-2xl shadow-slate-950/60 p-0 flex flex-col overflow-hidden rounded-none w-[320px] min-w-[260px] max-w-[90vw] ` +
                (isPanelCollapsed ? '' : 'max-h-[60vh]')
            }
        >
            <button
                type="button"
                className="flex flex-shrink-0 justify-between items-center gap-2 bg-transparent px-2 py-1 border-none w-full text-left whitespace-nowrap cursor-pointer select-none panel-header"
                aria-label={isPanelCollapsed ? 'Open incidents panel' : 'Close incidents panel'}
                onClick={() => setIsPanelCollapsed(!isPanelCollapsed)}
            >
                <div className="flex items-center gap-2 whitespace-nowrap">
                    <p className="font-semibold text-white text-lg leading-none">Latest incidents</p>
                    <span className="bg-blue-500/20 px-2 py-0.5 rounded-full font-medium text-blue-400 text-xs leading-none">
                        {events.length}
                    </span>
                </div>
            </button>

            {!isPanelCollapsed && (
                <div className="flex-1 mt-1 min-h-0 overflow-y-auto panel-content">
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
