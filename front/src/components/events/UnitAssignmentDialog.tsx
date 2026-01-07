import { useState, useEffect, useCallback } from 'react'
import type { JSX } from 'react'
import { Card } from '../ui/card'
import { Button } from '../ui/button'
import type { EventSummary, UnitSummary, UnitType, EventType } from '../../types'
import { fastPinPonService } from '../../services/FastPinPonService'
import { useAuth } from '../../auth/AuthProvider'

interface UnitAssignmentDialogProps {
  readonly isOpen: boolean
  readonly event: EventSummary
  readonly onClose: () => void
  readonly onRefresh?: () => Promise<void> | void
  readonly onTogglePauseRefresh?: (paused: boolean) => void
}

export function UnitAssignmentDialog({ isOpen, event, onClose, onRefresh, onTogglePauseRefresh }: UnitAssignmentDialogProps): JSX.Element | null {
  const { token } = useAuth()
  const [unitTypes, setUnitTypes] = useState<UnitType[]>([])
  const [nearbyUnits, setNearbyUnits] = useState<UnitSummary[]>([])
  const [selectedUnitTypes, setSelectedUnitTypes] = useState<string[]>([])
  const [selectedUnitIds, setSelectedUnitIds] = useState<string[]>([])
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    if (isOpen) {
      onTogglePauseRefresh?.(true)
    } else {
      onTogglePauseRefresh?.(false)
    }
  }, [isOpen, onTogglePauseRefresh])

  const fetchUnits = useCallback(async () => {
    setIsLoading(true)
    try {
      const units = await fastPinPonService.getNearbyUnits(
        event.location.latitude,
        event.location.longitude,
        selectedUnitTypes.length > 0 ? selectedUnitTypes : undefined,
        token ?? undefined
      )
      
      // Filter out already assigned units
      const assignedIds = new Set(event.assigned_units?.map(u => u.id) ?? [])
      setNearbyUnits(units.filter(u => !assignedIds.has(u.id)))
    } catch (err) {
      console.error('Failed to fetch nearby units', err)
    } finally {
      setIsLoading(false)
    }
  }, [event.location, selectedUnitTypes, token, event.assigned_units])

  useEffect(() => {
    if (!isOpen) {
      setSelectedUnitIds([])
      setNearbyUnits([])
      return
    }

    const fetchTypesAndInit = async () => {
      setIsLoading(true)
      try {
        const [uTypes, eTypes] = await Promise.all([
          fastPinPonService.getUnitTypes(),
          fastPinPonService.getEventTypes()
        ])
        setUnitTypes(uTypes)

        // Find recommended unit types for this event
        const eventType = eTypes.find((t: EventType) => t.code === event.event_type_code)
        if (eventType) {
          setSelectedUnitTypes(eventType.recommended_unit_types)
        }
      } catch (err) {
        console.error('Failed to fetch types', err)
      } finally {
        setIsLoading(false)
      }
    }

    fetchTypesAndInit()
  }, [isOpen, event.event_type_code])

  useEffect(() => {
    if (!isOpen) return
    fetchUnits()
  }, [isOpen, fetchUnits])

  const toggleUnitType = (code: string) => {
    setSelectedUnitTypes(prev => 
      prev.includes(code) ? prev.filter(c => c !== code) : [...prev, code]
    )
  }

  const toggleUnitSelection = (id: string) => {
    setSelectedUnitIds(prev => 
      prev.includes(id) ? prev.filter(i => i !== id) : [...prev, id]
    )
  }

  const handleAssign = async () => {
    if (!event.intervention_id || selectedUnitIds.length === 0 || isSubmitting) return

    setIsSubmitting(true)
    try {
      await Promise.all(
        selectedUnitIds.map(unitId => 
          fastPinPonService.assignUnitToIntervention(event.intervention_id!, unitId, token ?? undefined)
        )
      )
      if (onRefresh) await onRefresh()
      onClose()
    } catch (err) {
      console.error('Failed to assign units', err)
    } finally {
      setIsSubmitting(false)
    }
  }

  if (!isOpen) return null

  const unitCountLabel = selectedUnitIds.length > 0 ? selectedUnitIds.length : ''
  const unitPluralSuffix = selectedUnitIds.length > 1 ? 's' : ''

  let unitsListContent: JSX.Element | JSX.Element[]
  if (isLoading) {
    unitsListContent = (
      <div className="flex flex-col justify-center items-center space-y-3 py-12">
        <div className="border-2 border-sky-500/30 border-t-sky-500 rounded-full w-6 h-6 animate-spin" />
        <p className="text-slate-500 text-xs animate-pulse">Recherche des unités...</p>
      </div>
    )
  } else if (nearbyUnits.length === 0) {
    unitsListContent = (
      <div className="bg-slate-800/20 py-12 border border-slate-800 border-dashed rounded-2xl text-center">
        <p className="text-slate-500 text-sm italic">Aucune unité disponible pour ces critères.</p>
      </div>
    )
  } else {
    unitsListContent = nearbyUnits.map(unit => {
      const isSelected = selectedUnitIds.includes(unit.id)
      const distance = unit.distance_meters ? (unit.distance_meters / 1000).toFixed(1) : '?'
      return (
        <button
          key={unit.id}
          type="button"
          onClick={() => toggleUnitSelection(unit.id)}
          className={`w-full group flex justify-between items-center p-3.5 rounded-xl border transition-all duration-300 ${
            isSelected
              ? 'bg-sky-500/10 border-sky-500/50 shadow-[0_0_20px_-10px_rgba(14,165,233,0.3)]'
              : 'bg-slate-800/30 border-slate-800 hover:bg-slate-800/50 hover:border-slate-700'
          }`}
        >
          <div className="flex items-center gap-4">
            <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center transition-colors duration-300 ${
              isSelected ? 'bg-sky-500 border-sky-500' : 'border-slate-700 bg-slate-900'
            }`}>
              {isSelected && <svg width="10" height="10" viewBox="0 0 10 10" fill="none" className="text-white"><path d="M2 5L4 7L8 3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>}
            </div>
            <div className="text-left">
              <p className="font-bold text-white text-sm tracking-tight">{unit.call_sign}</p>
              <div className="flex items-center gap-2 mt-0.5">
                <span className="bg-sky-500/10 px-1.5 py-0.5 rounded font-bold text-[0.65rem] text-sky-400">{unit.unit_type_code}</span>
                <span className="font-medium text-[0.65rem] text-slate-500">• {unit.home_base}</span>
              </div>
            </div>
          </div>
          <div className="text-right">
            <p className="font-black text-white text-xs">{distance} <span className="ml-0.5 font-bold text-[0.6rem] text-slate-400 uppercase">km</span></p>
            <p className={`text-[0.65rem] mt-1 font-bold uppercase tracking-tighter ${
              unit.status === 'available' ? 'text-emerald-400/90' : 'text-slate-500'
            }`}>{unit.status}</p>
          </div>
        </button>
      )
    })
  }

  return (
    <div className="z-50 fixed inset-0 flex justify-center items-center bg-slate-950/70 backdrop-blur-sm p-4">
      <Card className="flex flex-col bg-slate-900 shadow-2xl border-slate-800 rounded-xl w-full max-w-2xl h-[85vh] overflow-hidden">
        <div className="flex justify-between items-center bg-slate-900/50 p-4 border-slate-800 border-b">
          <div>
            <h2 className="font-bold text-white text-xl leading-tight">Assigner des unités</h2>
            <p className="mt-1 text-slate-400 text-sm">{event.title}</p>
          </div>
          <div className="flex items-center gap-2">
            <button 
              onClick={() => fetchUnits()} 
              disabled={isLoading}
              className={`p-2 text-slate-400 hover:text-white transition-colors rounded-lg hover:bg-slate-800 ${isLoading ? 'animate-spin opacity-50' : ''}`}
              title="Actualiser les unités"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"></path><path d="M3 3v5h5"></path><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"></path><path d="M16 16h5v5"></path></svg>
            </button>
            <button onClick={onClose} className="hover:bg-slate-800 p-2 rounded-lg text-slate-400 hover:text-white transition-colors" aria-label="Fermer">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
            </button>
          </div>
        </div>

        <div className="flex-1 space-y-8 p-5 overflow-y-auto">
          <section>
            <h3 className="mb-4 font-bold text-[0.7rem] text-sky-400 uppercase tracking-widest">Filtrer par type d'unité</h3>
            <div className="gap-2 grid grid-cols-2 sm:grid-cols-4">
              {unitTypes.map(type => (
                <button
                  key={type.code}
                  type="button"
                  onClick={() => toggleUnitType(type.code)}
                  className={`p-3 rounded-xl border text-left transition-all duration-200 ${
                    selectedUnitTypes.includes(type.code)
                      ? 'bg-sky-500/10 border-sky-500 text-sky-100 shadow-[0_0_15px_-5px_rgba(14,165,233,0.4)]'
                      : 'bg-slate-800/40 border-slate-700/50 text-slate-400 hover:border-slate-500'
                  }`}
                >
                  <p className="font-black text-xs leading-none tracking-tighter">{type.code}</p>
                  <p className="opacity-80 mt-1.5 font-medium text-[0.65rem] truncate">{type.name}</p>
                </button>
              ))}
            </div>
          </section>

          <section>
            <div className="flex justify-between items-end mb-4">
              <h3 className="font-bold text-[0.7rem] text-sky-400 uppercase leading-none tracking-widest">Unités disponibles à proximité</h3>
              <span className="bg-slate-800/80 px-2 py-0.5 rounded-full font-medium text-[0.6rem] text-slate-500 uppercase tracking-tighter">Tri par distance</span>
            </div>
            
            <div className="space-y-2.5">
              {unitsListContent}
            </div>
          </section>
        </div>

        <div className="flex justify-end gap-3 bg-slate-900/50 backdrop-blur-md p-4 border-slate-800 border-t">
          <Button variant="ghost" onClick={onClose} disabled={isSubmitting} className="px-6">
            Annuler
          </Button>
          <Button 
            onClick={handleAssign} 
            disabled={selectedUnitIds.length === 0 || isSubmitting}
            className="px-8 min-w-[140px]"
          >
            {isSubmitting ? (
              <div className="flex items-center gap-2">
                <div className="border-2 border-slate-950/30 border-t-slate-950 rounded-full w-3 h-3 animate-spin" />
                <span>Envoi...</span>
              </div>
            ) : (
              `Assigner ${unitCountLabel} unité${unitPluralSuffix}`
            )}
          </Button>
        </div>
      </Card>
    </div>
  )
}
