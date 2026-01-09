import { useEffect, useRef, useState } from 'react'

import { Card } from '../ui/card'
import type { EventLog } from '../../types'
import { fastPinPonService } from '../../services/FastPinPonService'

const MAX_LOGS = 3

function formatTimestamp(value: string): string {
    const date = new Date(value)
    return date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function buildLabel(log: EventLog): string {
    const actor = log.actor ? ` · ${log.actor}` : ''
    const code = log.code.toLowerCase() === 'heartbeat' ? 'Signal' : log.code
    return `${code}${actor}`
}

type RecentLogsTickerProps = {
    token?: string | null
    refreshIntervalSec?: number
}

export function RecentLogsTicker({ token, refreshIntervalSec = 5 }: Readonly<RecentLogsTickerProps>) {
    const [open, setOpen] = useState(false)
    const [logs, setLogs] = useState<EventLog[]>([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const intervalRef = useRef<number | null>(null)

    useEffect(() => {
        let cancelled = false

        const fetchLogs = async () => {
            setLoading(true)
            setError(null)
            try {
                const data = await fastPinPonService.getRecentEventLogs(MAX_LOGS, token ?? undefined)
                if (!cancelled) setLogs(data)
            } catch (err) {
                if (!cancelled) setError(err instanceof Error ? err.message : 'Erreur inconnue')
            } finally {
                if (!cancelled) setLoading(false)
            }
        }

        const intervalMs = Math.max(1000, refreshIntervalSec * 1000)
        fetchLogs()
        intervalRef.current = globalThis.setInterval(fetchLogs, intervalMs)

        return () => {
            cancelled = true
            if (intervalRef.current !== null) {
                globalThis.clearInterval(intervalRef.current)
                intervalRef.current = null
            }
        }
    }, [token, refreshIntervalSec])

    return (
        <Card
            className={
                `z-10 fixed bottom-0 left-0 shadow-2xl shadow-slate-950/60 p-0 pb-1 flex flex-col overflow-hidden rounded-none w-[320px] min-w-[260px] max-w-[90vw] ` +
                (open ? 'max-h-[50vh]' : '')
            }
        >
            <button
                type="button"
                className="flex flex-shrink-0 justify-between items-center gap-2 bg-transparent px-2 py-1 border-none w-full text-left whitespace-nowrap cursor-pointer select-none panel-header"
                aria-label={open ? 'Masquer les événements récents' : 'Afficher les événements récents'}
                onClick={() => setOpen((v) => !v)}
            >
                <div className="flex items-center gap-2 whitespace-nowrap">
                    <p className="font-semibold text-white text-lg leading-none">Recent event logs</p>
                    <span className="bg-blue-500/20 px-2 py-0.5 rounded-full font-medium text-blue-400 text-xs leading-none">
                        {Math.min(logs.length, MAX_LOGS)}
                    </span>
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
                        {logs.slice(0, 3).map((log) => (
                            <li key={log.event_id} className="flex items-center gap-2 px-3 py-2 text-xs text-slate-100">
                                <span className="text-[11px] font-semibold text-cyan-300 whitespace-nowrap">
                                    {formatTimestamp(log.created_at)}
                                </span>
                                <span className="flex-1 truncate text-sm text-slate-100">
                                    {log.event_title || log.event_id} · {buildLabel(log)}
                                </span>
                                <span className="text-[10px] uppercase tracking-[0.18em] text-slate-400 whitespace-nowrap">
                                    {log.event_type_code}
                                </span>
                            </li>
                        ))}
                        {logs.length === 0 && !loading && !error && (
                            <li className="px-3 py-3 text-sm text-slate-400">Aucun log récent.</li>
                        )}
                    </ul>
                </div>
            )}
        </Card>
    )
}
