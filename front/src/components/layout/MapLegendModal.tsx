import type { JSX } from 'react'
import { useState } from 'react'
import { UNIT_TYPE_ICONS, DEFAULT_UNIT_ICON, EVENT_TYPE_ICONS, DEFAULT_EVENT_ICON } from '../map/mapIcons'

// Unit type definitions with full names
const UNIT_TYPES = [
    { code: 'FPT', name: 'Fourgon Pompe Tonne' },
    { code: 'FPTL', name: 'Fourgon Pompe Tonne Léger' },
    { code: 'VER', name: 'Véhicule Extraction Routière' },
    { code: 'VIA', name: 'Véhicule Intervention Aérienne' },
    { code: 'VIM', name: 'Véhicule Intervention Maritime' },
    { code: 'VSAV', name: 'Véhicule de Secours et d\'Assistance aux Victimes' },
    { code: 'VLHR', name: 'Véhicule Léger Haute-Résistance' },
    { code: 'EPA', name: 'Échelle Pivotante Automatique' },
] as const

// Event type definitions with full names
const EVENT_TYPES = [
    { code: 'CRASH', name: 'Accident Routier' },
    { code: 'FIRE_URBAN', name: 'Incendie Urbain' },
    { code: 'FIRE_INDUSTRIAL', name: 'Incendie Industriel' },
    { code: 'RESCUE_MEDICAL', name: 'Secours Médical' },
    { code: 'AQUATIC_RESCUE', name: 'Incidents Aquatiques' },
    { code: 'HAZMAT', name: 'Matières Dangereuses' },
    { code: 'OTHER', name: 'Autres Incidents' },
] as const

type LegendItemProps = {
    svgHtml: string
    code: string
    name: string
    size: number
}

function LegendItem({ svgHtml, code, name, size }: Readonly<LegendItemProps>): JSX.Element {
    return (
        <div className="flex items-center gap-3 py-2">
            <div
                className="flex-shrink-0 flex items-center justify-center"
                style={{ width: size, height: size }}
                dangerouslySetInnerHTML={{ __html: svgHtml }}
            />
            <div className="flex flex-col min-w-0">
                <span className="font-semibold text-slate-200 text-sm">{code}</span>
                <span className="text-slate-400 text-xs truncate">{name}</span>
            </div>
        </div>
    )
}

export function MapLegendModal(): JSX.Element {
    const [isOpen, setIsOpen] = useState(false)

    return (
        <>
            {/* Help button */}
            <button
                type="button"
                onClick={() => setIsOpen(true)}
                className="flex items-center justify-center hover:bg-slate-700/50 p-1.5 rounded-md text-slate-400 hover:text-blue-400 transition-colors cursor-pointer"
                aria-label="Légende de la carte"
                title="Légende"
            >
                <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="18"
                    height="18"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                >
                    <circle cx="12" cy="12" r="10" />
                    <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
                    <path d="M12 17h.01" />
                </svg>
            </button>

            {/* Modal overlay */}
            {isOpen && (
                <div
                    className="z-50 fixed inset-0 flex items-center justify-center bg-black/60 backdrop-blur-sm"
                    onClick={() => setIsOpen(false)}
                    onKeyDown={(e) => e.key === 'Escape' && setIsOpen(false)}
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="legend-title"
                >
                    {/* Modal content */}
                    <div
                        className="relative bg-slate-900 border border-slate-700 shadow-2xl mx-4 p-6 rounded-xl w-full max-w-2xl max-h-[80vh] overflow-y-auto"
                        onClick={(e) => e.stopPropagation()}
                    >
                        {/* Close button */}
                        <button
                            type="button"
                            onClick={() => setIsOpen(false)}
                            className="top-4 right-4 absolute text-slate-400 hover:text-white transition-colors cursor-pointer"
                            aria-label="Fermer"
                        >
                            <svg
                                xmlns="http://www.w3.org/2000/svg"
                                width="20"
                                height="20"
                                viewBox="0 0 24 24"
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="2"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                            >
                                <path d="M18 6 6 18" />
                                <path d="m6 6 12 12" />
                            </svg>
                        </button>

                        <h2 id="legend-title" className="mb-6 font-bold text-white text-xl">
                            Légende de la carte
                        </h2>

                        <div className="gap-8 grid grid-cols-1 md:grid-cols-2">
                            {/* Units section */}
                            <div>
                                <h3 className="flex items-center gap-2 mb-3 pb-2 border-slate-700 border-b font-semibold text-cyan-400 text-sm uppercase tracking-wide">
                                    <div
                                        className="w-5 h-5"
                                        dangerouslySetInnerHTML={{ __html: DEFAULT_UNIT_ICON }}
                                    />
                                    Unités
                                </h3>
                                <div className="space-y-1">
                                    {UNIT_TYPES.map((unit) => (
                                        <LegendItem
                                            key={unit.code}
                                            svgHtml={UNIT_TYPE_ICONS[unit.code] ?? DEFAULT_UNIT_ICON}
                                            code={unit.code}
                                            name={unit.name}
                                            size={28}
                                        />
                                    ))}
                                </div>
                            </div>

                            {/* Events section */}
                            <div>
                                <h3 className="flex items-center gap-2 mb-3 pb-2 border-slate-700 border-b font-semibold text-red-400 text-sm uppercase tracking-wide">
                                    <div
                                        className="w-5 h-5"
                                        dangerouslySetInnerHTML={{ __html: DEFAULT_EVENT_ICON }}
                                    />
                                    Événements
                                </h3>
                                <div className="space-y-1">
                                    {EVENT_TYPES.map((event) => (
                                        <LegendItem
                                            key={event.code}
                                            svgHtml={EVENT_TYPE_ICONS[event.code] ?? DEFAULT_EVENT_ICON}
                                            code={event.code}
                                            name={event.name}
                                            size={28}
                                        />
                                    ))}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </>
    )
}
