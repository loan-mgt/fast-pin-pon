import type { JSX, FormEvent } from 'react'
import { useEffect, useState } from 'react'

import { Card } from '../ui/card'
import { Button } from '../ui/button'

interface AssignMicrobitModalProps {
  isOpen: boolean
  onClose: () => void
  onSubmit: (microbitId: string) => Promise<void> | void
  microbitOptions: string[]
  unitCallSign?: string
  initialSelection?: string
}

export function AssignMicrobitModal({
  isOpen,
  onClose,
  onSubmit,
  microbitOptions,
  unitCallSign,
  initialSelection,
}: Readonly<AssignMicrobitModalProps>): JSX.Element | null {
  const [selected, setSelected] = useState<string>('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const hasOptions = microbitOptions.length > 0

  useEffect(() => {
    if (isOpen) {
      const defaultValue = initialSelection && microbitOptions.includes(initialSelection)
        ? initialSelection
        : microbitOptions[0] ?? ''
      setSelected(defaultValue)
      setError(null)
      setIsSubmitting(false)
      return
    }

    if (!isOpen) {
      setSelected('')
      setError(null)
      setIsSubmitting(false)
    }
  }, [isOpen, initialSelection, microbitOptions])

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!selected) {
      setError('Choisissez un microbit')
      return
    }
    setError(null)
    setIsSubmitting(true)
    try {
      await onSubmit(selected)
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
        <div className="flex items-start justify-between gap-3 mb-3">
          <div>
            <p className="text-cyan-300/70 text-xs uppercase tracking-[0.35em]">Assignation</p>
            <h2 className="text-xl font-semibold text-white">Assigner un microbit</h2>
            {unitCallSign && <p className="text-sm text-slate-400">Unité : {unitCallSign}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-white"
            aria-label="Fermer"
          >
            ×
          </button>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          <label className="flex flex-col gap-2 text-sm text-slate-200">
            <span>Microbit connecté</span>
            <select
              className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-cyan-500"
              value={selected}
              onChange={(e) => setSelected(e.target.value)}
              disabled={!hasOptions}
            >
              <option value="">Sélectionner…</option>
              {microbitOptions.map((id) => (
                <option key={id} value={id}>
                  {id}
                </option>
              ))}
            </select>
          </label>

          {error && <p className="text-sm text-red-400">{error}</p>}

          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" className="px-3" onClick={onClose}>
              Annuler
            </Button>
            <Button type="submit" disabled={isSubmitting || !hasOptions}>
              {isSubmitting ? 'Assignation…' : 'Assigner'}
            </Button>
          </div>
        </form>
        {!hasOptions && (
          <p className="mt-2 text-xs text-amber-300">
            Aucun microbit disponible. Vérifiez la configuration côté serveur.
          </p>
        )}
      </Card>
    </div>
  )
}