import { useState, useEffect, useMemo } from 'react'
import type { JSX } from 'react'
import type { EventSummary } from '../../types'
import { fastPinPonService } from '../../services/FastPinPonService'
import { useAuth } from '../../auth/AuthProvider'
import { getEventIconWithSeverity } from '../map/mapIcons'
import { severityLabel } from '../../utils/format'

type SortField = 'date' | 'type' | 'severity' | 'duration'
type SortDirection = 'asc' | 'desc'

function formatDate(dateString: string): string {
    const date = new Date(dateString)
    return date.toLocaleDateString('fr-FR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
    })
}

function formatTime(dateString: string): string {
    const date = new Date(dateString)
    return date.toLocaleTimeString('fr-FR', {
        hour: '2-digit',
        minute: '2-digit',
    })
}

function resolveStart(event: EventSummary): string | null {
    return event.started_at ?? event.reported_at ?? null
}

function resolveEnd(event: EventSummary): string | null {
    if (event.completed_at) return event.completed_at
    if (event.closed_at) return event.closed_at
    if (event.intervention_status === 'completed' && event.updated_at) return event.updated_at
    return null
}

function parseDate(value?: string | null): number | null {
    if (!value) return null
    const ts = new Date(value).getTime()
    return Number.isFinite(ts) ? ts : null
}

function calculateDuration(event: EventSummary): string {
    const start = parseDate(resolveStart(event))
    let end = parseDate(resolveEnd(event))
    if (start !== null && end === null) end = Date.now()
    if (start === null || end === null) return '—'

    const durationMs = end - start
    if (durationMs < 0) return '—'

    const totalSeconds = Math.floor(durationMs / 1000)
    const seconds = totalSeconds % 60
    const totalMinutes = Math.floor(totalSeconds / 60)
    const minutes = totalMinutes % 60
    const hours = Math.floor(totalMinutes / 60)

    if (hours > 0) {
        return `${hours}h ${minutes}min ${seconds}s`
    }
    if (totalMinutes > 0) {
        return `${totalMinutes}min ${seconds}s`
    }
    return `${seconds}s`
}

function getDurationMinutes(event: EventSummary): number {
    const start = parseDate(resolveStart(event))
    let end = parseDate(resolveEnd(event))
    if (start !== null && end === null) end = Date.now()
    if (start === null || end === null) return Number.MAX_SAFE_INTEGER
    return Math.floor((end - start) / (1000 * 60))
}

function getStatusStyle(status?: string): string {
    if (status === 'completed') {
        return 'bg-emerald-500/20 text-emerald-400'
    }
    if (status === 'cancelled') {
        return 'bg-slate-500/20 text-slate-400'
    }
    return 'bg-amber-500/20 text-amber-400'
}

interface SortIconProps {
    field: SortField
    currentField: SortField
    direction: SortDirection
}

function SortIcon({ field, currentField, direction }: Readonly<SortIconProps>): JSX.Element {
    if (currentField !== field) {
        return (
            <svg className="w-4 h-4 opacity-30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4" />
            </svg>
        )
    }
    if (direction === 'asc') {
        return (
            <svg className="w-4 h-4 text-cyan-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
            </svg>
        )
    }
    return (
        <svg className="w-4 h-4 text-cyan-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
    )
}

