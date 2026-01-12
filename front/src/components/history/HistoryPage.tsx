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

function calculateDuration(startedAt?: string, completedAt?: string): string {
    if (!startedAt || !completedAt) return '—'

    const start = new Date(startedAt).getTime()
    const end = new Date(completedAt).getTime()
    const durationMs = end - start

    if (durationMs < 0) return '—'

    const minutes = Math.floor(durationMs / (1000 * 60))
    const hours = Math.floor(minutes / 60)
    const remainingMinutes = minutes % 60

    if (hours > 0) {
        return `${hours}h ${remainingMinutes}min`
    }
    return `${minutes}min`
}

function getDurationMinutes(startedAt?: string, completedAt?: string): number {
    if (!startedAt || !completedAt) return Number.MAX_SAFE_INTEGER
    const start = new Date(startedAt).getTime()
    const end = new Date(completedAt).getTime()
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
    const [events, setEvents] = useState<EventSummary[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [sortField, setSortField] = useState<SortField>('date')
    const [sortDirection, setSortDirection] = useState<SortDirection>('desc')
    const [filterType, setFilterType] = useState<string>('all')

    useEffect(() => {
        const fetchHistory = async () => {
            setLoading(true)
            setError(null)
            try {
                // Fetch all events including completed ones
                const data = await fastPinPonService.getEvents(100, token ?? undefined)
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
            filtered = events.filter((e) => e.event_type_code === filterType)
        }

        return [...filtered].sort((a, b) => {
            let comparison = 0
            switch (sortField) {
                case 'date':
                    comparison = new Date(a.reported_at).getTime() - new Date(b.reported_at).getTime()
                    break
                case 'type':
                    comparison = a.event_type_code.localeCompare(b.event_type_code)
                    break
                case 'severity':
                    comparison = a.severity - b.severity
                    break
                case 'duration':
                    comparison = getDurationMinutes(a.started_at, a.completed_at) - getDurationMinutes(b.started_at, b.completed_at)
                    break
            }
            return sortDirection === 'asc' ? comparison : -comparison
        })
    }, [events, filterType, sortField, sortDirection])

    const handleSort = (field: SortField) => {
        if (sortField === field) {
            setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'))
        } else {
            setSortField(field)
            setSortDirection('desc')
        }
    }

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
                            {sortedAndFilteredEvents.map((event) => (
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
                                            {calculateDuration(event.started_at, event.completed_at)}
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

                <div className="mt-4 text-slate-500 text-sm">
                    Affichage de {sortedAndFilteredEvents.length} sur {events.length} incidents
                </div>
            </div>
        </div>
    )
}
