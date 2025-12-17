
import type { ChangeEvent, JSX } from 'react'
import { Button } from '../ui/button'

const REFRESH_OPTIONS = [5, 10, 20, 30] as const

interface NavbarProps {
    refreshInterval: number
    onIntervalChange: (event: ChangeEvent<HTMLSelectElement>) => void
    onRefresh: () => void
    isSpinning: boolean
    lastUpdated: string
}

export function Navbar({
    refreshInterval,
    onIntervalChange,
    onRefresh,
    isSpinning,
    lastUpdated,
}: NavbarProps): JSX.Element {
    return (
        <nav className="bg-slate-950/90 border-slate-900/70 border-b">
            <div className="flex justify-between items-center gap-4 mx-auto px-6 py-4 max-w-6xl">
                <div>
                    <p className="text-cyan-300/70 text-xs uppercase tracking-[0.35em]">Fast Pin Pon</p>
                    <p className="font-semibold text-white text-lg">Live events</p>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                    <div className="flex items-center gap-2 text-slate-400 text-xs">
                        <span className="hidden sm:inline">Auto-refresh:</span>
                        <select
                            value={refreshInterval}
                            onChange={onIntervalChange}
                            className="bg-slate-800/80 border border-blue-500/20 rounded-lg px-2 py-1.5 text-xs text-slate-200 focus:outline-none focus:ring-2 focus:ring-blue-500/40 cursor-pointer"
                            aria-label="Auto-refresh interval"
                        >
                            {REFRESH_OPTIONS.map((seconds) => (
                                <option key={seconds} value={seconds}>
                                    {seconds}s
                                </option>
                            ))}
                        </select>
                        <button
                            onClick={onRefresh}
                            disabled={isSpinning}
                            className="p-1 hover:bg-slate-700/50 rounded-md transition-colors disabled:opacity-50"
                            aria-label="Refresh data"
                        >
                            <svg
                                xmlns="http://www.w3.org/2000/svg"
                                width="16"
                                height="16"
                                viewBox="0 0 24 24"
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="2"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                className={isSpinning ? 'animate-spin' : 'hover:text-blue-400 transition-colors'}
                            >
                                <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" />
                                <path d="M21 3v5h-5" />
                                <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" />
                                <path d="M8 16H3v5" />
                            </svg>
                        </button>
                    </div>
                    <Button variant="ghost">Docs</Button>
                    <Button variant="ghost">Status</Button>
                    <div className="text-slate-400 text-xs hidden sm:block">
                        <span className="opacity-70">Updated </span>
                        {lastUpdated}
                    </div>
                </div>
            </div>
        </nav>
    )
}
