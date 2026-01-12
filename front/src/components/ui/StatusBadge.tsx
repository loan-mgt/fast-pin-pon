import type { JSX } from 'react'

export type StatusType = 'unit' | 'intervention'

interface StatusBadgeProps {
    status: string
    type?: StatusType
    className?: string
    showDot?: boolean
}

const UNIT_STATUS_COLORS: Record<string, string> = {
    available: 'bg-emerald-500/20 text-emerald-300 border-emerald-400/30',
    available_hidden: 'bg-indigo-500/20 text-indigo-300 border-indigo-400/30',
    under_way: 'bg-blue-500/15 text-blue-200 border-blue-300/30',
    on_site: 'bg-amber-500/15 text-amber-200 border-amber-300/30',
    unavailable: 'bg-purple-500/15 text-purple-200 border-purple-300/30',
    offline: 'bg-slate-600/20 text-slate-200 border-slate-300/20',
}

const INTERVENTION_STATUS_COLORS: Record<string, string> = {
    created: 'bg-gray-500/20 text-gray-300 border-gray-400/30',
    on_site: 'bg-amber-500/15 text-amber-200 border-amber-300/30',
    completed: 'bg-emerald-500/20 text-emerald-300 border-emerald-400/30',
    cancelled: 'bg-red-500/15 text-red-200 border-red-300/30',
}

const normalizeStatus = (status: string) => status?.toLowerCase().replaceAll(/[-\s]/g, '_') ?? ''

export function StatusBadge({ status, type = 'unit', className = '', showDot = true }: Readonly<StatusBadgeProps>): JSX.Element {
    const normalized = normalizeStatus(status)

    let colors = 'bg-slate-600/20 text-slate-200 border-slate-300/20'
    if (type === 'unit') {
        colors = UNIT_STATUS_COLORS[normalized] ?? UNIT_STATUS_COLORS.offline
    } else if (type === 'intervention') {
        colors = INTERVENTION_STATUS_COLORS[normalized] ?? 'bg-slate-600/20 text-slate-200 border-slate-300/20'
    }

    const formatStatus = (s: string) => {
        if (s === 'available_hidden') return 'home base'
        return s.replaceAll(/[-\s_]/g, ' ')
    }

    return (
        <span className={`inline-flex items-center gap-1 px-2 py-1 text-[11px] font-semibold border rounded-full ${colors} ${className}`}>
            {showDot && (
                <span className="inline-flex w-2 h-2 rounded-full bg-current opacity-80" aria-hidden="true" />
            )}
            <span className="truncate">{formatStatus(status)}</span>
        </span>
    )
}
