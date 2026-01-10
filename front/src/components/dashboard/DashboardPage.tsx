import type { JSX } from 'react'
import { useState, useMemo } from 'react'

import type { UnitSummary, Building } from '../../types'
import { Card } from '../ui/card'
import { AssignMicrobitModal } from './AssignMicrobitModal'
import { EditUnitModal } from './EditUnitModal'
import { fastPinPonService } from '../../services/FastPinPonService'
import { useAuth } from '../../auth/AuthProvider'

import { StatusBadge } from '../ui/StatusBadge'

type UnitStatus = 'available' | 'available_hidden' | 'under_way' | 'on_site' | 'unavailable' | 'offline'

interface DashboardPageProps {
  units: UnitSummary[]
  buildings?: Building[]
  selectedStationId?: string | null
  onStationChange?: (stationId: string | null) => void
  onRefresh?: () => void
}

type SortDirection = 'asc' | 'desc'
type SortKey = 'call_sign' | 'unit_type_code' | 'station' | 'status' | 'microbit_id' | 'last_contact_at'

type SortRule = {
  key: SortKey
  direction: SortDirection
}

const normalizeStatus = (status: string) => status?.toLowerCase().replaceAll(/[-\s]/g, '_') ?? ''

const formatDate = (iso: string) => {
  if (!iso) return '—'
  const date = new Date(iso)
  return `${date.toLocaleDateString('fr-FR')} ${date.toLocaleTimeString('fr-FR', {
    hour: '2-digit',
    minute: '2-digit',
  })}`
}

