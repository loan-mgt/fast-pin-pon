import type { JSX } from 'react'
import { useState, useMemo, useCallback } from 'react'
import { Card } from '../ui/card'
import { Button } from '../ui/button'
import { fastPinPonService } from '../../services/FastPinPonService'

// Scoring weights configuration
interface ScoringWeights {
    travelTime: number
    coveragePenalty: number
    preemptionCost: number
    enRouteProgress: number
    reassignmentCost: number
}

// Test scenario data
interface TestScenario {
    id: string
    name: string
    description: string
    candidates: CandidateInput[]
    targetSeverity: number
}

// Input for a candidate to score
interface CandidateInput {
    id: string
    callSign: string
    travelTimeSeconds: number
    otherUnitsAtBase: number
    isAvailable: boolean
    isCurrentlyAssigned: boolean
    currentInterventionSeverity?: number
}

// Scored result
interface ScoredResult {
    id: string
    callSign: string
    travelScore: number
    coverageScore: number
    preemptionScore: number
    totalScore: number
    isDisqualified: boolean
}

// Default weights from the backend
const DEFAULT_WEIGHTS: ScoringWeights = {
    travelTime: 0.7,
    coveragePenalty: 1.5,
    preemptionCost: 5,
    enRouteProgress: 0.2,
    reassignmentCost: 85,
}

// Default thresholds
const DEFAULT_MIN_RESERVE_PER_BASE = 1
const DEFAULT_PREEMPTION_SEVERITY_THRESHOLD = 2

// Predefined test scenarios (travel times in seconds, 7-23 minutes range)
const PREDEFINED_SCENARIOS: TestScenario[] = [
    {
        id: 'scenario-1',
        name: 'Simple Dispatch',
        description: 'Two available units at different distances',
        targetSeverity: 3,
        candidates: [
            { id: '1', callSign: 'VSAV-01', travelTimeSeconds: 480, otherUnitsAtBase: 2, isAvailable: true, isCurrentlyAssigned: false },
            { id: '2', callSign: 'VSAV-02', travelTimeSeconds: 840, otherUnitsAtBase: 3, isAvailable: true, isCurrentlyAssigned: false },
        ],
    },
    {
        id: 'scenario-2',
        name: 'Coverage Penalty',
        description: 'Close unit would deplete base below reserve',
        targetSeverity: 3,
        candidates: [
            { id: '1', callSign: 'VSAV-01', travelTimeSeconds: 420, otherUnitsAtBase: 0, isAvailable: true, isCurrentlyAssigned: false },
            { id: '2', callSign: 'VSAV-02', travelTimeSeconds: 960, otherUnitsAtBase: 3, isAvailable: true, isCurrentlyAssigned: false },
        ],
    },
    {
        id: 'scenario-3',
        name: 'Preemption Decision',
        description: 'Available unit further, assigned unit closer on lower priority',
        targetSeverity: 5,
        candidates: [
            { id: '1', callSign: 'VSAV-01', travelTimeSeconds: 1380, otherUnitsAtBase: 2, isAvailable: true, isCurrentlyAssigned: false },
            { id: '2', callSign: 'VSAV-02', travelTimeSeconds: 540, otherUnitsAtBase: 2, isAvailable: false, isCurrentlyAssigned: true, currentInterventionSeverity: 2 },
        ],
    },
    {
        id: 'scenario-4',
        name: 'Mixed Scenario',
        description: 'Multiple candidates with different trade-offs',
        targetSeverity: 4,
        candidates: [
            { id: '1', callSign: 'VSAV-01', travelTimeSeconds: 480, otherUnitsAtBase: 0, isAvailable: true, isCurrentlyAssigned: false },
            { id: '2', callSign: 'VSAV-02', travelTimeSeconds: 660, otherUnitsAtBase: 2, isAvailable: true, isCurrentlyAssigned: false },
            { id: '3', callSign: 'VSAV-03', travelTimeSeconds: 900, otherUnitsAtBase: 4, isAvailable: true, isCurrentlyAssigned: false },
            { id: '4', callSign: 'FPT-01', travelTimeSeconds: 600, otherUnitsAtBase: 1, isAvailable: false, isCurrentlyAssigned: true, currentInterventionSeverity: 1 },
        ],
    },
]

