import type { UnitSummary } from '../../types'

// Unit marker size in pixels
const UNIT_SIZE = 22

// Unit type icons - SVG strings (use viewBox only, size set by container)
export const UNIT_TYPE_ICONS: Record<string, string> = {
    FPT: `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="#0095FF" stroke="black" stroke-width="6"/>
  <path d="M 50 80 Q 20 60 50 20 Q 80 60 50 80 Z" fill="none" stroke="white" stroke-width="6" stroke-linejoin="round" stroke-linecap="round"/>
  <path d="M 50 80 Q 35 65 50 45" fill="none" stroke="white" stroke-width="6" stroke-linecap="round"/>
</svg>`,

    FPTL: `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="#0095FF" stroke="black" stroke-width="6"/>
  <polygon points="58,15 28,50 48,50 42,85 72,45 52,45" fill="none" stroke="white" stroke-width="6" stroke-linejoin="round" stroke-linecap="round"/>
</svg>`,

    VER: `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="#0095FF" stroke="black" stroke-width="6"/>
  <path d="M 30 70 L 45 50 L 30 30" fill="none" stroke="white" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M 70 70 L 55 50 L 70 30" fill="none" stroke="white" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="50" cy="50" r="5" fill="white"/>
</svg>`,

    VIA: `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="#0095FF" stroke="black" stroke-width="6"/>
  <circle cx="50" cy="50" r="8" fill="white"/>
  <line x1="50" y1="20" x2="50" y2="80" stroke="white" stroke-width="8" stroke-linecap="round"/>
  <line x1="24" y1="65" x2="76" y2="35" stroke="white" stroke-width="8" stroke-linecap="round"/>
  <circle cx="50" cy="50" r="32" fill="none" stroke="white" stroke-width="4" stroke-dasharray="10, 10"/>
</svg>`,

    VIM: `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="#0095FF" stroke="black" stroke-width="6"/>
  <line x1="50" y1="20" x2="50" y2="70" stroke="white" stroke-width="6" stroke-linecap="round"/>
  <line x1="40" y1="25" x2="60" y2="25" stroke="white" stroke-width="6" stroke-linecap="round"/>
  <path d="M 25 55 Q 50 90 75 55" fill="none" stroke="white" stroke-width="6" stroke-linecap="round"/>
</svg>`,

    VSAV: `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="#0095FF" stroke="black" stroke-width="6"/>
  <line x1="50" y1="25" x2="50" y2="75" stroke="white" stroke-width="8" stroke-linecap="round"/>
  <line x1="25" y1="50" x2="75" y2="50" stroke="white" stroke-width="8" stroke-linecap="round"/>
</svg>`,

    VLHR: `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="#0095FF" stroke="black" stroke-width="6"/>
  <line x1="50" y1="20" x2="50" y2="80" stroke="white" stroke-width="8" stroke-linecap="round"/>
  <line x1="24" y1="35" x2="76" y2="65" stroke="white" stroke-width="8" stroke-linecap="round"/>
  <line x1="24" y1="65" x2="76" y2="35" stroke="white" stroke-width="8" stroke-linecap="round"/>
</svg>`,

    EPA: `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="#0095FF" stroke="black" stroke-width="6"/>
  <line x1="35" y1="80" x2="35" y2="20" stroke="white" stroke-width="6" stroke-linecap="round"/>
  <line x1="65" y1="80" x2="65" y2="20" stroke="white" stroke-width="6" stroke-linecap="round"/>
  <line x1="35" y1="35" x2="65" y2="35" stroke="white" stroke-width="6" stroke-linecap="round"/>
  <line x1="35" y1="50" x2="65" y2="50" stroke="white" stroke-width="6" stroke-linecap="round"/>
  <line x1="35" y1="65" x2="65" y2="65" stroke="white" stroke-width="6" stroke-linecap="round"/>
</svg>`,
}

// Default unit icon for unknown types
export const DEFAULT_UNIT_ICON = `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="#0095FF" stroke="black" stroke-width="6"/>
  <circle cx="50" cy="50" r="15" fill="white"/>
</svg>`