export function DashboardPage({ units, buildings = [], selectedStationId, onStationChange, onRefresh }: Readonly<DashboardPageProps>): JSX.Element {
  const { token } = useAuth()
  const [selectedUnitId, setSelectedUnitId] = useState<string | null>(null)
  const [selectedUnitCallSign, setSelectedUnitCallSign] = useState<string | undefined>(undefined)
  const [selectedUnitMicrobitId, setSelectedUnitMicrobitId] = useState<string | undefined>(undefined)
  const [editingUnit, setEditingUnit] = useState<UnitSummary | null>(null)
  const [sortRules, setSortRules] = useState<SortRule[]>([])
  const [deletingUnitId, setDeletingUnitId] = useState<string | null>(null)
  const microbitPool = ['MB001', 'MB002', 'MB003', 'MB004', 'MB005', 'MB006', 'MB007', 'MB008', 'MB009', 'MB010']
  const usedMicrobits = new Set(units.map((unit) => unit.microbit_id).filter(Boolean))
  const availableMicrobits = microbitPool.filter(
    (id) => !usedMicrobits.has(id) || selectedUnitMicrobitId === id,
  )

  const handleAssign = async (microbitId: string) => {
    if (!selectedUnitId) return

    const alreadyAssigned = units.find(
      (unit) => unit.microbit_id === microbitId && unit.id !== selectedUnitId,
    )
    if (alreadyAssigned) {
      throw new Error(`Microbit ${microbitId} déjà assigné à ${alreadyAssigned.call_sign}.`)
    }

    await fastPinPonService.assignMicrobit(selectedUnitId, microbitId, token ?? undefined)
    if (onRefresh) onRefresh()
  }

  const handleUnassign = async () => {
    if (!selectedUnitId) return
    await fastPinPonService.unassignMicrobit(selectedUnitId, token ?? undefined)
    if (onRefresh) onRefresh()
  }

  const handleEditUnit = async (updates: { status?: UnitStatus; locationId?: string | null }) => {
    if (!editingUnit) return

    if (updates.status) {
      await fastPinPonService.updateUnitStatus(editingUnit.id, updates.status, token ?? undefined)
    }
    if (updates.locationId !== undefined) {
      await fastPinPonService.updateUnitStation(editingUnit.id, updates.locationId, token ?? undefined)
    }
    if (onRefresh) onRefresh()
  }

  const handleDeleteUnit = async (unitId: string, event: React.MouseEvent) => {
    event.stopPropagation()
    if (deletingUnitId) return
    setDeletingUnitId(unitId)
    try {
      await fastPinPonService.deleteUnit(unitId, token ?? undefined)
      if (onRefresh) onRefresh()
    } finally {
      setDeletingUnitId(null)
    }
  }

  const toggleSort = (key: SortKey) => {
    setSortRules((current) => {
      const idx = current.findIndex((rule) => rule.key === key)
      if (idx === -1) {
        return [...current, { key, direction: 'asc' }]
      }

      const existing = current[idx]
      if (existing.direction === 'asc') {
        const updated = [...current]
        updated[idx] = { ...existing, direction: 'desc' }
        return updated
      }

      const without = current.filter((rule) => rule.key !== key)
      return without
    })
  }

  // Get station name from location_id for sorting
  const getStationName = (locationId: string | undefined): string => {
    if (!locationId) return ''
    return buildings.find(b => b.id === locationId)?.name ?? ''
  }

  const sortValue = (unit: UnitSummary, key: SortKey): string | number => {
    switch (key) {
      case 'call_sign':
        return unit.call_sign ?? ''
      case 'unit_type_code':
        return unit.unit_type_code ?? ''
      case 'station':
        return getStationName(unit.location_id)
      case 'status':
        return normalizeStatus(unit.status)
      case 'microbit_id':
        return unit.microbit_id ?? ''
      case 'last_contact_at':
        return unit.last_contact_at ? new Date(unit.last_contact_at).getTime() : 0
      default:
        return ''
    }
  }

  // Filter units by selected station
  const filteredUnits = useMemo(() => {
    if (!selectedStationId) return units
    return units.filter(unit => unit.location_id === selectedStationId)
  }, [units, selectedStationId])

  // Get selected station name for display
  const selectedStation = useMemo(() => {
    if (!selectedStationId) return null
    return buildings.find(b => b.id === selectedStationId) ?? null
  }, [buildings, selectedStationId])

  const sortedUnits = (() => {
    if (sortRules.length === 0) return filteredUnits
    return [...filteredUnits]
      .map((unit, idx) => ({ unit, idx }))
      .sort((a, b) => {
        for (const rule of sortRules) {
          const aVal = sortValue(a.unit, rule.key)
          const bVal = sortValue(b.unit, rule.key)
          if (aVal < bVal) return rule.direction === 'asc' ? -1 : 1
          if (aVal > bVal) return rule.direction === 'asc' ? 1 : -1
        }
        return a.idx - b.idx
      })
      .map((entry) => entry.unit)
  })()

  const renderSortIndicator = (key: SortKey) => {
    const rule = sortRules.find((r) => r.key === key)
    if (!rule) return null
    return <span className="ml-1 text-xs text-cyan-300">{rule.direction === 'asc' ? '▲' : '▼'}</span>
  }

  return (
    <div className="flex flex-col gap-4 flex-1 pt-0 px-6 py-6 bg-slate-950 text-slate-100">
      {/* Station filter bar */}
      <div className="flex items-center gap-4">
        <label htmlFor="station-filter" className="text-sm text-slate-400">
          Caserne :
        </label>
        <select
          id="station-filter"
          value={selectedStationId ?? ''}
          onChange={(e) => onStationChange?.(e.target.value || null)}
          className="px-3 py-1.5 text-sm bg-slate-800 border border-slate-700 rounded-lg text-slate-200 focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
        >
          <option value="">Toutes les casernes</option>
          {buildings.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name}
            </option>
          ))}
        </select>
        {selectedStation && (
          <button
            type="button"
            onClick={() => onStationChange?.(null)}
            className="px-2 py-1 text-xs bg-slate-700 hover:bg-slate-600 rounded text-slate-300 transition-colors"
          >
            Effacer le filtre
          </button>
        )}
        <span className="ml-auto text-sm text-slate-400">
          {sortedUnits.length} unité{sortedUnits.length === 1 ? '' : 's'}
          {selectedStation && ` dans ${selectedStation.name}`}
        </span>
      </div>

      <Card className="w-full">
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left text-slate-200">
            <thead className="text-xs uppercase text-slate-400 border-b border-slate-800">
              <tr>
                <th className="px-3 py-2 cursor-pointer select-none" onClick={() => toggleSort('call_sign')}>
                  Call sign
                  {renderSortIndicator('call_sign')}
                </th>
                <th className="px-3 py-2 cursor-pointer select-none" onClick={() => toggleSort('unit_type_code')}>
                  Type
                  {renderSortIndicator('unit_type_code')}
                </th>
                <th className="px-3 py-2 cursor-pointer select-none" onClick={() => toggleSort('station')}>
                  Caserne
                  {renderSortIndicator('station')}
                </th>
                <th className="px-3 py-2 cursor-pointer select-none" onClick={() => toggleSort('status')}>
                  Statut
                  {renderSortIndicator('status')}
                </th>
                <th className="px-3 py-2 cursor-pointer select-none" onClick={() => toggleSort('microbit_id')}>
                  Microbit
                  {renderSortIndicator('microbit_id')}
                </th>
                <th className="px-3 py-2 cursor-pointer select-none whitespace-nowrap" onClick={() => toggleSort('last_contact_at')}>
                  Dernier contact
                  {renderSortIndicator('last_contact_at')}
                </th>
                <th className="px-2 py-2 text-center whitespace-nowrap">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-900/60">
              {sortedUnits.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-3 py-6 text-center text-slate-400">
                    Aucune unité disponible.
                  </td>
                </tr>
              ) : (
                sortedUnits.map((unit) => {
                  const stationName = buildings.find(b => b.id === unit.location_id)?.name ?? '—'

                  return (
                    <tr
                      key={unit.id}
                      className="hover:bg-slate-900/40 cursor-pointer"
                      onClick={() => {
                        setSelectedUnitId(unit.id)
                        setSelectedUnitCallSign(unit.call_sign)
                        setSelectedUnitMicrobitId(unit.microbit_id)
                      }}
                    >
                      <td className="px-3 py-2 font-semibold text-white">{unit.call_sign}</td>
                      <td className="px-3 py-2 text-slate-300">{unit.unit_type_code}</td>
                      <td className="px-3 py-2 text-slate-300">
                        {stationName}
                      </td>
                      <td className="px-3 py-2">
                        <StatusBadge status={unit.status} type="unit" />
                      </td>
                      <td className="px-3 py-2 text-slate-300">{unit.microbit_id ?? '—'}</td>
                      <td className="px-3 py-2 text-slate-300 whitespace-nowrap">{formatDate(unit.last_contact_at)}</td>
                      <td className="px-1 py-2 text-center w-24">
                        <div className="flex items-center justify-center gap-1">
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation()
                              setEditingUnit(unit)
                            }}
                            className="p-1.5 rounded-md text-cyan-400/70 hover:text-cyan-400 hover:bg-cyan-500/10 transition-all duration-150 hover:scale-110 active:scale-95"
                            aria-label={`Modifier ${unit.call_sign}`}
                            title="Modifier"
                          >
                            <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                            </svg>
                          </button>
                          <button
                            type="button"
                            onClick={(e) => handleDeleteUnit(unit.id, e)}
                            disabled={deletingUnitId === unit.id}
                            className="p-1.5 rounded-md text-red-400/70 hover:text-red-400 hover:bg-red-500/10 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-150 hover:scale-110 active:scale-95"
                            aria-label={`Supprimer ${unit.call_sign}`}
                            title="Supprimer"
                          >
                            {deletingUnitId === unit.id ? (
                              <svg className="w-4 h-4 animate-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <circle cx="12" cy="12" r="10" strokeOpacity="0.25" />
                                <path d="M12 2a10 10 0 0 1 10 10" strokeLinecap="round" />
                              </svg>
                            ) : (
                              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                                <line x1="18" y1="6" x2="6" y2="18" />
                                <line x1="6" y1="6" x2="18" y2="18" />
                              </svg>
                            )}
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <AssignMicrobitModal
        isOpen={selectedUnitId !== null}
        onClose={() => {
          setSelectedUnitId(null)
          setSelectedUnitCallSign(undefined)
          setSelectedUnitMicrobitId(undefined)
        }}
        onSubmit={handleAssign}
        onUnassign={selectedUnitMicrobitId ? handleUnassign : undefined}
        microbitOptions={availableMicrobits}
        unitCallSign={selectedUnitCallSign}
        initialSelection={selectedUnitMicrobitId}
      />

      <EditUnitModal
        isOpen={editingUnit !== null}
        onClose={() => setEditingUnit(null)}
        onSubmit={handleEditUnit}
        unit={editingUnit}
        buildings={buildings}
      />
    </div>
  )
}