export function ScoringConfigPage(): JSX.Element {
    const [weights, setWeights] = useState<ScoringWeights>(DEFAULT_WEIGHTS)
    const [minReservePerBase, setMinReservePerBase] = useState(DEFAULT_MIN_RESERVE_PER_BASE)
    const [preemptionThreshold, setPreemptionThreshold] = useState(DEFAULT_PREEMPTION_SEVERITY_THRESHOLD)
    const [selectedScenarioId, setSelectedScenarioId] = useState<string>(PREDEFINED_SCENARIOS[0].id)
    const [customScenario, setCustomScenario] = useState<TestScenario | null>(null)
    const [isEditingCustom, setIsEditingCustom] = useState(false)
    const [isSaving, setIsSaving] = useState(false)
    const [saveStatus, setSaveStatus] = useState<'idle' | 'success' | 'error'>('idle')

    const currentScenario = useMemo(() => {
        if (selectedScenarioId === 'custom' && customScenario) {
            return customScenario
        }
        return PREDEFINED_SCENARIOS.find(s => s.id === selectedScenarioId) ?? PREDEFINED_SCENARIOS[0]
    }, [selectedScenarioId, customScenario])

    // Score calculation logic (mirrors backend ScoringServiceImpl)
    const calculateScores = useCallback((scenario: TestScenario): ScoredResult[] => {
        return scenario.candidates.map(candidate => {
            // Travel score: w1 * travel_time_seconds
            const travelScore = weights.travelTime * candidate.travelTimeSeconds

            // Coverage score: penalty if base would go below reserve
            let coverageScore = 0
            if (candidate.otherUnitsAtBase < minReservePerBase) {
                const shortage = minReservePerBase - candidate.otherUnitsAtBase
                coverageScore = weights.coveragePenalty * shortage * 100
            }

            // Preemption score
            let preemptionScore = 0
            let isDisqualified = false

            if (candidate.isCurrentlyAssigned) {
                if (candidate.currentInterventionSeverity === undefined) {
                    isDisqualified = true
                    preemptionScore = Number.MAX_VALUE
                } else {
                    const severityDelta = scenario.targetSeverity - candidate.currentInterventionSeverity
                    if (severityDelta < preemptionThreshold) {
                        isDisqualified = true
                        preemptionScore = Number.MAX_VALUE
                    } else {
                        // Bonus/malus based on severity delta (targetSeverity - currentSeverity)
                        const preemptionBonus = weights.preemptionCost * severityDelta
                        preemptionScore = preemptionBonus + weights.reassignmentCost
                    }
                }
            }

            const totalScore = isDisqualified ? Number.MAX_VALUE : travelScore + coverageScore + preemptionScore

            return {
                id: candidate.id,
                callSign: candidate.callSign,
                travelScore,
                coverageScore,
                preemptionScore: isDisqualified ? 0 : preemptionScore,
                totalScore,
                isDisqualified,
            }
        }).sort((a, b) => a.totalScore - b.totalScore)
    }, [weights, minReservePerBase, preemptionThreshold])

    const scoredResults = useMemo(() => calculateScores(currentScenario), [calculateScores, currentScenario])

    const handleResetWeights = () => {
        setWeights(DEFAULT_WEIGHTS)
        setMinReservePerBase(DEFAULT_MIN_RESERVE_PER_BASE)
        setPreemptionThreshold(DEFAULT_PREEMPTION_SEVERITY_THRESHOLD)
    }

    const handleWeightChange = (key: keyof ScoringWeights, value: number) => {
        setWeights((prev: ScoringWeights) => ({ ...prev, [key]: value }))
        setSaveStatus('idle') // Reset save status when weights change
    }

    // Map frontend weight keys to backend config keys
    const weightKeyToConfigKey: Record<keyof ScoringWeights, string> = {
        travelTime: 'weight_travel_time',
        coveragePenalty: 'weight_coverage_penalty',
        preemptionCost: 'weight_preemption_delta',
        enRouteProgress: 'weight_en_route_progress',
        reassignmentCost: 'weight_reassignment_cost',
    }

    const handleSaveWeights = async () => {
        setIsSaving(true)
        setSaveStatus('idle')
        try {
            // Update each weight in the database
            for (const [frontendKey, backendKey] of Object.entries(weightKeyToConfigKey)) {
                const value = weights[frontendKey as keyof ScoringWeights]
                await fastPinPonService.updateDispatchConfig(backendKey, value)
            }
            // Also save thresholds
            await fastPinPonService.updateDispatchConfig('min_reserve_per_base', minReservePerBase)
            await fastPinPonService.updateDispatchConfig('preemption_severity_threshold', preemptionThreshold)

            setSaveStatus('success')
            setTimeout(() => setSaveStatus('idle'), 3000)
        } catch (error) {
            console.error('Failed to save weights:', error)
            setSaveStatus('error')
        } finally {
            setIsSaving(false)
        }
    }

    const handleCreateCustomScenario = () => {
        setCustomScenario({
            id: 'custom',
            name: 'Custom Scenario',
            description: 'User-defined test scenario',
            targetSeverity: 3,
            candidates: [
                { id: 'c1', callSign: 'Unit-01', travelTimeSeconds: 200, otherUnitsAtBase: 2, isAvailable: true, isCurrentlyAssigned: false },
            ],
        })
        setSelectedScenarioId('custom')
        setIsEditingCustom(true)
    }

    const handleAddCandidate = () => {
        if (!customScenario) return
        const newId = `c${customScenario.candidates.length + 1}`
        setCustomScenario({
            ...customScenario,
            candidates: [
                ...customScenario.candidates,
                { id: newId, callSign: `Unit-${customScenario.candidates.length + 1}`, travelTimeSeconds: 200, otherUnitsAtBase: 2, isAvailable: true, isCurrentlyAssigned: false },
            ],
        })
    }

    const handleRemoveCandidate = (candidateId: string) => {
        if (!customScenario || customScenario.candidates.length <= 1) return
        setCustomScenario({
            ...customScenario,
            candidates: customScenario.candidates.filter((c: CandidateInput) => c.id !== candidateId),
        })
    }

    const handleUpdateCandidate = (candidateId: string, updates: Partial<CandidateInput>) => {
        if (!customScenario) return
        setCustomScenario({
            ...customScenario,
            candidates: customScenario.candidates.map((c: CandidateInput) => c.id === candidateId ? { ...c, ...updates } : c),
        })
    }

    const formatScore = (score: number): string => {
        if (score === Number.MAX_VALUE) return '∞'
        return score.toFixed(1)
    }

    // Helper function for save button class
    const getSaveButtonClass = (): string => {
        if (saveStatus === 'success') return 'text-green-400'
        if (saveStatus === 'error') return 'text-red-400'
        return ''
    }

    // Helper function for save button content
    const renderSaveButtonContent = () => {
        if (isSaving) {
            return (
                <>
                    <svg className="w-4 h-4 mr-2 animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                    Saving...
                </>
            )
        }
        if (saveStatus === 'success') {
            return (
                <>
                    <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                    </svg>
                    Saved!
                </>
            )
        }
        if (saveStatus === 'error') {
            return (
                <>
                    <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                    Failed
                </>
            )
        }
        return (
            <>
                <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7H5a2 2 0 00-2 2v9a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-3m-1 4l-3 3m0 0l-3-3m3 3V4" />
                </svg>
                Save to Database
            </>
        )
    }

    return (
        <div className="flex flex-col gap-6 flex-1 px-6 py-6 bg-slate-950 text-slate-100 overflow-auto">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-2xl font-bold bg-gradient-to-r from-cyan-400 to-blue-500 bg-clip-text text-transparent">
                        Scoring Algorithm Configurator
                    </h1>
                    <p className="text-slate-400 text-sm mt-1">
                        Test and tune dispatch weights to optimize unit selection
                    </p>
                </div>
                <div className="flex gap-2">
                    <Button
                        variant="ghost"
                        onClick={handleSaveWeights}
                        disabled={isSaving}
                        className={getSaveButtonClass()}
                    >
                        {renderSaveButtonContent()}
                    </Button>
                    <Button variant="ghost" onClick={handleResetWeights}>
                        <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                        </svg>
                        Reset
                    </Button>
                </div>
            </div>

            {/* Formula Display */}
            <Card className="p-4">
                <h2 className="text-lg font-semibold text-cyan-400 mb-3">Scoring Formula</h2>
                <div className="font-mono text-xs bg-slate-800/50 p-4 rounded-xl border border-slate-700 leading-relaxed">
                    <div className="text-slate-300">
                        <span className="text-cyan-400 font-bold">Score</span> =
                        (<span className="text-amber-400">w₁</span> × Travel)
                        + (<span className="text-green-400">w₂</span> × Shortage × 100)
                        + <span className="text-slate-400">ComponentScore</span>
                    </div>
                    <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-4 pt-4 border-t border-slate-700/50">
                        <div className="space-y-1">
                            <div className="text-slate-500 uppercase tracking-wider text-[10px] font-bold">If Available:</div>
                            <div className="text-green-400">ComponentScore = <span className="font-bold">0</span></div>
                        </div>
                        <div className="space-y-1">
                            <div className="text-slate-500 uppercase tracking-wider text-[10px] font-bold">If Assigned:</div>
                            <div className="text-rose-400">ComponentScore = (<span className="font-bold">w₃</span> × SeverityΔ) + <span className="font-bold">w₄</span></div>
                        </div>
                    </div>
                    <div className="text-slate-500 text-[10px] mt-4 flex items-center gap-2">
                        <div className="w-1 h-1 rounded-full bg-slate-600"></div>
                        <span>Lower score = Better candidate for dispatch</span>
                    </div>
                </div>
            </Card>

            {/* Map and Results side by side */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Visual Scenario Map */}
                <Card className="p-4">
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="text-lg font-semibold text-cyan-400">Scenario Visualization</h2>
                        <span className="text-xs text-slate-500">
                            Incident severity: <span className="text-red-400 font-bold">{currentScenario.targetSeverity}</span>
                        </span>
                    </div>
                    <div className="relative bg-slate-800/50 rounded-xl border border-slate-700 h-[220px] overflow-hidden">
                        {/* Grid background */}
                        <div className="absolute inset-0" style={{
                            backgroundImage: 'radial-gradient(circle at 1px 1px, rgba(100,116,139,0.15) 1px, transparent 0)',
                            backgroundSize: '24px 24px'
                        }} />

                        {/* Incident marker at center */}
                        <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-20">
                            <div className="relative">
                                {/* Pulse animation */}
                                <div className="absolute inset-0 bg-red-500/30 rounded-full animate-ping" style={{ width: 40, height: 40, marginLeft: -20, marginTop: -20 }} />
                                {/* Main marker */}
                                <div className="relative flex items-center justify-center w-10 h-10 -translate-x-1/2 -translate-y-1/2 bg-gradient-to-br from-red-500 to-red-600 rounded-full shadow-lg shadow-red-500/50 border-2 border-red-300">
                                    <svg className="w-5 h-5 text-white" fill="currentColor" viewBox="0 0 20 20">
                                        <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                                    </svg>
                                </div>
                                <div className="absolute top-12 left-1/2 -translate-x-1/2 whitespace-nowrap text-xs font-bold text-red-400 bg-slate-900/90 px-2 py-0.5 rounded">
                                    Incident
                                </div>
                            </div>
                        </div>

                        {/* Unit markers positioned around incident based on travel time */}
                        {currentScenario.candidates.map((candidate: CandidateInput, index: number) => {
                            // Calculate position based on travel time (closer = nearer to center)
                            const maxTravelTime = Math.max(...currentScenario.candidates.map((c: CandidateInput) => c.travelTimeSeconds))
                            const minTravelTime = Math.min(...currentScenario.candidates.map((c: CandidateInput) => c.travelTimeSeconds))
                            const travelRange = maxTravelTime - minTravelTime || 1

                            // Normalize distance: 20-38% from center (keep within bounds)
                            const normalizedDistance = (candidate.travelTimeSeconds - minTravelTime) / travelRange
                            const distance = 20 + normalizedDistance * 18

                            // Spread units around the incident evenly at different angles
                            const totalUnits = currentScenario.candidates.length
                            // Use the actual number of units for angle calculation
                            const angleStep = (2 * Math.PI) / totalUnits
                            // Start from top-left to spread visually
                            const startAngle = -Math.PI * 0.75
                            const angle = startAngle + (index * angleStep)

                            const x = 50 + Math.cos(angle) * distance
                            const y = 50 + Math.sin(angle) * distance

                            const scoredResult = scoredResults.find((r: ScoredResult) => r.id === candidate.id)
                            const isSelected = scoredResult && !scoredResult.isDisqualified && scoredResults.indexOf(scoredResult) === 0
                            const isDisqualified = scoredResult?.isDisqualified

                            // Determine unit color based on status
                            const getUnitColor = (): string => {
                                if (isDisqualified) return '#475569' // slate-600
                                if (isSelected) return '#22d3ee' // cyan-400
                                if (candidate.isCurrentlyAssigned) return '#eab308' // amber-500 (under_way)
                                return '#22c55e' // green-500 (available)
                            }

                            // Determine connection line background color
                            const getLineBackground = (): string => {
                                if (isDisqualified) return '#475569'
                                if (isSelected) return '#22d3ee'
                                return '#64748b'
                            }

                            // Extract unit type from callSign (e.g., "VSAV-01" -> "VSAV")
                            const unitType = candidate.callSign.split('-')[0].toUpperCase()

                            // Generate SVG for unit type
                            const getUnitSvg = (type: string, color: string): string => {
                                if (type === 'VSAV') {
                                    return `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg" style="width:100%;height:100%;filter:drop-shadow(0 2px 4px rgba(0,0,0,0.5))">
                                        <circle cx="50" cy="50" r="45" fill="${color}" stroke="white" stroke-width="8"/>
                                        <line x1="50" y1="25" x2="50" y2="75" stroke="white" stroke-width="8" stroke-linecap="round"/>
                                        <line x1="25" y1="50" x2="75" y2="50" stroke="white" stroke-width="8" stroke-linecap="round"/>
                                    </svg>`
                                }
                                if (type === 'FPT') {
                                    return `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg" style="width:100%;height:100%;filter:drop-shadow(0 2px 4px rgba(0,0,0,0.5))">
                                        <circle cx="50" cy="50" r="45" fill="${color}" stroke="white" stroke-width="8"/>
                                        <path d="M 50 80 Q 20 60 50 20 Q 80 60 50 80 Z" fill="none" stroke="white" stroke-width="6" stroke-linejoin="round" stroke-linecap="round"/>
                                        <path d="M 50 80 Q 35 65 50 45" fill="none" stroke="white" stroke-width="6" stroke-linecap="round"/>
                                    </svg>`
                                }
                                return `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg" style="width:100%;height:100%;filter:drop-shadow(0 2px 4px rgba(0,0,0,0.5))">
                                    <circle cx="50" cy="50" r="45" fill="${color}" stroke="white" stroke-width="8"/>
                                    <circle cx="50" cy="50" r="15" fill="white"/>
                                </svg>`
                            }

                            return (
                                <div
                                    key={candidate.id}
                                    className="absolute z-10 transform -translate-x-1/2 -translate-y-1/2 transition-all duration-300"
                                    style={{ left: `${x}%`, top: `${y}%` }}
                                >
                                    {/* Connection line to incident - using single line element */}
                                    <div
                                        className="absolute pointer-events-none"
                                        style={{
                                            position: 'absolute',
                                            left: '50%',
                                            top: '50%',
                                            width: `${distance * 1.5}%`,
                                            height: '2px',
                                            background: getLineBackground(),
                                            opacity: isDisqualified ? 0.3 : 0.4,
                                            transformOrigin: '0 50%',
                                            transform: `rotate(${Math.atan2(50 - y, 50 - x) * 180 / Math.PI}deg)`,
                                            borderStyle: isDisqualified ? 'dashed' : 'solid',
                                        }}
                                    />

                                    {/* Unit marker - using actual map icons */}
                                    <div
                                        className={`relative transition-all ${isSelected ? 'scale-125' : ''}`}
                                        style={{ width: 36, height: 36 }}
                                        dangerouslySetInnerHTML={{
                                            __html: getUnitSvg(unitType, getUnitColor())
                                        }}
                                    />
                                    {isSelected && (
                                        <div className="absolute -top-1 -right-1 w-5 h-5 bg-cyan-400 rounded-full flex items-center justify-center text-[10px] font-bold text-slate-900 border-2 border-white shadow">
                                            1
                                        </div>
                                    )}

                                    {/* Unit info */}
                                    <div className={`absolute top-10 left-1/2 -translate-x-1/2 whitespace-nowrap text-center ${isDisqualified ? 'opacity-50' : ''}`}>
                                        <div className={`text-xs font-bold ${isSelected ? 'text-cyan-400' : 'text-slate-300'}`}>
                                            {candidate.callSign}
                                        </div>
                                        <div className="text-[10px] text-slate-500">
                                            {Math.floor(candidate.travelTimeSeconds / 60)}m {candidate.travelTimeSeconds % 60}s
                                        </div>
                                        {candidate.isCurrentlyAssigned && (
                                            <div className="text-[10px] text-amber-400">
                                                Sev {candidate.currentInterventionSeverity}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            )
                        })}

                        {/* Legend */}
                        <div className="absolute bottom-2 right-2 bg-slate-900/90 rounded-lg px-3 py-2 text-[10px] space-y-1.5">
                            <div className="flex items-center gap-2">
                                <div className="w-4 h-4 rounded-full bg-green-500 border-2 border-white shadow" />
                                <span className="text-slate-400">Available</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-4 h-4 rounded-full bg-amber-500 border-2 border-white shadow" />
                                <span className="text-slate-400">On assignment</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-4 h-4 rounded-full bg-cyan-500 border-2 border-white shadow" />
                                <span className="text-slate-400">Selected</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-4 h-4 rounded-full bg-slate-600 border-2 border-white shadow opacity-50" />
                                <span className="text-slate-400">Disqualified</span>
                            </div>
                        </div>
                    </div>
                </Card>

                {/* Scoring Results - side by side with map */}
                <Card className="p-4">
                    <h2 className="text-lg font-semibold text-cyan-400 mb-4">Scoring Results</h2>
                    <p className="text-xs text-slate-500 mb-3">
                        Candidates ranked by total score (lower = better)
                    </p>
                    <div className="overflow-x-auto max-h-[160px]">
                        <table className="w-full text-sm">
                            <thead className="text-xs uppercase text-slate-400 border-b border-slate-700 sticky top-0 bg-slate-900">
                                <tr>
                                    <th className="px-2 py-2 text-left">Rank</th>
                                    <th className="px-2 py-2 text-left">Unit</th>
                                    <th className="px-2 py-2 text-right">Score</th>
                                </tr>
                            </thead>
                            <tbody>
                                {scoredResults.map((result: ScoredResult, index: number) => {
                                    // Determine row class based on status
                                    const getRowClass = (): string => {
                                        if (result.isDisqualified) return 'text-slate-500 line-through'
                                        if (index === 0) return 'bg-cyan-500/10 text-cyan-400'
                                        return ''
                                    }
                                    return (
                                        <tr
                                            key={result.id}
                                            className={`border-b border-slate-800 ${getRowClass()}`}
                                        >
                                            <td className="px-2 py-2 text-left">
                                                {result.isDisqualified ? '—' : `#${index + 1}`}
                                            </td>
                                            <td className="px-2 py-2 text-left font-medium">
                                                {result.callSign}
                                            </td>
                                            <td className="px-2 py-2 text-right font-mono">
                                                {formatScore(result.totalScore)}
                                            </td>
                                        </tr>
                                    )
                                })}
                            </tbody>
                        </table>
                    </div>
                </Card>
            </div>

            {/* Weights and Scenarios Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Weights Configuration */}
                <Card className="p-4">
                    <h2 className="text-lg font-semibold text-cyan-400 mb-4">Weight Configuration</h2>

                    <div className="space-y-5">
                        {/* Travel Time Weight */}
                        <div className="space-y-2">
                            <div className="flex justify-between items-center">
                                <label htmlFor="travel-time-weight" className="text-sm font-medium text-amber-400">
                                    w₁ - Travel Time Weight
                                </label>
                                <span className="text-sm font-mono bg-slate-800 px-2 py-0.5 rounded text-amber-400">
                                    {weights.travelTime.toFixed(2)}
                                </span>
                            </div>
                            <input
                                id="travel-time-weight"
                                type="range"
                                min="0"
                                max="5"
                                step="0.1"
                                value={weights.travelTime}
                                onChange={(e) => handleWeightChange('travelTime', Number.parseFloat(e.target.value))}
                                className="w-full h-2 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-amber-500"
                            />
                            <p className="text-xs text-slate-500">Multiplied by travel time in seconds</p>
                        </div>

                        {/* Coverage Penalty Weight */}
                        <div className="space-y-2">
                            <div className="flex justify-between items-center">
                                <label htmlFor="coverage-penalty-weight" className="text-sm font-medium text-green-400">
                                    w₂ - Coverage Penalty Weight
                                </label>
                                <span className="text-sm font-mono bg-slate-800 px-2 py-0.5 rounded text-green-400">
                                    {weights.coveragePenalty.toFixed(2)}
                                </span>
                            </div>
                            <input
                                id="coverage-penalty-weight"
                                type="range"
                                min="0"
                                max="2"
                                step="0.1"
                                value={weights.coveragePenalty}
                                onChange={(e) => handleWeightChange('coveragePenalty', Number.parseFloat(e.target.value))}
                                className="w-full h-2 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-green-500"
                            />
                            <p className="text-xs text-slate-500">Applied when depleting base below reserve (×100 per unit shortage)</p>
                        </div>

                        {/* Preemption Delta Weight */}
                        <div className="space-y-2">
                            <div className="flex justify-between items-center">
                                <label htmlFor="preemption-delta-weight" className="text-sm font-medium text-rose-400">
                                    w₃ - Preemption Delta Weight
                                </label>
                                <span className="text-sm font-mono bg-slate-800 px-2 py-0.5 rounded text-rose-400">
                                    {weights.preemptionCost.toFixed(1)}
                                </span>
                            </div>
                            <input
                                id="preemption-delta-weight"
                                type="range"
                                min="-100"
                                max="100"
                                step="5"
                                value={weights.preemptionCost}
                                onChange={(e) => handleWeightChange('preemptionCost', Number.parseFloat(e.target.value))}
                                className="w-full h-2 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-rose-500"
                            />
                            <p className="text-xs text-slate-500">Multiplied by severity delta (target - current)</p>
                        </div>

                        {/* Reassignment Cost */}
                        <div className="space-y-2">
                            <div className="flex justify-between items-center">
                                <label htmlFor="reassignment-cost-weight" className="text-sm font-medium text-orange-400">
                                    w₄ - Reassignment Base Cost
                                </label>
                                <span className="text-sm font-mono bg-slate-800 px-2 py-0.5 rounded text-orange-400">
                                    {weights.reassignmentCost.toFixed(1)}
                                </span>
                            </div>
                            <input
                                id="reassignment-cost-weight"
                                type="range"
                                min="0"
                                max="200"
                                step="5"
                                value={weights.reassignmentCost}
                                onChange={(e) => handleWeightChange('reassignmentCost', Number.parseFloat(e.target.value))}
                                className="w-full h-2 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-orange-500"
                            />
                            <p className="text-xs text-slate-500">Fixed penalty for reassigning an already-assigned unit</p>
                        </div>

                        {/* Separator */}
                        <hr className="border-slate-700" />

                        {/* Thresholds */}
                        <div className="space-y-2">
                            <div className="flex justify-between items-center">
                                <label htmlFor="min-reserve-per-base" className="text-sm font-medium text-slate-300">
                                    Min Reserve Per Base
                                </label>
                                <input
                                    id="min-reserve-per-base"
                                    type="number"
                                    min="0"
                                    max="5"
                                    value={minReservePerBase}
                                    onChange={(e) => setMinReservePerBase(Number.parseInt(e.target.value) || 0)}
                                    className="w-16 px-2 py-1 text-sm bg-slate-800 border border-slate-700 rounded text-slate-200 text-center"
                                />
                            </div>
                            <p className="text-xs text-slate-500">Minimum units to keep at each base</p>
                        </div>

                        <div className="space-y-2">
                            <div className="flex justify-between items-center">
                                <label htmlFor="preemption-severity-threshold" className="text-sm font-medium text-slate-300">
                                    Preemption Severity Threshold
                                </label>
                                <input
                                    id="preemption-severity-threshold"
                                    type="number"
                                    min="0"
                                    max="5"
                                    value={preemptionThreshold}
                                    onChange={(e) => setPreemptionThreshold(Number.parseInt(e.target.value) || 0)}
                                    className="w-16 px-2 py-1 text-sm bg-slate-800 border border-slate-700 rounded text-slate-200 text-center"
                                />
                            </div>
                            <p className="text-xs text-slate-500">Min severity delta required to preempt</p>
                        </div>
                    </div>
                </Card>

                {/* Scenario Selection & Testing */}
                <Card className="p-4">
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="text-lg font-semibold text-cyan-400">Test Scenarios</h2>
                        <Button variant="ghost" onClick={handleCreateCustomScenario} className="text-xs">
                            + Custom
                        </Button>
                    </div>

                    <div className="space-y-4">
                        {/* Scenario Selector */}
                        <div className="flex gap-2 flex-wrap">
                            {PREDEFINED_SCENARIOS.map(scenario => (
                                <button
                                    key={scenario.id}
                                    onClick={() => { setSelectedScenarioId(scenario.id); setIsEditingCustom(false) }}
                                    className={`px-3 py-1.5 text-sm rounded-lg transition-all ${selectedScenarioId === scenario.id
                                        ? 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/50'
                                        : 'bg-slate-800 text-slate-400 border border-slate-700 hover:border-slate-600'
                                        }`}
                                >
                                    {scenario.name}
                                </button>
                            ))}
                            {customScenario && (
                                <button
                                    onClick={() => { setSelectedScenarioId('custom'); setIsEditingCustom(true) }}
                                    className={`px-3 py-1.5 text-sm rounded-lg transition-all ${selectedScenarioId === 'custom'
                                        ? 'bg-purple-500/20 text-purple-400 border border-purple-500/50'
                                        : 'bg-slate-800 text-slate-400 border border-slate-700 hover:border-slate-600'
                                        }`}
                                >
                                    Custom
                                </button>
                            )}
                        </div>

                        {/* Scenario Description */}
                        <div className="bg-slate-800/50 p-3 rounded-xl border border-slate-700">
                            <div className="flex items-center justify-between">
                                <h3 className="font-medium text-white">{currentScenario.name}</h3>
                                <span className="text-xs bg-red-500/20 text-red-400 px-2 py-0.5 rounded-full">
                                    Severity: {currentScenario.targetSeverity}
                                </span>
                            </div>
                            <p className="text-sm text-slate-400 mt-1">{currentScenario.description}</p>
                        </div>

                        {/* Custom Scenario Editor */}
                        {isEditingCustom && customScenario && (
                            <div className="bg-slate-800/30 p-3 rounded-xl border border-slate-700 space-y-3">
                                <div className="flex items-center gap-4">
                                    <label htmlFor="custom-target-severity" className="text-xs text-slate-400">Target Severity:</label>
                                    <input
                                        id="custom-target-severity"
                                        type="number"
                                        min="1"
                                        max="5"
                                        value={customScenario.targetSeverity}
                                        onChange={(e) => setCustomScenario({ ...customScenario, targetSeverity: Number.parseInt(e.target.value) || 1 })}
                                        className="w-16 px-2 py-1 text-sm bg-slate-800 border border-slate-700 rounded text-slate-200 text-center"
                                    />
                                </div>

                                {customScenario.candidates.map((candidate) => (
                                    <div key={candidate.id} className="flex items-center gap-2 text-xs">
                                        <input
                                            type="text"
                                            value={candidate.callSign}
                                            onChange={(e) => handleUpdateCandidate(candidate.id, { callSign: e.target.value })}
                                            className="w-20 px-2 py-1 bg-slate-800 border border-slate-700 rounded text-slate-200"
                                            placeholder="Name"
                                        />
                                        <div className="flex items-center gap-1">
                                            <span className="text-slate-500">T:</span>
                                            <input
                                                type="number"
                                                value={candidate.travelTimeSeconds}
                                                onChange={(e) => handleUpdateCandidate(candidate.id, { travelTimeSeconds: Number.parseInt(e.target.value) || 0 })}
                                                className="w-16 px-1 py-1 bg-slate-800 border border-slate-700 rounded text-slate-200 text-center"
                                            />
                                            <span className="text-slate-500">s</span>
                                        </div>
                                        <div className="flex items-center gap-1">
                                            <span className="text-slate-500">Base:</span>
                                            <input
                                                type="number"
                                                value={candidate.otherUnitsAtBase}
                                                onChange={(e) => handleUpdateCandidate(candidate.id, { otherUnitsAtBase: Number.parseInt(e.target.value) || 0 })}
                                                className="w-12 px-1 py-1 bg-slate-800 border border-slate-700 rounded text-slate-200 text-center"
                                            />
                                        </div>
                                        <label className="flex items-center gap-1 text-slate-400">
                                            <input
                                                type="checkbox"
                                                checked={candidate.isAvailable}
                                                onChange={(e) => handleUpdateCandidate(candidate.id, { isAvailable: e.target.checked })}
                                                className="rounded border-slate-600"
                                            />{' '}
                                            Avail
                                        </label>
                                        <label className="flex items-center gap-1 text-slate-400">
                                            <input
                                                type="checkbox"
                                                checked={candidate.isCurrentlyAssigned}
                                                onChange={(e) => handleUpdateCandidate(candidate.id, { isCurrentlyAssigned: e.target.checked, currentInterventionSeverity: e.target.checked ? 2 : undefined })}
                                                className="rounded border-slate-600"
                                            />{' '}
                                            Assigned
                                        </label>
                                        {candidate.isCurrentlyAssigned && (
                                            <div className="flex items-center gap-1">
                                                <span className="text-slate-500">Sev:</span>
                                                <input
                                                    type="number"
                                                    min="1"
                                                    max="5"
                                                    value={candidate.currentInterventionSeverity ?? 2}
                                                    onChange={(e) => handleUpdateCandidate(candidate.id, { currentInterventionSeverity: Number.parseInt(e.target.value) || 2 })}
                                                    className="w-10 px-1 py-1 bg-slate-800 border border-slate-700 rounded text-slate-200 text-center"
                                                />
                                            </div>
                                        )}
                                        <button
                                            onClick={() => handleRemoveCandidate(candidate.id)}
                                            disabled={customScenario.candidates.length <= 1}
                                            className="p-1 text-red-400/70 hover:text-red-400 disabled:opacity-30"
                                        >
                                            ✕
                                        </button>
                                    </div>
                                ))}

                                <button
                                    onClick={handleAddCandidate}
                                    className="text-xs text-cyan-400 hover:text-cyan-300"
                                >
                                    + Add Candidate
                                </button>
                            </div>
                        )}

                        {/* Candidate Input Table */}
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead className="text-xs uppercase text-slate-400 border-b border-slate-700">
                                    <tr>
                                        <th className="px-2 py-2 text-left">Unit</th>
                                        <th className="px-2 py-2 text-right">Travel (min)</th>
                                        <th className="px-2 py-2 text-right">Units @ Base</th>
                                        <th className="px-2 py-2 text-center">Available</th>
                                        <th className="px-2 py-2 text-center">Assigned</th>
                                        <th className="px-2 py-2 text-center">Current Sev</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-800">
                                    {currentScenario.candidates.map(c => (
                                        <tr key={c.id} className="text-slate-300">
                                            <td className="px-2 py-2 font-medium text-white">{c.callSign}</td>
                                            <td className="px-2 py-2 text-right font-mono">{(c.travelTimeSeconds / 60).toFixed(1)}</td>
                                            <td className="px-2 py-2 text-right font-mono">{c.otherUnitsAtBase}</td>
                                            <td className="px-2 py-2 text-center">
                                                {c.isAvailable ? (
                                                    <span className="text-green-400">✓</span>
                                                ) : (
                                                    <span className="text-slate-500">—</span>
                                                )}
                                            </td>
                                            <td className="px-2 py-2 text-center">
                                                {c.isCurrentlyAssigned ? (
                                                    <span className="text-amber-400">✓</span>
                                                ) : (
                                                    <span className="text-slate-500">—</span>
                                                )}
                                            </td>
                                            <td className="px-2 py-2 text-center font-mono">
                                                {c.currentInterventionSeverity ?? '—'}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </Card>
            </div>
        </div>
    )
}
