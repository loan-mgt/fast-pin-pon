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

  return (
    <div className="flex flex-col gap-4 flex-1 pt-0 px-6 py-6 bg-slate-950 text-slate-100">

      <Card className="w-full">
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left text-slate-200">
            <thead className="text-xs uppercase text-slate-400 border-b border-slate-800">
              <tr>
                <th className="px-3 py-2">Call sign</th>
                <th className="px-3 py-2">Type</th>
                <th className="px-3 py-2">Base</th>
                <th className="px-3 py-2">Statut</th>
                <th className="px-3 py-2">Microbit</th>
                <th className="px-3 py-2">Dernier contact</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-900/60">
              {units.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-3 py-6 text-center text-slate-400">
                    Aucune unité disponible.
                  </td>
                </tr>
              ) : (
                units.map((unit) => {
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