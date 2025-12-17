import type { JSX, MouseEvent } from 'react'
import { useState, useRef } from 'react'
import { Card } from '../ui/card'
import { EventList } from './EventList'
import type { EventSummary } from '../../types'

interface EventPanelProps {
    events: EventSummary[]
    error: string | null
}

export function EventPanel({ events, error }: Readonly<EventPanelProps>): JSX.Element {
    const [isPanelCollapsed, setIsPanelCollapsed] = useState(false)
    const [panelPosition, setPanelPosition] = useState(() => ({
        x: globalThis.window === undefined ? 16 : globalThis.window.innerWidth - 400,
        y: 16
    }))
    const [isDragging, setIsDragging] = useState(false)
    const dragStartRef = useRef({ x: 0, y: 0, panelX: 0, panelY: 0 })

    const handleDragStart = (e: MouseEvent<HTMLButtonElement>) => {
        e.preventDefault()
        setIsDragging(true)
        dragStartRef.current = {
            x: e.clientX,
            y: e.clientY,
            panelX: panelPosition.x,
            panelY: panelPosition.y,
        }

        const handleMouseMove = (moveEvent: globalThis.MouseEvent) => {
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
    }

    return (
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
                onMouseDown={handleDragStart}
            >
                <div className="flex items-center gap-2">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-slate-500 flex-shrink-0">
                        <circle cx="9" cy="12" r="1" /><circle cx="9" cy="5" r="1" /><circle cx="9" cy="19" r="1" />
                        <circle cx="15" cy="12" r="1" /><circle cx="15" cy="5" r="1" /><circle cx="15" cy="19" r="1" />
                    </svg>
                    <p className="font-semibold text-white text-lg">Latest incidents</p>
                    <span className="bg-blue-500/20 text-blue-400 text-xs font-medium px-2 py-0.5 rounded-full">
                        {events.length}
                    </span>
                </div>
                <div className="flex items-center gap-3">
                    {isPanelCollapsed ? (
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-slate-400">
                            <path d="m6 9 6 6 6-6" />
                        </svg>
                    ) : (
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-slate-400">
                            <path d="m18 15-6-6-6 6" />
                        </svg>
                    )}
                </div>
            </button>

            {!isPanelCollapsed && (
                <div className="mt-4 pb-4 panel-content flex-1 min-h-0 overflow-y-auto">
                    <EventList events={events} error={error} />
                </div>
            )}
        </Card>
    )
}
