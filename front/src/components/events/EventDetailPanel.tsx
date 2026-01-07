import { useCallback, useState } from 'react'
import type { JSX } from 'react'
import { Card } from '../ui/card'
import { ConfirmationDialog } from '../ui/confirmation-dialog'
import { UnitAssignmentDialog } from './UnitAssignmentDialog'
import type { EventSummary } from '../../types'
import type { Permissions } from '../../auth/AuthProvider'
import { formatTimestamp, severityLabel } from '../../utils/format'
import { fastPinPonService } from '../../services/FastPinPonService'
import { useAuth } from '../../auth/AuthProvider'

interface EventDetailPanelProps {
  readonly event: EventSummary | null
  readonly onClose: () => void
  readonly onEventSelect?: (eventId: string) => void
  readonly permissions?: Permissions
  readonly onRefresh?: () => Promise<void> | void
  readonly onTogglePauseRefresh?: (paused: boolean) => void
  readonly onLocateEvent?: (lng: number, lat: number) => void
}

export function EventDetailPanel({ event, onClose, onEventSelect, permissions, onRefresh, onTogglePauseRefresh, onLocateEvent }: EventDetailPanelProps): JSX.Element | null {
  const { token } = useAuth()
  const [isDeleting, setIsDeleting] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [showAssignmentDialog, setShowAssignmentDialog] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [unitToUnassign, setUnitToUnassign] = useState<{ id: string, callSign: string } | null>(null)

  const canAssign = permissions?.canAssignUnits ?? false
  const canDelete = permissions?.canDeleteIncident ?? false
  const assignedUnits = event?.assigned_units ?? []

  const handleDeleteIncident = useCallback(async () => {
    if (!event || !canDelete || isDeleting) return
    if (!event.intervention_id) return

    setShowConfirm(true)
  }, [canDelete, event, isDeleting])

  const confirmDelete = useCallback(async () => {
    if (!event?.intervention_id) return

    setShowConfirm(false)
    setIsDeleting(true)
    try {
      await fastPinPonService.updateInterventionStatus(event.intervention_id, 'completed', token ?? undefined)

      if (onRefresh) {
        await onRefresh()
      }
      onClose()
    } catch (err) {
      console.error('Failed to complete incident', err)
      setErrorMessage('Impossible de clore l\'incident. Veuillez réessayer.')
    } finally {
      setIsDeleting(false)
    }
  }, [event, onClose, token, onRefresh])

  const confirmUnassign = useCallback(async () => {
    if (!event?.intervention_id || !unitToUnassign) return

    const { id: unitId } = unitToUnassign
    setUnitToUnassign(null)
    
    try {
      await fastPinPonService.unassignUnitFromIntervention(event.intervention_id, unitId, token ?? undefined)
      if (onRefresh) {
        await onRefresh()
      }
    } catch (err) {
      console.error('Failed to unassign unit', err)
      setErrorMessage('Impossible de libérer l\'unité. Veuillez réessayer.')
    }
  }, [event, unitToUnassign, token, onRefresh])

  if (!event) return null

  return (
    <>
      <Card
        className="top-[72px] left-0 z-20 fixed shadow-2xl shadow-slate-950/60 p-3 rounded-none w-[360px] max-w-[90vw] max-h-[calc(100vh-96px)] overflow-y-auto"
      >
        <div className="flex justify-between items-start gap-2">
          <div className="space-y-1">
            <p className="text-sky-300/80 text-xs uppercase tracking-wide">Incident</p>
            <h2 className="font-bold text-white text-lg leading-tight">{event.title}</h2>
            <p className="text-slate-300/80 text-xs leading-snug">{event.description ?? 'No description'}</p>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="hover:bg-sky-500/10 p-2 border border-sky-500/40 rounded-md text-sky-200 transition-colors cursor-pointer"
              aria-label="Centrer sur l'incident"
              title="Centrer sur l'incident"
              onClick={() => {
                if (event.location?.longitude && event.location?.latitude) {
                  onLocateEvent?.(event.location.longitude, event.location.latitude)
                }
              }}
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <circle cx="12" cy="12" r="10" />
                <circle cx="12" cy="12" r="6" />
                <circle cx="12" cy="12" r="2" />
                <line x1="12" y1="2" x2="12" y2="4" />
                <line x1="12" y1="20" x2="12" y2="22" />
                <line x1="2" y1="12" x2="4" y2="12" />
                <line x1="20" y1="12" x2="22" y2="12" />
              </svg>
            </button>
            <button
              type="button"
              className={`p-2 rounded-md border transition-colors ${
                canDelete
                  ? 'border-rose-500/40 text-rose-200 hover:bg-rose-500/10 cursor-pointer'
                  : 'border-slate-700 text-slate-500 cursor-not-allowed'
              }`}
              aria-label="Supprimer l'incident"
              title="Supprimer l'incident"
              disabled={!canDelete || isDeleting}
              onClick={handleDeleteIncident}
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <polyline points="3 6 5 6 21 6" />
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
                <path d="M10 11v6" />
                <path d="M14 11v6" />
                <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
              </svg>
            </button>
            <button
              type="button"
              className="p-2 text-slate-300 hover:text-white transition-colors cursor-pointer"
              aria-label="Close incident details"
              onClick={onClose}
            >
              ✕
            </button>
          </div>
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
        </div>

        <div className="mt-5">
          <div className="flex justify-between items-center gap-2">
            <div className="flex items-center gap-2">
              <p className="font-semibold text-white text-sm">Unités assignées</p>
              <span className="bg-slate-800/70 px-2 py-0.5 border border-slate-700 rounded-full text-[0.7rem] text-slate-300">
                {assignedUnits.length}
              </span>
            </div>
            <button
              type="button"
              className={`p-2 rounded-md border transition-colors ${
                canAssign
                  ? 'border-blue-500/40 text-blue-200 hover:bg-blue-500/10 cursor-pointer'
                  : 'border-slate-700 text-slate-500 cursor-not-allowed'
              }`}
              aria-label="Assigner une unité"
              title="Assigner une unité"
              disabled={!canAssign}
              onClick={() => setShowAssignmentDialog(true)}
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <line x1="12" y1="5" x2="12" y2="19" />
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
            </button>
          </div>
          <div className="space-y-2 mt-2 max-h-60 overflow-y-auto">
            {assignedUnits.length === 0 ? (
              <p className="text-slate-400 text-xs">Aucune unité assignée pour le moment.</p>
            ) : (
              assignedUnits.map((unit) => (
                <article
                  key={unit.id}
                  className="flex justify-between items-start gap-2 bg-slate-800/60 hover:bg-slate-800/80 px-3 py-2 border border-slate-700 rounded-lg transition-colors cursor-pointer"
                  onClick={() => onEventSelect?.(event.id)}
                >
                  <div className="space-y-1 min-w-0">
                    <p className="font-semibold text-white text-sm truncate leading-tight">{unit.call_sign}</p>
                    <p className="text-[0.75rem] text-slate-300 truncate leading-tight">
                      {unit.unit_type_code} • {unit.home_base}
                    </p>
                    <p className="text-[0.7rem] text-slate-400 truncate leading-tight">{unit.status}</p>
                  </div>
                  {canAssign && (
                    <button
                      type="button"
                      className="bg-rose-500/20 hover:bg-rose-500/40 p-1.5 border border-rose-500/30 rounded-full h-fit text-rose-300 transition-colors cursor-pointer"
                      title="Désassigner l'unité"
                      onClick={() => setUnitToUnassign({ id: unit.id, callSign: unit.call_sign })}
                    >
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        width="14"
                        height="14"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      >
                        <line x1="18" y1="6" x2="6" y2="18" />
                        <line x1="6" y1="6" x2="18" y2="18" />
                      </svg>
                    </button>
                  )}
                </article>
              ))
            )}
          </div>
        </div>
      </Card>

      <ConfirmationDialog
        isOpen={showConfirm}
        title="Fermer l'incident ?"
        description={`Êtes-vous sûr de vouloir clore l'incident "${event.title}" ? Cette action marquera l'intervention comme terminée.`}
        confirmLabel="Clore l'incident"
        cancelLabel="Annuler"
        variant="danger"
        isSubmitting={isDeleting}
        onConfirm={confirmDelete}
        onCancel={() => setShowConfirm(false)}
      />

      <ConfirmationDialog
        isOpen={unitToUnassign !== null}
        title="Libérer l'unité ?"
        description={`Êtes-vous sûr de vouloir retirer l'unité "${unitToUnassign?.callSign}" de cette intervention ? L'unité redeviendra disponible.`}
        confirmLabel="Libérer"
        cancelLabel="Annuler"
        variant="danger"
        onConfirm={confirmUnassign}
        onCancel={() => setUnitToUnassign(null)}
      />

      <ConfirmationDialog
        isOpen={!!errorMessage}
        title="Erreur"
        description={errorMessage ?? ''}
        confirmLabel="OK"
        onConfirm={() => setErrorMessage(null)}
      />

      <UnitAssignmentDialog
        isOpen={showAssignmentDialog}
        event={event}
        onClose={() => setShowAssignmentDialog(false)}
        onRefresh={onRefresh}
        onTogglePauseRefresh={onTogglePauseRefresh}
      />
    </>
  )
}

