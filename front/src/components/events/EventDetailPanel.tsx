import type { JSX } from 'react'
import { Card } from '../ui/card'
import type { EventSummary } from '../../types'
import { formatTimestamp, severityLabel } from '../../utils/format'

interface EventDetailPanelProps {
  readonly event: EventSummary | null
  readonly onClose: () => void
}

export function EventDetailPanel({ event, onClose }: EventDetailPanelProps): JSX.Element | null {
  if (!event) return null

  return (
    <Card
      className="z-20 fixed top-[72px] left-0 shadow-2xl shadow-slate-950/60 p-3 rounded-none w-[360px] max-w-[90vw]"
    >
      <div className="flex justify-between items-start gap-2">
        <div className="space-y-1">
          <p className="text-xs uppercase tracking-wide text-sky-300/80">Incident</p>
          <h2 className="text-lg font-bold text-white leading-tight">{event.title}</h2>
          <p className="text-xs text-slate-300/80 leading-snug">{event.description ?? 'No description'}</p>
        </div>
        <button
          type="button"
          className="text-slate-300 hover:text-white transition-colors px-2 py-1"
          aria-label="Close incident details"
          onClick={onClose}
        >
          ✕
        </button>
      </div>

      <div className="mt-3 grid grid-cols-2 gap-2 text-xs text-slate-200/90">
        <div className="bg-slate-800/60 border border-blue-500/20 rounded-md px-2 py-1.5">
          <p className="text-[0.7rem] uppercase tracking-wide text-slate-400">Severity</p>
          <p className="font-semibold text-white">{severityLabel(event.severity)}</p>
        </div>
        <div className="bg-slate-800/60 border border-blue-500/20 rounded-md px-2 py-1.5">
          <p className="text-[0.7rem] uppercase tracking-wide text-slate-400">Status</p>
          <p className="font-semibold text-white">{event.status || 'Unknown'}</p>
        </div>
        <div className="bg-slate-800/60 border border-blue-500/20 rounded-md px-2 py-1.5 col-span-2">
          <p className="text-[0.7rem] uppercase tracking-wide text-slate-400">Reported</p>
          <p className="font-semibold text-white">{formatTimestamp(event.reported_at)}</p>
        </div>
      </div>

      <div className="mt-3 space-y-1 text-sm text-slate-200/90">
        {event.address ? <p className="font-semibold">{event.address}</p> : null}
        {event.location ? (
          <p className="text-xs text-slate-400">
            {event.location.latitude.toFixed(5)}, {event.location.longitude.toFixed(5)}
          </p>
        ) : null}
      </div>
    </Card>
  )
}