// Unit type icon templates - functions that return SVG with dynamic color
export const UNIT_TYPE_ICON_TEMPLATES: Record<string, (color: string) => string> = {
    FPT: (color) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="${color}" stroke="black" stroke-width="6"/>
  <path d="M 50 80 Q 20 60 50 20 Q 80 60 50 80 Z" fill="none" stroke="white" stroke-width="6" stroke-linejoin="round" stroke-linecap="round"/>
  <path d="M 50 80 Q 35 65 50 45" fill="none" stroke="white" stroke-width="6" stroke-linecap="round"/>
</svg>`,
    FPTL: (color) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="${color}" stroke="black" stroke-width="6"/>
  <polygon points="58,15 28,50 48,50 42,85 72,45 52,45" fill="none" stroke="white" stroke-width="6" stroke-linejoin="round" stroke-linecap="round"/>
</svg>`,
    VER: (color) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="${color}" stroke="black" stroke-width="6"/>
  <path d="M 30 70 L 45 50 L 30 30" fill="none" stroke="white" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M 70 70 L 55 50 L 70 30" fill="none" stroke="white" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="50" cy="50" r="5" fill="white"/>
</svg>`,
    VIA: (color) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="${color}" stroke="black" stroke-width="6"/>
  <circle cx="50" cy="50" r="8" fill="white"/>
  <line x1="50" y1="20" x2="50" y2="80" stroke="white" stroke-width="8" stroke-linecap="round"/>
  <line x1="24" y1="65" x2="76" y2="35" stroke="white" stroke-width="8" stroke-linecap="round"/>
  <circle cx="50" cy="50" r="32" fill="none" stroke="white" stroke-width="4" stroke-dasharray="10, 10"/>
</svg>`,
    VIM: (color) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="${color}" stroke="black" stroke-width="6"/>
  <line x1="50" y1="20" x2="50" y2="70" stroke="white" stroke-width="6" stroke-linecap="round"/>
  <line x1="40" y1="25" x2="60" y2="25" stroke="white" stroke-width="6" stroke-linecap="round"/>
  <path d="M 25 55 Q 50 90 75 55" fill="none" stroke="white" stroke-width="6" stroke-linecap="round"/>
</svg>`,
    VSAV: (color) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="${color}" stroke="black" stroke-width="6"/>
  <line x1="50" y1="25" x2="50" y2="75" stroke="white" stroke-width="8" stroke-linecap="round"/>
  <line x1="25" y1="50" x2="75" y2="50" stroke="white" stroke-width="8" stroke-linecap="round"/>
</svg>`,
    VLHR: (color) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="${color}" stroke="black" stroke-width="6"/>
  <line x1="50" y1="20" x2="50" y2="80" stroke="white" stroke-width="8" stroke-linecap="round"/>
  <line x1="24" y1="35" x2="76" y2="65" stroke="white" stroke-width="8" stroke-linecap="round"/>
  <line x1="24" y1="65" x2="76" y2="35" stroke="white" stroke-width="8" stroke-linecap="round"/>
</svg>`,
    EPA: (color) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="${color}" stroke="black" stroke-width="6"/>
  <line x1="35" y1="80" x2="35" y2="20" stroke="white" stroke-width="6" stroke-linecap="round"/>
  <line x1="65" y1="80" x2="65" y2="20" stroke="white" stroke-width="6" stroke-linecap="round"/>
  <line x1="35" y1="35" x2="65" y2="35" stroke="white" stroke-width="6" stroke-linecap="round"/>
  <line x1="35" y1="50" x2="65" y2="50" stroke="white" stroke-width="6" stroke-linecap="round"/>
  <line x1="35" y1="65" x2="65" y2="65" stroke="white" stroke-width="6" stroke-linecap="round"/>
</svg>`,
}

// Default unit icon template
const DEFAULT_UNIT_ICON_TEMPLATE = (color: string) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="45" fill="${color}" stroke="black" stroke-width="6"/>
  <circle cx="50" cy="50" r="15" fill="white"/>
