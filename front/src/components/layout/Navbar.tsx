
import type { ChangeEvent, JSX } from 'react'

const REFRESH_OPTIONS = [5, 10, 20, 30] as const

interface NavbarProps {
    refreshInterval: number
    onIntervalChange: (event: ChangeEvent<HTMLSelectElement>) => void
    onRefresh: () => void
    isSpinning: boolean
    lastUpdated: string
    currentView: 'live' | 'dashboard'
    onNavigate: (view: 'live' | 'dashboard') => void
    onLogout?: () => void
    userLabel?: string
}

export function Navbar({
    refreshInterval,
    onIntervalChange,
    onRefresh,
    isSpinning,
    lastUpdated,
    currentView,
    onNavigate,
    onLogout,
    userLabel,
}: Readonly<NavbarProps>): JSX.Element {
    return (
        <nav className="bg-slate-950/90 border-slate-900/70 border-b">
            <div className="flex justify-between items-center gap-4 mx-auto mr-2 px-6 py-4 max-w-6xl">
                <div>
                    <p className="text-cyan-300/70 text-xs uppercase tracking-[0.35em]">Fast Pin Pon</p>
                    <p className="font-semibold text-white text-lg">{currentView === 'live' ? 'Live events' : 'Dashboard'}</p>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                    <div className="flex items-center gap-2 bg-slate-900/70 border border-slate-800 rounded-full px-1 py-1">
                        <button
                            type="button"
                            onClick={() => onNavigate('live')}
                            className={`px-3 py-1 text-xs font-semibold rounded-full transition ${
                                currentView === 'live'
                                    ? 'bg-slate-100 text-slate-950 shadow'
                                    : 'text-slate-300 hover:text-white'
                            }`}
                            aria-pressed={currentView === 'live'}
                        >
                            Live
                        </button>
                        <button
                            type="button"
                            onClick={() => onNavigate('dashboard')}
                            className={`px-3 py-1 text-xs font-semibold rounded-full transition ${
                                currentView === 'dashboard'
                                    ? 'bg-slate-100 text-slate-950 shadow'
                                    : 'text-slate-300 hover:text-white'
                            }`}
                            aria-pressed={currentView === 'dashboard'}
                        >
                            Dashboard
                        </button>
                    </div>
                    <div className="flex items-center gap-2 text-slate-400 text-xs">
                        <div className="hidden sm:block text-slate-400 text-xs">
                            <span className="opacity-70">Updated </span>
                            {lastUpdated}
                        </div>
                        <span className="hidden sm:inline">Auto-refresh:</span>
                        <select
                            value={refreshInterval}
                            onChange={onIntervalChange}
                            className="bg-slate-800/80 px-2 py-1.5 border border-blue-500/20 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/40 text-slate-200 text-xs cursor-pointer"
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
                            className="hover:bg-slate-700/50 disabled:opacity-50 p-1 rounded-md transition-colors cursor-pointer"
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
                    {userLabel && (
                        <button
                            onClick={onLogout}
                            className="flex items-center gap-2 bg-slate-800/60 hover:bg-slate-700/80 px-3 py-1.5 rounded-lg text-slate-300 text-xs transition-colors cursor-pointer"
                            aria-label="Se déconnecter"
                            title="Déconnexion"
                        >
                            <span className="inline-flex bg-emerald-400 rounded-full w-2 h-2" aria-hidden="true" />
                            <span className="max-w-[140px] truncate" title={userLabel}>{userLabel}</span>
                            <svg
                                xmlns="http://www.w3.org/2000/svg"
                                width="14"
                                height="14"
                                viewBox="0 0 24 24"
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="2"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                className="flex-shrink-0 ml-1"
                            >
                                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                                <polyline points="16 17 21 12 16 7" />
                                <line x1="21" y1="12" x2="9" y2="12" />
                            </svg>
                        </button>
                    )}
                </div>
            </div>
        </nav>
    )
}
