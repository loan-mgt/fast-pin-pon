import type { JSX, FormEvent } from 'react'
import { useEffect, useState } from 'react'

import type { UnitSummary, Building } from '../../types'
import { Card } from '../ui/card'
import { Button } from '../ui/button'

type UnitStatus = 'available' | 'available_hidden' | 'under_way' | 'on_site' | 'unavailable' | 'offline'

const STATUS_OPTIONS: { value: UnitStatus; label: string }[] = [
  { value: 'available', label: 'Disponible' },
  { value: 'available_hidden', label: 'Disponible (caché)' },
  { value: 'under_way', label: 'En route' },
  { value: 'on_site', label: 'Sur site' },
  { value: 'unavailable', label: 'Indisponible' },
  { value: 'offline', label: 'Hors ligne' },
]

interface EditUnitModalProps {
  isOpen: boolean
  onClose: () => void
  onSubmit: (updates: { status?: UnitStatus; locationId?: string | null }) => Promise<void> | void
  unit: UnitSummary | null
  buildings: Building[]
}

export function EditUnitModal({
  isOpen,
  onClose,
  onSubmit,
  unit,
  buildings,
}: Readonly<EditUnitModalProps>): JSX.Element | null {
  const [status, setStatus] = useState<UnitStatus>('available')
  const [locationId, setLocationId] = useState<string>('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (isOpen && unit) {
      setStatus((unit.status as UnitStatus) ?? 'available')
      setLocationId(unit.location_id ?? '')
      setError(null)
      setIsSubmitting(false)
    }
  }, [isOpen, unit])

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    const updates: { status?: UnitStatus; locationId?: string | null } = {}
    
    if (status !== unit?.status) {
      updates.status = status
    }
    
    const newLocationId = locationId || null
    if (newLocationId !== (unit?.location_id ?? null)) {
      updates.locationId = newLocationId
    }

    if (Object.keys(updates).length === 0) {
      onClose()
      return
    }

    try {
      await onSubmit(updates)
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erreur inconnue')
    } finally {
      setIsSubmitting(false)
    }
  }

  if (!isOpen || !unit) return null

  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50 px-4">
      <Card className="w-full max-w-md p-4 bg-slate-900">
        <div className="flex items-start justify-between gap-3 mb-3">
          <div>
            <p className="text-cyan-300/70 text-xs uppercase tracking-[0.35em]">Modification</p>
            <h2 className="text-xl font-semibold text-white">Modifier l'unité</h2>
            <p className="text-sm text-slate-400">Unité : {unit.call_sign}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-white text-2xl leading-none"
            aria-label="Fermer"
          >
            ×
          </button>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          <label className="flex flex-col gap-2 text-sm text-slate-200">
            <span>Statut</span>
            <select
              className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-cyan-500"
              value={status}
              onChange={(e) => setStatus(e.target.value as UnitStatus)}
            >
              {STATUS_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </label>

          <label className="flex flex-col gap-2 text-sm text-slate-200">
            <span>Caserne</span>
            <select
              className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-cyan-500"
              value={locationId}
              onChange={(e) => setLocationId(e.target.value)}
            >
              <option value="">Aucune caserne</option>
              {buildings.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                </option>
              ))}
            </select>
          </label>

          {error && (
            <p className="text-red-400 text-sm">{error}</p>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={onClose}
              disabled={isSubmitting}
            >
              Annuler
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Enregistrement...' : 'Enregistrer'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
