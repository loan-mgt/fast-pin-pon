import type { JSX } from 'react'
import { Card } from '../ui/card'
import type { EventSummary } from '../../types'
import type { Permissions } from '../../auth/AuthProvider'
import { formatTimestamp, severityLabel } from '../../utils/format'

interface EventDetailPanelProps {
  readonly event: EventSummary | null
  readonly onClose: () => void
  readonly permissions?: Permissions
}

export function EventDetailPanel({ event, onClose, permissions }: EventDetailPanelProps): JSX.Element | null {
  if (!event) return null

  const canAssign = permissions?.canAssignUnits ?? false
  const canDelete = permissions?.canDeleteIncident ?? false
  const assignedUnits = event.assigned_units ?? []

  return (
    <Card
      className="z-20 fixed top-[72px] left-0 shadow-2xl shadow-slate-950/60 p-3 rounded-none w-[360px] max-w-[90vw] max-h-[calc(100vh-96px)] overflow-y-auto"
    >
      <div className="flex justify-between items-start gap-2">
        <div className="space-y-1">
          <p className="text-sky-300/80 text-xs uppercase tracking-wide">Incident</p>
          <h2 className="font-bold text-white text-lg leading-tight">{event.title}</h2>
          <p className="text-slate-300/80 text-xs leading-snug">{event.description ?? 'No description'}</p>
        </div>
        <button
          type="button"
          className="px-2 py-1 text-slate-300 hover:text-white transition-colors"
          aria-label="Close incident details"
          onClick={onClose}
        >
          ✕
        </button>
      </div>

      <div className="gap-2 grid grid-cols-2 mt-3 text-slate-200/90 text-xs">
        <div className="bg-slate-800/60 px-2 py-1.5 border border-blue-500/20 rounded-md">
          <p className="text-[0.7rem] text-slate-400 uppercase tracking-wide">Severity</p>
          <p className="font-semibold text-white">{severityLabel(event.severity)}</p>
        </div>
        <div className="bg-slate-800/60 px-2 py-1.5 border border-blue-500/20 rounded-md">
          <p className="text-[0.7rem] text-slate-400 uppercase tracking-wide">Status</p>
          <p className="font-semibold text-white">{event.intervention_status || 'Unknown'}</p>
        </div>
        <div className="col-span-2 bg-slate-800/60 px-2 py-1.5 border border-blue-500/20 rounded-md">
          <p className="text-[0.7rem] text-slate-400 uppercase tracking-wide">Reported</p>
          <p className="font-semibold text-white">{formatTimestamp(event.reported_at)}</p>
        </div>
      </div>

      <div className="space-y-1 mt-3 text-slate-200/90 text-sm">
        {event.address ? <p className="font-semibold">{event.address}</p> : null}
        {event.location ? (
          <p className="text-slate-400 text-xs">
            {event.location.latitude.toFixed(5)}, {event.location.longitude.toFixed(5)}
          </p>
        ) : null}
      </div>

      <div className="mt-4 flex flex-wrap gap-2 text-sm">
        <button
          type="button"
          className={`px-3 py-1.5 rounded-md border transition-colors ${
            canAssign
              ? 'bg-blue-600/80 hover:bg-blue-500 text-white border-blue-500'
              : 'bg-slate-800/60 text-slate-500 border-slate-700 cursor-not-allowed'
          }`}
          disabled={!canAssign}
          aria-disabled={!canAssign}
        >
          Assigner une unité
        </button>
        <button
          type="button"
          className={`px-3 py-1.5 rounded-md border transition-colors ${
            canDelete
              ? 'bg-rose-600/80 hover:bg-rose-500 text-white border-rose-500'
              : 'bg-slate-800/60 text-slate-500 border-slate-700 cursor-not-allowed'
          }`}
          disabled={!canDelete}
          aria-disabled={!canDelete}
        >
          Supprimer l'incident
        </button>
      </div>

      <div className="mt-5">
        <div className="flex items-center justify-between gap-2">
          <p className="text-sm font-semibold text-white">Unités assignées</p>
          <span className="text-[0.7rem] text-slate-300 bg-slate-800/70 border border-slate-700 rounded-full px-2 py-0.5">
            {assignedUnits.length}
          </span>
        </div>
        <div className="mt-2 max-h-60 overflow-y-auto space-y-2">
          {assignedUnits.length === 0 ? (
            <p className="text-xs text-slate-400">Aucune unité assignée pour le moment.</p>
          ) : (
            assignedUnits.map((unit) => (
              <article
                key={unit.id}
                className="bg-slate-800/60 border border-slate-700 rounded-lg px-3 py-2 flex items-start justify-between gap-2"
              >
                <div className="space-y-1 min-w-0">
                  <p className="text-sm font-semibold text-white leading-tight truncate">{unit.call_sign}</p>
                  <p className="text-[0.75rem] text-slate-300 leading-tight truncate">
                    {unit.unit_type_code} • {unit.home_base}
                  </p>
                  <p className="text-[0.7rem] text-slate-400 leading-tight truncate">{unit.status}</p>
                </div>
                <span className="text-[0.65rem] text-cyan-200 bg-cyan-500/15 border border-cyan-500/30 rounded-full px-2 py-1 self-center">
                  {unit.microbit_id ?? '—'}
                </span>
              </article>
            ))
          )}
        </div>
      </div>
    </Card>
  )
}
