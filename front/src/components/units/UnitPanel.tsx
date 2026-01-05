import type { JSX } from 'react'
import { useState } from 'react'

import { Card } from '../ui/card'
import type { UnitSummary } from '../../types'

interface UnitPanelProps {
  units: UnitSummary[]
}

export function UnitPanel({ units }: Readonly<UnitPanelProps>): JSX.Element {
  const [isPanelCollapsed, setIsPanelCollapsed] = useState(true)

  const normalizeStatus = (status: string) => status?.toLowerCase().replace(/[-\s]/g, '_') ?? ''

  const availableUnits = units.filter((unit) => {
    const status = normalizeStatus(unit.status)
    return status === 'available' || status === 'avl'
  })

  return (
    <Card
      className={
        `z-10 fixed bottom-0 left-0 shadow-2xl shadow-slate-950/60 p-0 flex flex-col overflow-hidden rounded-none ` +
        (isPanelCollapsed
          ? 'w-auto max-w-[90vw]'
          : 'w-[320px] min-w-[260px] max-w-[90vw] max-h-[60vh]')
      }
    >
      <button
        type="button"
        className="flex justify-between items-center select-none flex-shrink-0 w-full text-left bg-transparent border-none whitespace-nowrap gap-2 px-2 py-1"
        aria-label={isPanelCollapsed ? 'Open available vehicles panel' : 'Close available vehicles panel'}
        onClick={() => setIsPanelCollapsed(!isPanelCollapsed)}
      >
        <div className="flex items-center gap-2 whitespace-nowrap">
          <p className="font-semibold text-white text-lg leading-none">Available vehicles</p>
          <span className="bg-blue-500/20 text-blue-400 text-xs font-medium px-2 py-0.5 rounded-full leading-none">
            {availableUnits.length}
          </span>
        </div>
      </button>

      {!isPanelCollapsed && (
        <div className="mt-1 flex-1 min-h-0 overflow-y-auto">
          {availableUnits.length === 0 ? (
            <p className="text-slate-300 text-sm">No available vehicles.</p>
          ) : (
            <div className="space-y-3">
              {availableUnits.map((unit) => (
                <article
                  key={unit.id}
                  className="space-y-1 bg-slate-800/50 backdrop-blur-sm p-3 border border-blue-500/10 rounded-xl"
                >
                  <div className="flex justify-between items-center gap-2">
                    <h3 className="font-semibold text-white text-sm">{unit.call_sign}</h3>
                    <span className="text-[0.55rem] text-cyan-300/90 uppercase tracking-[0.4em]">
                      {unit.unit_type_code}
                    </span>
                  </div>
                  <p className="text-slate-400 text-xs">{unit.home_base}</p>
                </article>
              ))}
            </div>
          )}
        </div>
      )}
    </Card>
  )
}
