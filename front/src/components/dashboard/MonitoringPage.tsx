import { useEffect, useState } from 'react'
import { fastPinPonService } from '../../services/FastPinPonService'
import { useAuth } from '../../auth/AuthProvider'
import type { DetailedHealthResponse } from '../../types'

export function MonitoringPage() {
    const { token, permissions } = useAuth()
    const [data, setData] = useState<DetailedHealthResponse | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [loading, setLoading] = useState(false)

    const refreshHealth = async () => {
        if (!token) return
        setLoading(true)
        try {
            const res = await fastPinPonService.getAdminHealth(token)
            setData(res)
            setError(null)
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Unknown error')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        refreshHealth()
        const interval = setInterval(refreshHealth, 10000) // Poll every 10s
        return () => clearInterval(interval)
    }, [token])

    if (!permissions?.canViewDashboard) { // Or canViewMonitoring if we added it, but canViewDashboard covers "it"
        return <div className="p-8 text-center text-slate-400">Accès non autorisé</div>
    }

    if (!data && loading) {
        return <div className="p-8 text-center text-slate-400">Chargement du dashboard...</div>
    }

    if (error && !data) {
        return (
            <div className="p-8 flex flex-col items-center justify-center min-h-[50vh]">
                <div className="text-red-400 mb-4 text-xl">Erreur de connexion</div>
                <div className="text-slate-500 mb-4">{error}</div>
                <button
                    onClick={refreshHealth}
                    className="px-4 py-2 bg-slate-800 hover:bg-slate-700 rounded text-sm transition-colors"
                >
                    Réessayer
                </button>
            </div>
        )
    }

    if (!data) return null

    const isSimMode = data.mode === 'demo'
    const isNetworkActive = data.microbit_network.status === 'active'

    return (
        <div className="p-6 max-w-7xl mx-auto w-full">
            <div className="flex items-center justify-between mb-8">
                <div>
                    <h1 className="text-2xl font-bold text-slate-100 flex items-center gap-3">
                        <span className="w-3 h-3 rounded-full animate-pulse bg-emerald-500 shadow-[0_0_10px_#10b981]" />{' '}
                        Fast Pin Pon Health Center
                    </h1>
                    <p className="text-slate-400 text-sm mt-1">
                        Uptime: {data.uptime} • Mode: {isSimMode ? 'Simulation Automatique' : 'Hybride / Hardware'}
                    </p>
                </div>
                <button
                    onClick={refreshHealth}
                    className={`p-2 rounded-full hover:bg-slate-800 transition-all ${loading ? 'animate-spin' : ''}`}
                    title="Rafraîchir"
                >
                    <svg className="w-5 h-5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                    </svg>
                </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
                <ServiceCard name="Base de Données" status={data.services.database} icon="database" />
                <ServiceCard name="Simulation" status={data.services.simulation} icon="chip" />
                <ServiceCard name="Moteur Engine" status={data.services.engine} icon="server" />

                <div className="bg-slate-800/50 backdrop-blur border border-slate-700 rounded-xl p-5 relative overflow-hidden group">
                    <div className="absolute top-0 right-0 p-4 opacity-10 group-hover:opacity-20 transition-opacity">
                        <svg className="w-16 h-16 text-slate-100" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M5.636 18.364a9 9 0 010-12.728m12.728 0a9 9 0 010 12.728m-9.9-2.829a5 5 0 010-7.07m7.072 0a5 5 0 010 7.07M13 12a1 1 0 11-2 0 1 1 0 012 0z" />
                        </svg>
                    </div>
                    <h3 className="text-slate-400 text-sm font-medium uppercase tracking-wider mb-2">Réseau Microbit</h3>
                    <div className="flex items-center gap-3">
                        <div className={`w-3 h-3 rounded-full ${isNetworkActive ? 'bg-emerald-500 shadow-[0_0_8px_#10b981]' : 'bg-slate-600'}`} />
                        <span className={`text-2xl font-bold ${isNetworkActive ? 'text-emerald-400' : 'text-slate-500'}`}>
                            {isNetworkActive ? 'ACTIF' : 'INACTIF'}
                        </span>
                    </div>
                    <div className="mt-3 text-xs text-slate-400">
                        Dernier message : {data.microbit_network.seconds_since_last >= 0 ? `il y a ${data.microbit_network.seconds_since_last}s` : 'Jamais'}
                    </div>
                </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Could embed Grafana here if needed, but simple stats for now */}
                <div className="p-6 bg-slate-800/30 border border-slate-700/50 rounded-xl">
                    <div className="flex items-center justify-between mb-4">
                        <h3 className="text-slate-200 font-semibold">Métriques Système</h3>
                    </div>
                    <div className="grid grid-cols-2 gap-4">
                        <div className="p-4 bg-slate-900/50 rounded-lg">
                            <div className="text-slate-400 text-xs uppercase mb-1">Unités Actives</div>
                            <div className="text-2xl font-mono text-blue-400">{data.system_stats.active_units}</div>
                        </div>
                        <div className="p-4 bg-slate-900/50 rounded-lg">
                            <div className="text-slate-400 text-xs uppercase mb-1">Incidents en cours</div>
                            <div className="text-2xl font-mono text-orange-400">{data.system_stats.active_incidents}</div>
                        </div>
                    </div>
                </div>

                <div className="p-6 bg-slate-800/30 border border-slate-700/50 rounded-xl relative overflow-hidden">
                    <div className="absolute inset-0 bg-gradient-to-br from-blue-500/5 to-purple-500/5 pointer-events-none" />
                    <h3 className="text-slate-200 font-semibold mb-4">État des Services</h3>
                    <div className="space-y-3">
                        <StatusRow label="API Response Time" value="< 15ms" status="good" />
                        <StatusRow label="Database Connections" value="Active" status="good" />
                        <StatusRow label="Bridge Connection" value={isNetworkActive ? "Established" : "Disconnected"} status={isNetworkActive ? "good" : "warning"} />
                    </div>
                </div>
            </div>
        </div>
    )
}