export function HistoryPage(): JSX.Element {
    const { token } = useAuth()
    const PAGE_SIZE = 10
    const [events, setEvents] = useState<EventSummary[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [sortField, setSortField] = useState<SortField>('date')
    const [sortDirection, setSortDirection] = useState<SortDirection>('desc')
    const [filterType, setFilterType] = useState<string>('all')
    const [filterSeverity, setFilterSeverity] = useState<'all' | 'low' | 'medium' | 'high'>('all')
    const [page, setPage] = useState(0)

    useEffect(() => {
        const fetchHistory = async () => {
            setLoading(true)
            setError(null)
            try {
                // Fetch all events including completed ones
                const data = await fastPinPonService.getEvents(500, token ?? undefined)
                setEvents(data)
            } catch (err) {
                setError(err instanceof Error ? err.message : 'Échec du chargement de l\'historique')
            } finally {
                setLoading(false)
            }
        }
        fetchHistory()
    }, [token])

    const eventTypes = useMemo(() => {
        const types = new Set(events.map((e) => e.event_type_code))
        return Array.from(types).sort((a, b) => a.localeCompare(b))
    }, [events])

    const sortedAndFilteredEvents = useMemo(() => {
        let filtered = events
        if (filterType !== 'all') {
            filtered = filtered.filter((e) => e.event_type_code === filterType)
        }
        if (filterSeverity !== 'all') {
            filtered = filtered.filter((e) => {
                if (filterSeverity === 'low') return e.severity <= 2
                if (filterSeverity === 'medium') return e.severity === 3
                return e.severity >= 4
            })
        }

        return [...filtered].sort((a, b) => {
            let comparison = 0
            switch (sortField) {
                case 'date':
                    comparison = (parseDate(a.reported_at) ?? 0) - (parseDate(b.reported_at) ?? 0)
                    break
                case 'type':
                    comparison = a.event_type_code.localeCompare(b.event_type_code)
                    break
                case 'severity':
                    comparison = a.severity - b.severity
                    break
                case 'duration':
                    comparison = getDurationMinutes(a) - getDurationMinutes(b)
                    break
            }
            return sortDirection === 'asc' ? comparison : -comparison
        })
    }, [events, filterType, filterSeverity, sortField, sortDirection])

    const handleSort = (field: SortField) => {
        if (sortField === field) {
            setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'))
        } else {
            setSortField(field)
            setSortDirection('desc')
        }
    }

    // Paginate after filtering/sorting so filters apply to the full list
    const startIndex = page * PAGE_SIZE
    const pageEvents = sortedAndFilteredEvents.slice(startIndex, startIndex + PAGE_SIZE)
    const totalPages = Math.max(1, Math.ceil(sortedAndFilteredEvents.length / PAGE_SIZE))

    if (loading) {
        return (
            <div className="flex justify-center items-center flex-1 p-8">
                <p className="text-slate-400">Chargement de l'historique...</p>
            </div>
        )
    }

    if (error) {
        return (
            <div className="flex justify-center items-center flex-1 p-8">
                <p className="text-rose-400">{error}</p>
            </div>
        )
    }

    return (
        <div className="flex-1 p-6 overflow-auto">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-6">
                    <h1 className="text-2xl font-bold text-white">Historique des incidents</h1>
                    <div className="flex items-center gap-3">
                        <label htmlFor="filter-type" className="text-slate-400 text-sm">Filtrer par type :</label>
                        <select
                            id="filter-type"
                            value={filterType}
                            onChange={(e) => setFilterType(e.target.value)}
                            className="bg-slate-800/80 px-3 py-2 border border-slate-700 rounded-lg text-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-cyan-500/40"
                        >
                            <option value="all">Tous les types</option>
                            {eventTypes.map((type) => (
                                <option key={type} value={type}>
                                    {type}
                                </option>
                            ))}
                        </select>
                        <label htmlFor="filter-severity" className="text-slate-400 text-sm">Severity:</label>
                        <select
                            id="filter-severity"
                            value={filterSeverity}
                            onChange={(e) => setFilterSeverity(e.target.value as typeof filterSeverity)}
                            className="bg-slate-800/80 px-3 py-2 border border-slate-700 rounded-lg text-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-cyan-500/40"
                        >
                            <option value="all">All</option>
                            <option value="low">Low (1-2)</option>
                            <option value="medium">Medium (3)</option>
                            <option value="high">High (4-5)</option>
                        </select>
                    </div>
                </div>

                <div className="bg-slate-900/50 border border-slate-800 rounded-xl overflow-hidden">
                    <table className="w-full">
                        <thead>
                            <tr className="border-b border-slate-700/50">
                                <th className="px-4 py-3 text-left">
                                    <button
                                        type="button"
                                        onClick={() => handleSort('date')}
                                        className="flex items-center gap-2 text-slate-300 text-xs font-semibold uppercase tracking-wider hover:text-white transition-colors"
                                    >
                                        Date et heure
                                        <SortIcon field="date" currentField={sortField} direction={sortDirection} />
                                    </button>
                                </th>
                                <th className="px-4 py-3 text-left">
                                    <button
                                        type="button"
                                        onClick={() => handleSort('type')}
                                        className="flex items-center gap-2 text-slate-300 text-xs font-semibold uppercase tracking-wider hover:text-white transition-colors"
                                    >
                                        Type
                                        <SortIcon field="type" currentField={sortField} direction={sortDirection} />
                                    </button>
                                </th>
                                <th className="px-4 py-3 text-left">
                                    <span className="text-slate-300 text-xs font-semibold uppercase tracking-wider">
                                        Titre
                                    </span>
                                </th>
                                <th className="px-4 py-3 text-left">
                                    <button
                                        type="button"
                                        onClick={() => handleSort('severity')}
                                        className="flex items-center gap-2 text-slate-300 text-xs font-semibold uppercase tracking-wider hover:text-white transition-colors"
                                    >
                                        Criticité
                                        <SortIcon field="severity" currentField={sortField} direction={sortDirection} />
                                    </button>
                                </th>
                                <th className="px-4 py-3 text-left">
                                    <button
                                        type="button"
                                        onClick={() => handleSort('duration')}
                                        className="flex items-center gap-2 text-slate-300 text-xs font-semibold uppercase tracking-wider hover:text-white transition-colors"
                                    >
                                        Durée
                                        <SortIcon field="duration" currentField={sortField} direction={sortDirection} />
                                    </button>
                                </th>
                                <th className="px-4 py-3 text-left">
                                    <span className="text-slate-300 text-xs font-semibold uppercase tracking-wider">
                                        Statut
                                    </span>
                                </th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-800/50">
                            {pageEvents.map((event) => (
                                <tr key={event.id} className="hover:bg-slate-800/30 transition-colors">
                                    <td className="px-4 py-3">
                                        <div className="text-white text-sm">{formatDate(event.reported_at)}</div>
                                        <div className="text-slate-400 text-xs">{formatTime(event.reported_at)}</div>
                                    </td>
                                    <td className="px-4 py-3">
                                        <div className="flex items-center gap-2">
                                            <div
                                                className="w-6 h-6 flex-shrink-0"
                                                dangerouslySetInnerHTML={{
                                                    __html: getEventIconWithSeverity(event.event_type_code, event.severity),
                                                }}
                                            />
                                            <span className="text-slate-300 text-sm">{event.event_type_name || event.event_type_code}</span>
                                        </div>
                                    </td>
                                    <td className="px-4 py-3">
                                        <span className="text-white text-sm">{event.title}</span>
                                    </td>
                                    <td className="px-4 py-3">
                                        <span className="text-cyan-300/90 text-xs uppercase tracking-widest">
                                            {severityLabel(event.severity)}
                                        </span>
                                    </td>
                                    <td className="px-4 py-3">
                                        <span className="text-slate-300 text-sm">
                                            {calculateDuration(event)}
                                        </span>
                                    </td>
                                    <td className="px-4 py-3">
                                        <span
                                            className={`inline-flex px-2 py-1 rounded-full text-xs font-medium ${getStatusStyle(event.intervention_status)}`}
                                        >
                                            {event.intervention_status ?? 'unknown'}
                                        </span>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    {sortedAndFilteredEvents.length === 0 && (
                        <div className="p-8 text-center text-slate-400">
                            Aucun incident trouvé
                        </div>
                    )}
                </div>

                <div className="mt-4 flex items-center justify-between text-slate-500 text-sm">
                    <span>
                        Page {page + 1} / {totalPages} • Affichage de {pageEvents.length} sur {sortedAndFilteredEvents.length} incidents
                    </span>
                    <div className="flex items-center gap-2">
                        <button
                            type="button"
                            onClick={() => setPage((p) => Math.max(p - 1, 0))}
                            disabled={page === 0 || loading}
                            className="px-3 py-1.5 rounded border border-slate-700 bg-slate-800 text-slate-200 disabled:opacity-50 disabled:cursor-not-allowed hover:bg-slate-700 transition-colors"
                        >
                            Précédent
                        </button>
                        <button
                            type="button"
                            onClick={() => setPage((p) => Math.min(p + 1, totalPages - 1))}
                            disabled={page >= totalPages - 1 || loading}
                            className="px-3 py-1.5 rounded border border-slate-700 bg-slate-800 text-slate-200 disabled:opacity-50 disabled:cursor-not-allowed hover:bg-slate-700 transition-colors"
                        >
                            Suivant
                        </button>
                    </div>
                </div>

            </div>
        </div>
    )
}
