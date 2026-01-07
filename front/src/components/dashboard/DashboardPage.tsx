import type { JSX } from 'react'
import { useState } from 'react'

import type { UnitSummary } from '../../types'
import { Card } from '../ui/card'
import { AssignMicrobitModal } from './AssignMicrobitModal'
import { fastPinPonService } from '../../services/FastPinPonService'
import { useAuth } from '../../auth/AuthProvider'

interface DashboardPageProps {
  units: UnitSummary[]
  onRefresh?: () => void
}

type SortDirection = 'asc' | 'desc'
type SortKey = 'call_sign' | 'unit_type_code' | 'home_base' | 'status' | 'microbit_id' | 'last_contact_at'

type SortRule = {
  key: SortKey
  direction: SortDirection
}

const STATUS_COLORS: Record<string, string> = {
  available: 'bg-emerald-500/20 text-emerald-300 border-emerald-400/30',
  en_route: 'bg-blue-500/15 text-blue-200 border-blue-300/30',
  on_site: 'bg-amber-500/15 text-amber-200 border-amber-300/30',
  maintenance: 'bg-purple-500/15 text-purple-200 border-purple-300/30',
  offline: 'bg-slate-600/20 text-slate-200 border-slate-300/20',
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

export function DashboardPage({ units, onRefresh }: Readonly<DashboardPageProps>): JSX.Element {
  const { token } = useAuth()
  const [selectedUnitId, setSelectedUnitId] = useState<string | null>(null)
  const [selectedUnitCallSign, setSelectedUnitCallSign] = useState<string | undefined>(undefined)
  const [selectedUnitMicrobitId, setSelectedUnitMicrobitId] = useState<string | undefined>(undefined)
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

  const sortValue = (unit: UnitSummary, key: SortKey): string | number => {
    switch (key) {
      case 'call_sign':
        return unit.call_sign ?? ''
      case 'unit_type_code':
        return unit.unit_type_code ?? ''
      case 'home_base':
        return unit.home_base ?? ''
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

  const sortedUnits = (() => {
    if (sortRules.length === 0) return units
    return [...units]
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
                <th className="px-3 py-2 cursor-pointer select-none" onClick={() => toggleSort('home_base')}>
                  Base
                  {renderSortIndicator('home_base')}
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
                  Suppr.
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-900/60">
              {sortedUnits.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-3 py-6 text-center text-slate-400">
                    Aucune unité disponible.
                  </td>
                </tr>
              ) : (
                sortedUnits.map((unit) => {
                  const normalizedStatus = normalizeStatus(unit.status)
                  const pillClass = STATUS_COLORS[normalizedStatus] ?? STATUS_COLORS.offline

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
                      <td className="px-3 py-2 text-slate-300">{unit.home_base}</td>
                      <td className="px-3 py-2">
                        <span className={`inline-flex items-center gap-1 px-2 py-1 text-[11px] font-semibold border rounded-full ${pillClass}`}>
                          <span className="inline-flex w-2 h-2 rounded-full bg-current" aria-hidden="true" />
                          {unit.status}
                        </span>
                      </td>
                      <td className="px-3 py-2 text-slate-300">{unit.microbit_id ?? '—'}</td>
                      <td className="px-3 py-2 text-slate-300 whitespace-nowrap">{formatDate(unit.last_contact_at)}</td>
                      <td className="px-1 py-2 text-center w-16">
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
    </div>
  )
}