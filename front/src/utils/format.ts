export const STATUS_COLORS: Record<string, string> = {
    available: '#22c55e',
    under_way: '#eab308',
    on_site: '#3b82f6',
    unavailable: '#ef4444',
    offline: '#6b7280',
}

export const formatTimestamp = (value?: string): string =>
    value
        ? new Date(value).toLocaleString('fr-FR', {
            hour: 'numeric',
            minute: '2-digit',
            month: 'short',
            day: 'numeric',
        })
        : 'Inconnu'

export const severityLabel = (severity?: number): string => {
    if (severity === undefined) return 'En attente'
    if (severity <= 2) return 'Faible'
    if (severity === 3) return 'Moyen'
    return 'Élevé'
}
