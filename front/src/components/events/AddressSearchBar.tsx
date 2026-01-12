import type { ChangeEvent, JSX, KeyboardEvent } from 'react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { addressSearchService, type AddressSuggestion } from '../../services/AddressSearchService'

type AddressSearchBarProps = {
    /** Callback when an address is selected */
    readonly onSelect: (address: AddressSuggestion) => void
    /** Callback when user starts typing (to clear previous coordinates) */
    readonly onInputChange?: () => void
    /** Placeholder text */
    readonly placeholder?: string
    /** Additional CSS classes */
    readonly className?: string
}

const DEBOUNCE_DELAY = 300

export function AddressSearchBar({
    onSelect,
    onInputChange,
    placeholder = 'Rechercher une adresse…',
    className = '',
}: AddressSearchBarProps): JSX.Element {
    const [query, setQuery] = useState('')
    const [suggestions, setSuggestions] = useState<AddressSuggestion[]>([])
    const [isLoading, setIsLoading] = useState(false)
    const [isOpen, setIsOpen] = useState(false)
    const [highlightedIndex, setHighlightedIndex] = useState(-1)
    const [error, setError] = useState<string | null>(null)

    const containerRef = useRef<HTMLDivElement>(null)
    const inputRef = useRef<HTMLInputElement>(null)
    const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

    // Debounced search
    const performSearch = useCallback(async (searchQuery: string) => {
        if (searchQuery.trim().length < 3) {
            setSuggestions([])
            setIsOpen(false)
            return
        }

        setIsLoading(true)
        setError(null)

        try {
            const results = await addressSearchService.searchAddress(searchQuery)
            setSuggestions(results)
            setIsOpen(results.length > 0)
            setHighlightedIndex(-1)
        } catch (err: unknown) {
            const message = err instanceof Error ? err.message : 'Erreur lors de la recherche'
            setError(message)
            setSuggestions([])
        } finally {
            setIsLoading(false)
        }
    }, [])

    // Handle input change with debounce
    const handleInputChange = useCallback(
        (e: ChangeEvent<HTMLInputElement>) => {
            const newQuery = e.target.value
            setQuery(newQuery)

            // Clear previous coordinates when user starts typing
            onInputChange?.()

            // Clear previous timer
            if (debounceTimerRef.current) {
                clearTimeout(debounceTimerRef.current)
            }

            // Set new debounced search
            debounceTimerRef.current = setTimeout(() => {
                performSearch(newQuery).catch(() => { /* error handled in performSearch */ })
            }, DEBOUNCE_DELAY)
        },
        [performSearch, onInputChange],
    )

    // Handle suggestion selection
    const handleSelect = useCallback(
        (suggestion: AddressSuggestion) => {
            setQuery(suggestion.label)
            setSuggestions([])
            setIsOpen(false)
            onSelect(suggestion)
        },
        [onSelect],
    )

    // Handle keyboard navigation
    const handleKeyDown = useCallback(
        (e: KeyboardEvent<HTMLInputElement>) => {
            if (!isOpen || suggestions.length === 0) return

            switch (e.key) {
                case 'ArrowDown':
                    e.preventDefault()
                    setHighlightedIndex((prev) => Math.min(prev + 1, suggestions.length - 1))
                    break
                case 'ArrowUp':
                    e.preventDefault()
                    setHighlightedIndex((prev) => Math.max(prev - 1, 0))
                    break
                case 'Enter':
                    e.preventDefault()
                    if (highlightedIndex >= 0 && highlightedIndex < suggestions.length) {
                        handleSelect(suggestions[highlightedIndex])
                    }
                    break
                case 'Escape':
                    setIsOpen(false)
                    setHighlightedIndex(-1)
                    break
            }
        },
        [isOpen, suggestions, highlightedIndex, handleSelect],
    )

    // Close dropdown when clicking outside
    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
                setIsOpen(false)
            }
        }

        document.addEventListener('mousedown', handleClickOutside)
        return () => document.removeEventListener('mousedown', handleClickOutside)
    }, [])

    // Cleanup debounce timer on unmount
    useEffect(() => {
        return () => {
            if (debounceTimerRef.current) {
                clearTimeout(debounceTimerRef.current)
            }
            addressSearchService.cancel()
        }
    }, [])

    return (
        <div ref={containerRef} className={`relative ${className}`}>
            <div className="relative">
                <input
                    ref={inputRef}
                    id="address-search"
                    type="text"
                    role="combobox"
                    value={query}
                    onChange={handleInputChange}
                    onKeyDown={handleKeyDown}
                    onFocus={() => suggestions.length > 0 && setIsOpen(true)}
                    placeholder={placeholder}
                    className="bg-slate-900/60 pr-10 pl-3 py-2 border border-blue-500/20 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500/40 w-full text-white text-sm"
                    aria-label="Rechercher une adresse"
                    aria-expanded={isOpen}
                    aria-haspopup="listbox"
                    aria-controls="address-suggestions-list"
                    aria-autocomplete="list"
                    autoComplete="off"
                />
                {/* Search icon or loading spinner */}
                <div className="right-3 absolute inset-y-0 flex items-center pointer-events-none">
                    {isLoading ? (
                        <svg
                            className="w-4 h-4 text-slate-400 animate-spin"
                            xmlns="http://www.w3.org/2000/svg"
                            fill="none"
                            viewBox="0 0 24 24"
                        >
                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                            <path
                                className="opacity-75"
                                fill="currentColor"
                                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                            />
                        </svg>
                    ) : (
                        <svg
                            className="w-4 h-4 text-slate-400"
                            xmlns="http://www.w3.org/2000/svg"
                            fill="none"
                            viewBox="0 0 24 24"
                            stroke="currentColor"
                            strokeWidth="2"
                        >
                            <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                            />
                        </svg>
                    )}
                </div>
            </div>

            {/* Error message */}
            {error && <p className="mt-1 text-rose-400 text-xs">{error}</p>}

            {/* Suggestions dropdown */}
            {isOpen && suggestions.length > 0 && (
                <ul
                    id="address-suggestions-list"
                    className="z-50 absolute bg-slate-800/95 backdrop-blur-sm shadow-lg mt-1 border border-blue-500/20 rounded-xl w-full max-h-60 overflow-auto"
                    role="listbox"
                >
                    {suggestions.map((suggestion, index) => (
                        <li
                            key={`${suggestion.latitude}-${suggestion.longitude}`}
                            role="option"
                            aria-selected={highlightedIndex === index}
                            tabIndex={-1}
                            className={`px-3 py-2 cursor-pointer text-sm transition-colors ${highlightedIndex === index
                                ? 'bg-sky-500/20 text-white'
                                : 'text-slate-300 hover:bg-slate-700/50'
                                }`}
                            onClick={() => handleSelect(suggestion)}
                            onKeyDown={(e) => e.key === 'Enter' && handleSelect(suggestion)}
                            onMouseEnter={() => setHighlightedIndex(index)}
                        >
                            <div className="font-medium">{suggestion.label}</div>
                            {suggestion.postcode && (
                                <div className="text-slate-400 text-xs">
                                    {suggestion.postcode} {suggestion.city}
                                </div>
                            )}
                        </li>
                    ))}
                </ul>
            )}

            {/* No results message */}
            {isOpen && query.length >= 3 && suggestions.length === 0 && !isLoading && !error && (
                <div className="z-50 absolute bg-slate-800/95 backdrop-blur-sm shadow-lg mt-1 px-3 py-2 border border-blue-500/20 rounded-xl w-full text-slate-400 text-sm">
                    Aucun résultat trouvé
                </div>
            )}
        </div>
    )
}
