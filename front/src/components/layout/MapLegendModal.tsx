import type { JSX } from 'react'
import { useRef, useEffect } from 'react'
import { UNIT_TYPE_ICONS, DEFAULT_UNIT_ICON, EVENT_TYPE_ICONS, DEFAULT_EVENT_ICON, SEVERITY_COLORS, EVENT_TYPE_ICON_TEMPLATES, UNIT_STATUS_COLORS, UNIT_TYPE_ICON_TEMPLATES } from '../map/mapIcons'

// Unit type definitions
const UNIT_TYPES = [
    { code: 'FPT', name: 'Pompe Tonne' },
    { code: 'FPTL', name: 'Pompe Léger' },
    { code: 'VER', name: 'Extraction' },
    { code: 'VIA', name: 'Aérien' },
    { code: 'VIM', name: 'Maritime' },
    { code: 'VSAV', name: 'Secours' },
    { code: 'VLHR', name: 'Haute-Rés.' },
    { code: 'EPA', name: 'Échelle' },
] as const

// Event type definitions
const EVENT_TYPES = [
    { code: 'CRASH', name: 'Accident' },
    { code: 'FIRE_URBAN', name: 'Feu Urbain' },
    { code: 'FIRE_INDUSTRIAL', name: 'Feu Industriel' },
    { code: 'RESCUE_MEDICAL', name: 'Médical' },
    { code: 'AQUATIC_RESCUE', name: 'Aquatique' },
    { code: 'HAZMAT', name: 'Dangereux' },
    { code: 'OTHER', name: 'Autre' },
] as const

// Severity levels
const SEVERITY_LEVELS = [
    { level: 'Faible', range: '1-2', color: SEVERITY_COLORS.low },
    { level: 'Moyen', range: '3', color: SEVERITY_COLORS.medium },
    { level: 'Élevé', range: '4+', color: SEVERITY_COLORS.high },
] as const

// Unit statuses
const UNIT_STATUSES = [
    { status: 'available', label: 'Disponible', color: UNIT_STATUS_COLORS.available },
    { status: 'under_way', label: 'En route', color: UNIT_STATUS_COLORS.under_way },
    { status: 'on_site', label: 'Sur site', color: UNIT_STATUS_COLORS.on_site },
    { status: 'unavailable', label: 'Indisponible', color: UNIT_STATUS_COLORS.unavailable },
    { status: 'offline', label: 'Hors ligne', color: UNIT_STATUS_COLORS.offline },
] as const

type LegendItemProps = {
    svgHtml: string
    code: string
    name: string
    size: number
}

function LegendItem({ svgHtml, code, name, size }: Readonly<LegendItemProps>): JSX.Element {
    return (
        <div className="flex items-center gap-4 py-2">
            <div
                className="flex-shrink-0 flex items-center justify-center"
                style={{ width: size, height: size }}
                dangerouslySetInnerHTML={{ __html: svgHtml }}
            />
            <span className="text-slate-300 text-base">
                <span className="font-medium">{code}</span>
                <span className="text-slate-500"> · {name}</span>
            </span>
        </div>
    )
}