</svg>`

/**
 * Get the unit icon SVG with the appropriate status color
 */
export function getUnitIconWithStatus(unitTypeCode: string, status?: string): string {
    const color = status ? (UNIT_STATUS_COLORS[status.toLowerCase()] ?? UNIT_STATUS_COLORS.offline) : '#0095FF'
    const template = UNIT_TYPE_ICON_TEMPLATES[unitTypeCode.toUpperCase()]
    return template ? template(color) : DEFAULT_UNIT_ICON_TEMPLATE(color)
}

// Event marker size in pixels
const EVENT_SIZE = 28

// Severity color mapping
export const SEVERITY_COLORS: Record<string, string> = {
    low: '#facc15',      // Yellow (was green, but yellow indicates there's still a problem)
    medium: '#f59e0b',   // Orange/Amber
    high: '#E60000',     // Red
}

// Unit status color mapping (matches format.ts STATUS_COLORS)
export const UNIT_STATUS_COLORS: Record<string, string> = {
    available: '#22c55e',    // Green - Ready for dispatch
    under_way: '#eab308',    // Yellow - En route
    on_site: '#3b82f6',      // Blue - On scene
    unavailable: '#ef4444',  // Red - Not available
    offline: '#6b7280',      // Gray - Disconnected
}

/**
 * Get the color for a given severity level
 */
export function getSeverityColor(severity?: number): string {
    if (severity === undefined) return SEVERITY_COLORS.medium
    if (severity <= 2) return SEVERITY_COLORS.low
    if (severity === 3) return SEVERITY_COLORS.medium
    return SEVERITY_COLORS.high
}

// Event type icon templates - functions that return SVG with dynamic color
export const EVENT_TYPE_ICON_TEMPLATES: Record<string, (color: string) => string> = {
    CRASH: (color) => `<svg viewBox="0 0 100 100">
  <polygon points="50,5 95,50 50,95 5,50" fill="${color}" stroke="black" stroke-width="6"/>
  <path d="M 25 50 L 45 50 M 75 50 L 55 50" stroke="white" stroke-width="8" stroke-linecap="round"/>
  <path d="M 40 35 L 50 50 L 40 65 M 60 35 L 50 50 L 60 65" fill="none" stroke="white" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
</svg>`,

    OTHER: (color) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <polygon points="50,5 95,50 50,95 5,50" fill="${color}" stroke="black" stroke-width="6"/>
  <circle cx="30" cy="50" r="5" fill="white"/>
  <circle cx="50" cy="50" r="5" fill="white"/>
  <circle cx="70" cy="50" r="5" fill="white"/>
</svg>`,

    FIRE_INDUSTRIAL: (color) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <polygon points="50,5 95,50 50,95 5,50" fill="${color}" stroke="black" stroke-width="6"/>
  <path d="M30 70 V50 L40 50 V40 L50 40 V30 L70 30 V70 Z" fill="none" stroke="white" stroke-width="4"/>
  <path d="M50 75 Q40 60 50 45 Q60 60 50 75" fill="white"/>
</svg>`,

    AQUATIC_RESCUE: (color) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <polygon points="50,5 95,50 50,95 5,50" fill="${color}" stroke="black" stroke-width="6"/>
  <circle cx="50" cy="50" r="20" fill="none" stroke="white" stroke-width="8"/>
  <path d="M38 28 Q50 18 62 28" fill="none" stroke="white" stroke-width="3" stroke-linecap="round"/>
  <path d="M38 72 Q50 82 62 72" fill="none" stroke="white" stroke-width="3" stroke-linecap="round"/>
  <path d="M28 38 Q18 50 28 62" fill="none" stroke="white" stroke-width="3" stroke-linecap="round"/>
  <path d="M72 38 Q82 50 72 62" fill="none" stroke="white" stroke-width="3" stroke-linecap="round"/>
</svg>`,

    HAZMAT: (color) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <polygon points="50,5 95,50 50,95 5,50" fill="${color}" stroke="black" stroke-width="6"/>
  <path d="M50 30 Q35 30 35 45 Q35 58 50 58 Q65 58 65 45 Q65 30 50 30" fill="white"/>
  <rect x="44" y="55" width="12" height="8" rx="2" fill="white"/>
  <circle cx="43" cy="45" r="4" fill="${color}"/>
  <circle cx="57" cy="45" r="4" fill="${color}"/>
</svg>`,

    FIRE_URBAN: (color) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <polygon points="50,5 95,50 50,95 5,50" fill="${color}" stroke="black" stroke-width="6"/>
  <path d="M42 70 V35 Q50 25 58 35 V70 M35 45 H65" fill="none" stroke="white" stroke-width="6" stroke-linecap="round"/>
</svg>`,

    RESCUE_MEDICAL: (color) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <polygon points="50,5 95,50 50,95 5,50" fill="${color}" stroke="black" stroke-width="6"/>
  <path d="M25 50 H35 L42 30 L53 70 L60 50 H75" fill="none" stroke="white" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