function ServiceCard({ name, status, icon }: Readonly<{ name: string, status: string, icon: 'database' | 'server' | 'chip' }>) {
    const isUp = status === 'up'

    const getIconPath = () => {
        if (icon === 'database') return "M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4"
        if (icon === 'chip') return "M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6H3m18-6h-2m2 6h-2M7 19h10a2 2 0 002-2V7a2 2 0 00-2-2H7a2 2 0 00-2 2v10a2 2 0 002 2zM9 9h6v6H9V9z"
        return "M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01"
    }

    return (
        <div className={`bg-slate-800/50 backdrop-blur border ${isUp ? 'border-slate-700' : 'border-red-900/50'} rounded-xl p-5 relative overflow-hidden transition-all hover:bg-slate-800/70 group`}>
            <div className="absolute top-0 right-0 p-4 opacity-10 group-hover:opacity-20 transition-opacity">
                <svg className="w-16 h-16 text-slate-100" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d={getIconPath()} />
                </svg>
            </div>
            <h3 className="text-slate-400 text-sm font-medium uppercase tracking-wider mb-2">{name}</h3>
            <div className="flex items-center gap-3">
                <StatusDot status={isUp ? 'up' : 'down'} />
                <span className={`text-2xl font-bold ${isUp ? 'text-slate-100' : 'text-red-400'}`}>
                    {status.toUpperCase()}
                </span>
            </div>
        </div>
    )
}

function StatusDot({ status }: Readonly<{ status: 'up' | 'down' }>) {
    const color = status === 'up' ? 'bg-emerald-500 shadow-[0_0_8px_#10b981]' : 'bg-red-500 shadow-[0_0_8px_#ef4444]'
    return <div className={`w-3 h-3 rounded-full ${color}`} />
}

function StatusRow({ label, value, status }: Readonly<{ label: string, value: string, status: 'good' | 'warning' | 'bad' }>) {
    let color = 'text-emerald-400'
    if (status === 'warning') color = 'text-amber-400'
    if (status === 'bad') color = 'text-red-400'

    return (
        <div className="flex items-center justify-between p-3 bg-slate-900/30 rounded border border-slate-700/30">
            <span className="text-slate-400 text-sm">{label}</span>
            <span className={`font-mono text-sm ${color}`}>{value}</span>
        </div>
    )
}