export function MapLegendModal(): JSX.Element {
    const dialogRef = useRef<HTMLDialogElement>(null)

    const openModal = () => {
        dialogRef.current?.showModal()
    }

    const closeModal = () => {
        dialogRef.current?.close()
    }

    // Close on backdrop click
    useEffect(() => {
        const dialog = dialogRef.current
        if (!dialog) return

        const handleClick = (e: MouseEvent) => {
            const rect = dialog.getBoundingClientRect()
            const isInDialog =
                e.clientX >= rect.left &&
                e.clientX <= rect.right &&
                e.clientY >= rect.top &&
                e.clientY <= rect.bottom
            if (!isInDialog) {
                dialog.close()
            }
        }

        dialog.addEventListener('click', handleClick)
        return () => dialog.removeEventListener('click', handleClick)
    }, [])

    return (
        <>
            {/* Help button */}
            <button
                type="button"
                onClick={openModal}
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

            {/* Native dialog element */}
            <dialog
                ref={dialogRef}
                className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 bg-transparent p-0 m-0 backdrop:bg-black/60 backdrop:backdrop-blur-sm"
                aria-labelledby="legend-title"
            >
                {/* Modal content - 4-column layout */}
                <div className="relative bg-slate-900 border border-slate-700 shadow-2xl p-6 rounded-xl w-full max-w-6xl">
                    {/* Close button */}
                    <button
                        type="button"
                        onClick={closeModal}
                        className="top-3 right-3 absolute text-slate-400 hover:text-white transition-colors cursor-pointer"
                        aria-label="Fermer"
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
                            <path d="M18 6 6 18" />
                            <path d="m6 6 12 12" />
                        </svg>
                    </button>

                    <h2 id="legend-title" className="mb-6 font-bold text-white text-2xl">
                        Légende de la carte
                    </h2>

                    {/* 4-column grid */}
                    <div className="gap-6 grid grid-cols-2 md:grid-cols-4">
                        {/* Units types section */}
                        <div>
                            <h3 className="flex items-center gap-3 mb-4 pb-3 border-slate-700 border-b font-semibold text-cyan-400 text-base uppercase tracking-wide min-h-[48px]">
                                <div
                                    className="w-7 h-7 flex-shrink-0"
                                    dangerouslySetInnerHTML={{ __html: DEFAULT_UNIT_ICON }}
                                />
                                <span>Types unités</span>
                            </h3>
                            <div className="space-y-2">
                                {UNIT_TYPES.map((unit) => (
                                    <LegendItem
                                        key={unit.code}
                                        svgHtml={UNIT_TYPE_ICONS[unit.code] ?? DEFAULT_UNIT_ICON}
                                        code={unit.code}
                                        name={unit.name}
                                        size={36}
                                    />
                                ))}
                            </div>
                        </div>

                        {/* Unit Status section */}
                        <div>
                            <h3 className="flex items-center gap-3 mb-4 pb-3 border-slate-700 border-b font-semibold text-cyan-400 text-base uppercase tracking-wide min-h-[48px]">
                                <div className="w-6 h-6 rounded-full bg-cyan-400 flex-shrink-0" />
                                <span>Statut unités</span>
                            </h3>
                            <div className="space-y-2">
                                {UNIT_STATUSES.map((s) => (
                                    <div key={s.status} className="flex items-center gap-4 py-2">
                                        <div
                                            className="flex-shrink-0 flex items-center justify-center"
                                            style={{ width: 36, height: 36 }}
                                            dangerouslySetInnerHTML={{
                                                __html: UNIT_TYPE_ICON_TEMPLATES.VSAV(s.color)
                                            }}
                                        />
                                        <span className="text-slate-300 text-base">{s.label}</span>
                                    </div>
                                ))}
                            </div>
                        </div>

                        {/* Events types section */}
                        <div>
                            <h3 className="flex items-center gap-3 mb-4 pb-3 border-slate-700 border-b font-semibold text-red-400 text-base uppercase tracking-wide min-h-[48px]">
                                <div
                                    className="w-7 h-7 flex-shrink-0"
                                    dangerouslySetInnerHTML={{ __html: DEFAULT_EVENT_ICON }}
                                />
                                <span>Événements</span>
                            </h3>
                            <div className="space-y-2">
                                {EVENT_TYPES.map((event) => (
                                    <LegendItem
                                        key={event.code}
                                        svgHtml={EVENT_TYPE_ICONS[event.code] ?? DEFAULT_EVENT_ICON}
                                        code={event.code}
                                        name={event.name}
                                        size={36}
                                    />
                                ))}
                            </div>
                        </div>

                        {/* Severity section */}
                        <div>
                            <h3 className="flex items-center gap-3 mb-4 pb-3 border-slate-700 border-b font-semibold text-amber-400 text-base uppercase tracking-wide min-h-[48px]">
                                <svg
                                    xmlns="http://www.w3.org/2000/svg"
                                    width="24"
                                    height="24"
                                    viewBox="0 0 24 24"
                                    fill="none"
                                    stroke="currentColor"
                                    strokeWidth="2"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                    className="flex-shrink-0"
                                >
                                    <path d="M12 2L2 7l10 5 10-5-10-5z" />
                                    <path d="M2 17l10 5 10-5" />
                                    <path d="M2 12l10 5 10-5" />
                                </svg>
                                <span>Criticité</span>
                            </h3>
                            <div className="space-y-3">
                                {SEVERITY_LEVELS.map((severity) => (
                                    <div key={severity.level} className="flex items-center gap-4 py-2">
                                        <div
                                            className="flex-shrink-0 flex items-center justify-center"
                                            style={{ width: 40, height: 40 }}
                                            dangerouslySetInnerHTML={{
                                                __html: EVENT_TYPE_ICON_TEMPLATES.CRASH(severity.color)
                                            }}
                                        />
                                        <div className="flex flex-col">
                                            <span
                                                className="font-medium text-base leading-tight"
                                                style={{ color: severity.color }}
                                            >
                                                {severity.level}
                                            </span>
                                            <span className="text-slate-500 text-sm">
                                                Niveau {severity.range}
                                            </span>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                </div>
            </dialog>
        </>
    )
}