</svg>`,
}

// Default event icon template
export const DEFAULT_EVENT_ICON_TEMPLATE = (color: string) => `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <polygon points="50,5 95,50 50,95 5,50" fill="${color}" stroke="black" stroke-width="6"/>
  <circle cx="50" cy="45" r="8" fill="white"/>
  <rect x="46" y="58" width="8" height="15" rx="2" fill="white"/>
</svg>`

// Static versions for legend (using high severity color)
export const EVENT_TYPE_ICONS: Record<string, string> = Object.fromEntries(
    Object.entries(EVENT_TYPE_ICON_TEMPLATES).map(([key, template]) => [key, template(SEVERITY_COLORS.high)])
)

export const DEFAULT_EVENT_ICON = DEFAULT_EVENT_ICON_TEMPLATE(SEVERITY_COLORS.high)

/**
 * Get the SVG icon for a unit type
 */
export function getUnitIcon(unitTypeCode: string): string {
    return UNIT_TYPE_ICONS[unitTypeCode.toUpperCase()] ?? DEFAULT_UNIT_ICON
}

/**
 * Get the SVG icon for an event type (static, for legend)
 */
export function getEventIcon(eventTypeCode: string): string {
    return EVENT_TYPE_ICONS[eventTypeCode.toUpperCase()] ?? DEFAULT_EVENT_ICON
}

/**
 * Get the SVG icon for an event type with dynamic severity color
 */
export function getEventIconWithSeverity(eventTypeCode: string, severity?: number): string {
    const color = getSeverityColor(severity)
    const template = EVENT_TYPE_ICON_TEMPLATES[eventTypeCode.toUpperCase()]
    return template ? template(color) : DEFAULT_EVENT_ICON_TEMPLATE(color)
}

/**
 * Create a unit marker element with the appropriate icon and status color
 */
export function createUnitMarkerElement(unitTypeCode: string, status?: string): HTMLDivElement {
    const wrapper = document.createElement('div')
    wrapper.className = 'unit-marker'
    wrapper.style.cssText = `
        width: ${UNIT_SIZE}px;
        height: ${UNIT_SIZE}px;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
    `

    const svgContainer = document.createElement('div')
    svgContainer.style.cssText = `
        width: 100%;
        height: 100%;
        transition: transform 0.15s ease;
        display: flex;
        align-items: center;
        justify-content: center;
    `
    svgContainer.innerHTML = getUnitIconWithStatus(unitTypeCode, status)

    // Make SVG fill container
    const svg = svgContainer.querySelector('svg')
    if (svg) {
        svg.style.width = '100%'
        svg.style.height = '100%'
        svg.style.display = 'block'
    }

    wrapper.appendChild(svgContainer)

    wrapper.addEventListener('mouseenter', () => {
        svgContainer.style.transform = 'scale(1.15)'
    })
    wrapper.addEventListener('mouseleave', () => {
        svgContainer.style.transform = 'scale(1)'
    })

    return wrapper
}

// Size for unit badges displayed under events
const UNIT_BADGE_SIZE = 18

/**
 * Group units by their type code and return an array of [typeCode, count]
 */
function groupUnitsByType(units: UnitSummary[]): Array<{ typeCode: string; count: number }> {
    const counts = new Map<string, number>()
    for (const unit of units) {
        // Normalize type code: uppercase and trim whitespace
        const code = (unit.unit_type_code ?? '').toUpperCase().trim()
        if (code) {
            counts.set(code, (counts.get(code) ?? 0) + 1)
        }
    }
    return Array.from(counts.entries()).map(([typeCode, count]) => ({ typeCode, count }))
}

/**
 * Create a small unit badge element with optional count indicator
 */
function createUnitBadge(typeCode: string, count: number): HTMLDivElement {
    const badge = document.createElement('div')
    badge.style.cssText = `
        position: relative;
        width: ${UNIT_BADGE_SIZE}px;
        height: ${UNIT_BADGE_SIZE}px;
        flex-shrink: 0;
        margin: 0 2px;
    `

    // Unit icon (use on_site color - blue)
    const iconContainer = document.createElement('div')
    iconContainer.style.cssText = `
        width: 100%;
        height: 100%;
    `
    iconContainer.innerHTML = getUnitIconWithStatus(typeCode, 'on_site')
    const svg = iconContainer.querySelector('svg')
    if (svg) {
        svg.style.width = '100%'
        svg.style.height = '100%'
        svg.style.display = 'block'
    }
    badge.appendChild(iconContainer)

    // Count badge (only if count > 1)
    if (count > 1) {
        const countBadge = document.createElement('div')
        countBadge.textContent = String(count)
        countBadge.style.cssText = `
            position: absolute;
            top: -6px;
            right: -6px;
            min-width: 16px;
            height: 16px;
            background: #facc15;
            color: #000;
            font-size: 11px;
            font-weight: 700;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            border: 2px solid #fff;
            box-shadow: 0 1px 3px rgba(0,0,0,0.4);
            padding: 0 3px;
            line-height: 1;
        `
        badge.appendChild(countBadge)
    }

    return badge
}

/**
 * Create an event marker element with the appropriate icon and severity color.
 * Optionally displays on-site units grouped by type below the event icon.
 */
export function createEventMarkerElement(
    eventTypeCode: string,
    isSelected: boolean,
    severity?: number,
    onSiteUnits?: UnitSummary[],
): HTMLDivElement {
    const hasUnits = onSiteUnits && onSiteUnits.length > 0
    const groupedUnits = hasUnits ? groupUnitsByType(onSiteUnits) : []

    const wrapper = document.createElement('div')
    wrapper.className = 'event-marker'
    wrapper.style.cssText = `
        display: flex;
        flex-direction: column;
        align-items: center;
        cursor: pointer;
    `

    // Event icon container
    const eventContainer = document.createElement('div')
    eventContainer.style.cssText = `
        width: ${EVENT_SIZE}px;
        height: ${EVENT_SIZE}px;
        display: flex;
        align-items: center;
        justify-content: center;
    `

    const svgContainer = document.createElement('div')
    svgContainer.style.cssText = `
        width: 100%;
        height: 100%;
        transition: transform 0.15s ease;
        display: flex;
        align-items: center;
        justify-content: center;
        ${isSelected ? 'filter: drop-shadow(0 0 6px rgba(59,130,246,0.9));' : ''}
    `
    svgContainer.innerHTML = getEventIconWithSeverity(eventTypeCode, severity)

    const svg = svgContainer.querySelector('svg')
    if (svg) {
        svg.style.width = '100%'
        svg.style.height = '100%'
        svg.style.display = 'block'
    }

    eventContainer.appendChild(svgContainer)
    wrapper.appendChild(eventContainer)

    // Unit badges row (if units are on site)
    if (groupedUnits.length > 0) {
        const unitsRow = document.createElement('div')
        unitsRow.style.cssText = `
            display: flex;
            flex-wrap: wrap;
            gap: 4px;
            margin-top: 4px;
            justify-content: center;
        `
        for (const { typeCode, count } of groupedUnits) {
            unitsRow.appendChild(createUnitBadge(typeCode, count))
        }
        wrapper.appendChild(unitsRow)
    }

    wrapper.addEventListener('mouseenter', () => {
        svgContainer.style.transform = 'scale(1.15)'
    })
    wrapper.addEventListener('mouseleave', () => {
        svgContainer.style.transform = 'scale(1)'
    })

    return wrapper
}

// Building (caserne) icon SVG (static)
const BUILDING_ICON_SVG = `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <rect x="8" y="8" width="84" height="84" rx="8" fill="#9146FF" stroke="white" stroke-width="6"/>
  <circle cx="50" cy="50" r="30" fill="none" stroke="white" stroke-width="2" stroke-dasharray="4,4"/>
  <path d="M40 65 V45 L50 35 L60 45 V65 H40" fill="white"/>
  <rect x="47" y="55" width="6" height="10" fill="#9146FF"/>
</svg>`

const BUILDING_SIZE = 24

export function createBuildingMarkerElement(): HTMLDivElement {
  const wrapper = document.createElement('div')
  wrapper.className = 'building-marker'
  wrapper.style.cssText = `
    width: ${BUILDING_SIZE}px;
    height: ${BUILDING_SIZE}px;
    display: flex;
    align-items: center;
    justify-content: center;
  `

  const svgContainer = document.createElement('div')
  svgContainer.style.cssText = `
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  `
  svgContainer.innerHTML = BUILDING_ICON_SVG

  const svg = svgContainer.querySelector('svg')
  if (svg) {
    svg.style.width = '100%'
    svg.style.height = '100%'
    svg.style.display = 'block'
  }

  wrapper.appendChild(svgContainer)
  return wrapper
}
