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

// Event marker size in pixels
const EVENT_SIZE = 28

// Event type icons - SVG strings (use viewBox only, size set by container)
export const EVENT_TYPE_ICONS: Record<string, string> = {
    CRASH: `<svg viewBox="0 0 100 100">
  <polygon points="50,5 95,50 50,95 5,50" fill="#FF4444" stroke="black" stroke-width="6"/>
  <path d="M 25 50 L 45 50 M 75 50 L 55 50" stroke="white" stroke-width="8" stroke-linecap="round"/>
  <path d="M 40 35 L 50 50 L 40 65 M 60 35 L 50 50 L 60 65" fill="none" stroke="white" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
</svg>`,

    OTHER: `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <polygon points="50,5 95,50 50,95 5,50" fill="#E60000" stroke="black" stroke-width="6"/>
  <circle cx="30" cy="50" r="5" fill="white"/>
  <circle cx="50" cy="50" r="5" fill="white"/>
  <circle cx="70" cy="50" r="5" fill="white"/>
</svg>`,

    FIRE_INDUSTRIAL: `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <polygon points="50,5 95,50 50,95 5,50" fill="#E60000" stroke="black" stroke-width="6"/>
  <circle cx="50" cy="50" r="20" fill="none" stroke="white" stroke-width="8"/>
  <path d="M38 28 Q50 18 62 28" fill="none" stroke="white" stroke-width="3" stroke-linecap="round"/>
  <path d="M38 72 Q50 82 62 72" fill="none" stroke="white" stroke-width="3" stroke-linecap="round"/>
  <path d="M28 38 Q18 50 28 62" fill="none" stroke="white" stroke-width="3" stroke-linecap="round"/>
  <path d="M72 38 Q82 50 72 62" fill="none" stroke="white" stroke-width="3" stroke-linecap="round"/>
</svg>`,

    AQUATIC_RESCUE: `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <polygon points="50,5 95,50 50,95 5,50" fill="#E60000" stroke="black" stroke-width="6"/>
  <circle cx="50" cy="50" r="20" fill="none" stroke="white" stroke-width="8"/>
  <path d="M38 28 Q50 18 62 28" fill="none" stroke="white" stroke-width="3" stroke-linecap="round"/>
  <path d="M38 72 Q50 82 62 72" fill="none" stroke="white" stroke-width="3" stroke-linecap="round"/>
  <path d="M28 38 Q18 50 28 62" fill="none" stroke="white" stroke-width="3" stroke-linecap="round"/>
  <path d="M72 38 Q82 50 72 62" fill="none" stroke="white" stroke-width="3" stroke-linecap="round"/>
</svg>`,

    HAZMAT: `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <polygon points="50,5 95,50 50,95 5,50" fill="#E60000" stroke="black" stroke-width="6"/>
  <path d="M50 30 Q35 30 35 45 Q35 58 50 58 Q65 58 65 45 Q65 30 50 30" fill="white"/>
  <rect x="44" y="55" width="12" height="8" rx="2" fill="white"/>
  <circle cx="43" cy="45" r="4" fill="#E60000"/>
  <circle cx="57" cy="45" r="4" fill="#E60000"/>
</svg>`,

    FIRE_URBAN: `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <polygon points="50,5 95,50 50,95 5,50" fill="#E60000" stroke="black" stroke-width="6"/>
  <path d="M42 70 V35 Q50 25 58 35 V70 M35 45 H65" fill="none" stroke="white" stroke-width="6" stroke-linecap="round"/>
</svg>`,

    RESCUE_MEDICAL: `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <polygon points="50,5 95,50 50,95 5,50" fill="#E60000" stroke="black" stroke-width="6"/>
  <path d="M25 50 H35 L42 30 L53 70 L60 50 H75" fill="none" stroke="white" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
</svg>`,
}

// Default event icon for unknown types
export const DEFAULT_EVENT_ICON = `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <polygon points="50,5 95,50 50,95 5,50" fill="#E60000" stroke="black" stroke-width="6"/>
  <circle cx="50" cy="45" r="8" fill="white"/>
  <rect x="46" y="58" width="8" height="15" rx="2" fill="white"/>
</svg>`

/**
 * Get the SVG icon for a unit type
 */
export function getUnitIcon(unitTypeCode: string): string {
    return UNIT_TYPE_ICONS[unitTypeCode.toUpperCase()] ?? DEFAULT_UNIT_ICON
}

/**
 * Get the SVG icon for an event type
 */
export function getEventIcon(eventTypeCode: string): string {
    return EVENT_TYPE_ICONS[eventTypeCode.toUpperCase()] ?? DEFAULT_EVENT_ICON
}

/**
 * Create a unit marker element with the appropriate icon
 */
export function createUnitMarkerElement(unitTypeCode: string): HTMLDivElement {
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
    svgContainer.innerHTML = getUnitIcon(unitTypeCode)

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

/**
 * Create an event marker element with the appropriate icon
 */
export function createEventMarkerElement(eventTypeCode: string, isSelected: boolean): HTMLDivElement {
    const wrapper = document.createElement('div')
    wrapper.className = 'event-marker'
    wrapper.style.cssText = `
        width: ${EVENT_SIZE}px;
        height: ${EVENT_SIZE}px;
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
        ${isSelected ? 'filter: drop-shadow(0 0 6px rgba(59,130,246,0.9));' : ''}
    `
    svgContainer.innerHTML = getEventIcon(eventTypeCode)

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
