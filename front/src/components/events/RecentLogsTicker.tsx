import { useState } from 'react'

import { Card } from '../ui/card'
import { StatusBadge } from '../ui/StatusBadge'
import type { ActivityLog } from '../../types'

const MAX_LOGS = 10

function formatTimeAgo(value: string): string {
    const now = new Date()
    const then = new Date(value)
    const diffMs = now.getTime() - then.getTime()
    const diffSec = Math.floor(diffMs / 1000)
    const diffMin = Math.floor(diffSec / 60)
    const diffHour = Math.floor(diffMin / 60)
    const diffDay = Math.floor(diffHour / 24)

    if (diffSec < 60) return `${diffSec}s ago`
    if (diffMin < 60) return `${diffMin}m ago`
    if (diffHour < 24) return `${diffHour}h ago`
    return `${diffDay}d ago`
}

import { truncateMiddle } from '../../utils/stringUtils'



type RecentLogsTickerProps = {
    logs: ActivityLog[]
    loading?: boolean
    error?: string | null
    onUnitClick?: (unitId: string) => void
    onEventClick?: (eventId: string) => void
}

export function RecentLogsTicker({ logs, loading, error, onUnitClick, onEventClick }: Readonly<RecentLogsTickerProps>) {
    const [open, setOpen] = useState(false)

    const handleItemClick = (log: ActivityLog) => {
        if (log.entity_type === 'unit' && log.entity_id && onUnitClick) {
            onUnitClick(log.entity_id)
        } else if (log.entity_type === 'intervention' && log.event_id && onEventClick) {
            onEventClick(log.event_id)
        }
    }

    return (
        <Card
            className={
                `z-10 fixed bottom-0 left-0 shadow-2xl shadow-slate-950/60 p-0 pb-1 flex flex-col overflow-hidden rounded-none w-[450px] min-w-[260px] max-w-[90vw] ` +
                (open ? 'max-h-[50vh]' : '')
            }
        >
            <button
                type="button"
                className="flex flex-shrink-0 justify-between items-center gap-2 bg-transparent px-2 py-1 border-none w-full text-left whitespace-nowrap cursor-pointer select-none panel-header"
                aria-label={open ? 'Masquer les logs d\'activité' : 'Afficher les logs d\'activité'}
                onClick={() => setOpen((v) => !v)}
            >
                <div className="flex items-center gap-2 whitespace-nowrap">
                    <p className="font-semibold text-white text-lg leading-none">Activity Logs</p>

                </div>
            </button>

            {open && (
                <div className="flex-1 mt-1 min-h-0 overflow-y-auto panel-content">
                    {error && <div className="px-3 py-2 text-xs text-red-300">{error}</div>}
                    {loading && !logs.length && <div className="px-3 py-2 text-xs text-slate-300">Chargement…</div>}
                    {!loading && logs.length === 0 && !error && (
                        <div className="px-3 py-3 text-sm text-slate-400">Aucun log récent.</div>
                    )}
                    <ul className="divide-y divide-slate-800/80">
                        {logs.slice(0, MAX_LOGS).map((log) => {
                            const isClickable = (log.entity_type === 'unit' && onUnitClick) ||
                                (log.entity_type === 'intervention' && onEventClick)

                            return (
                                <li
                                    key={log.id}
                                    className={`grid grid-cols-[50px_80px_100px_1fr] items-center gap-2 px-3 py-2 text-xs ${isClickable ? 'cursor-pointer hover:bg-slate-800/40' : ''}`}
                                    onClick={() => handleItemClick(log)}
                                >
                                    {/* Time ago */}
                                    <span className="text-left text-[11px] font-semibold text-cyan-300 whitespace-nowrap tabular-nums overflow-hidden">
                                        {formatTimeAgo(log.created_at)}
                                    </span>

                                    {/* Entity label (clickable unit or event) */}
                                    <span className={`min-w-0 font-medium truncate ${isClickable ? 'text-blue-300 underline underline-offset-2' : 'text-slate-300'}`}>
                                        {/* Unit: usually short, but truncating just in case */}
                                        {log.entity_type === 'unit' && log.unit_call_sign
                                            ? truncateMiddle(log.unit_call_sign, 3, 3)
                                            : null}
                                        {/* Event: often long, needs truncation */}
                                        {log.entity_type === 'intervention' && log.event_title
                                            ? truncateMiddle(log.event_title, 3, 3)
                                            : null}
                                    </span>



                                    {/* Old Status */}
                                    <div className="flex items-center overflow-hidden">
                                        {log.old_value ? (
                                            <StatusBadge
                                                status={log.old_value}
                                                type={(log.entity_type as 'unit' | 'intervention') || 'unit'}
                                                className="opacity-60 scale-90 origin-left shrink"
                                                showDot={false}
                                            />
                                        ) : null}
                                    </div>

                                    {/* New Status */}
                                    <div className="flex items-center overflow-hidden">
                                        <StatusBadge
                                            status={log.new_value || '?'}
                                            type={(log.entity_type as 'unit' | 'intervention') || 'unit'}
                                        />
                                    </div>
                                </li>
                            )
                        })}
                    </ul>
                </div>
            )}
        </Card>
    )
}
