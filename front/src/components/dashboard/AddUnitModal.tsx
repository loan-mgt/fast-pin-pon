import type { JSX, FormEvent } from 'react'
import { useEffect, useState, useMemo } from 'react'

import type { UnitType, Building } from '../../types'
import { Card } from '../ui/card'
import { Button } from '../ui/button'

interface AddUnitModalProps {
  isOpen: boolean
  onClose: () => void
  onSubmit: (callSign: string, unitTypeCode: string, homeBase: string, lat: number, lon: number) => Promise<void>
  unitTypes: UnitType[]
  buildings: Building[]
}

export function AddUnitModal({
  isOpen,
  onClose,
  onSubmit,
  unitTypes,
  buildings,
}: Readonly<AddUnitModalProps>): JSX.Element | null {
  const [callSign, setCallSign] = useState('')
  const [unitTypeCode, setUnitTypeCode] = useState('')
  const [selectedBuilding, setSelectedBuilding] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Memoize stations to avoid recreating on every render
  const stations = useMemo(() => buildings.filter((b) => b.type === 'station'), [buildings])

  // Only reset form when modal opens (isOpen changes from false to true)
  useEffect(() => {
    if (isOpen) {
      setCallSign('')
      setUnitTypeCode(unitTypes[0]?.code ?? '')
      setSelectedBuilding(stations[0]?.id ?? '')
      setError(null)
      setIsSubmitting(false)
    }
  }, [isOpen]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    if (!callSign.trim()) {
      setError('Le nom de l\'unité est requis')
      return
    }
    if (!unitTypeCode) {
      setError('Le type d\'unité est requis')
      return
    }
    if (!selectedBuilding) {
      setError('La caserne est requise')
      return
    }

    const building = stations.find((b) => b.id === selectedBuilding)
    if (!building) {
      setError('Caserne introuvable')
      return
    }

    setError(null)
    setIsSubmitting(true)

    try {
      await onSubmit(
        callSign.trim(),
        unitTypeCode,
        building.name,
        building.location.latitude,
        building.location.longitude,
      )
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erreur inconnue')
    } finally {
      setIsSubmitting(false)
    }
  }

  if (!isOpen) return null

  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50 px-4">
      <Card className="w-full max-w-md p-4 bg-slate-900">
        <div className="flex items-start justify-between gap-3 mb-4">
          <div>
            <p className="text-cyan-300/70 text-xs uppercase tracking-[0.35em]">Nouvelle unité</p>
            <h2 className="text-xl font-semibold text-white">Ajouter une unité</h2>
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

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="callSign" className="block text-sm font-medium text-slate-300 mb-1">
              Indicatif (Call Sign)
            </label>
            <input
              id="callSign"
              type="text"
              value={callSign}
              onChange={(e) => setCallSign(e.target.value)}
              className="w-full px-3 py-2 rounded bg-slate-800 border border-slate-700 text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
              placeholder="Ex: U21"
              disabled={isSubmitting}
            />
          </div>

          <div>
            <label htmlFor="unitType" className="block text-sm font-medium text-slate-300 mb-1">
              Type d'unité
            </label>
            <select
              id="unitType"
              value={unitTypeCode}
              onChange={(e) => setUnitTypeCode(e.target.value)}
              className="w-full px-3 py-2 rounded bg-slate-800 border border-slate-700 text-white focus:border-cyan-500 focus:outline-none"
              disabled={isSubmitting}
            >
              {unitTypes.map((ut) => (
                <option key={ut.code} value={ut.code}>
                  {ut.code} - {ut.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="homeBase" className="block text-sm font-medium text-slate-300 mb-1">
              Caserne
            </label>
            <select
              id="homeBase"
              value={selectedBuilding}
              onChange={(e) => setSelectedBuilding(e.target.value)}
              className="w-full px-3 py-2 rounded bg-slate-800 border border-slate-700 text-white focus:border-cyan-500 focus:outline-none"
              disabled={isSubmitting}
            >
              {stations.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                </option>
              ))}
            </select>
          </div>

          {error && (
            <p className="text-sm text-red-400">{error}</p>
          )}

          <div className="flex gap-2 justify-end pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={onClose}
              disabled={isSubmitting}
            >
              Annuler
            </Button>
            <Button
              type="submit"
              disabled={isSubmitting}
            >
              {isSubmitting ? 'Création...' : 'Créer l\'unité'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
