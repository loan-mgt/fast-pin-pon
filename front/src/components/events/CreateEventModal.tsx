import type { ChangeEvent, FormEvent, JSX } from 'react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import type { CreateEventRequest, EventType } from '../../types/eventTypes'
import { Button } from '../ui/button'
import { Card } from '../ui/card'
import { AddressSearchBar } from './AddressSearchBar'
import type { AddressSuggestion } from '../../services/AddressSearchService'
import { useAuth } from '../../auth/AuthProvider'

/**
 * Map bounds matching the MapContainer maxBounds configuration.
 * Incidents can only be created within these bounds.
 */
const MAP_BOUNDS = {
  southwest: { lng: 4.290161, lat: 45.375302 },
  northeast: { lng: 5.386047, lat: 46.0999 },
}

function isWithinBounds(lng: number, lat: number): boolean {
  return (
    lng >= MAP_BOUNDS.southwest.lng &&
    lng <= MAP_BOUNDS.northeast.lng &&
    lat >= MAP_BOUNDS.southwest.lat &&
    lat <= MAP_BOUNDS.northeast.lat
  )
}

type CreateEventModalProps = {
  readonly isOpen: boolean
  readonly onClose: () => void
  readonly eventTypes: EventType[]
  readonly onSubmit: (payload: CreateEventRequest) => Promise<void> | void
  readonly initialLocation?: { latitude: number; longitude: number } | null
  /** When true, address search is required (navbar button mode). When false, it's optional (right-click mode) */
  readonly addressRequired?: boolean
}

export function CreateEventModal({
  isOpen,
  onClose,
  eventTypes,
  onSubmit,
  initialLocation,
  addressRequired = false,
}: Readonly<CreateEventModalProps>): JSX.Element | null {
  const { permissions } = useAuth()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [latitude, setLatitude] = useState('')
  const [longitude, setLongitude] = useState('')
  const [severity, setSeverity] = useState(3)
  const [eventTypeCode, setEventTypeCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [selectedAddress, setSelectedAddress] = useState<string>('')

  const canUseAddressSearch = permissions?.canUseAddressSearch ?? false

  const handleAddressSelect = useCallback((address: AddressSuggestion) => {
    setLatitude(address.latitude.toString())
    setLongitude(address.longitude.toString())
    setSelectedAddress(address.label)
  }, [])

  // Clear coordinates and address when user starts typing in search bar
  // Only in addressRequired mode (navbar button) - in right-click mode, typing doesn't clear coords
  const handleClearCoordinates = useCallback(() => {
    if (addressRequired) {
      setLatitude('')
      setLongitude('')
      setSelectedAddress('')
    }
  }, [addressRequired])

  const hasCoords = latitude.trim() !== '' && longitude.trim() !== ''

  const defaultEventType = useMemo(() => eventTypes.at(0)?.code ?? '', [eventTypes])

  useEffect(() => {
    if (!eventTypeCode && defaultEventType) {
      setEventTypeCode(defaultEventType)
    }
  }, [defaultEventType, eventTypeCode])

  useEffect(() => {
    if (!isOpen) {
      return
    }
    if (initialLocation) {
      setLatitude(initialLocation.latitude.toString())
      setLongitude(initialLocation.longitude.toString())
    }
  }, [initialLocation, isOpen])

  useEffect(() => {
    if (!isOpen) {
      setTitle('')
      setDescription('')
      setLatitude(initialLocation ? initialLocation.latitude.toString() : '')
      setLongitude(initialLocation ? initialLocation.longitude.toString() : '')
      setSeverity(3)
      setEventTypeCode(defaultEventType)
      setError(null)
      setIsSubmitting(false)
      setSelectedAddress('')
    }
  }, [isOpen, defaultEventType, initialLocation])

  const handleInputChange =
    (setter: (value: string) => void) => (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      setter(event.target.value)
    }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)

    if (!title.trim()) {
      setError('Title is required.')
      return
    }
    if (!hasCoords) {
      setError('Latitude and longitude are required.')
      return
    }

    // Validate coordinates are within allowed map bounds
    const lat = Number.parseFloat(latitude)
    const lng = Number.parseFloat(longitude)
    if (!isWithinBounds(lng, lat)) {
      setError('Les coordonnées doivent être dans la zone couverte (région Rhône/Lyon).')
      return
    }

    if (!eventTypeCode) {
      setError('Pick an event type.')
      return
    }

    setIsSubmitting(true)
    try {
      const payload: CreateEventRequest = {
        title: title.trim(),
        latitude: Number.parseFloat(latitude),
        longitude: Number.parseFloat(longitude),
        severity,
        event_type_code: eventTypeCode,
      }

      const trimmedDescription = description.trim()
      if (trimmedDescription) {
        payload.description = trimmedDescription
      }

      await onSubmit(payload)
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create event.')
    } finally {
      setIsSubmitting(false)
    }
  }

  if (!isOpen) {
    return null
  }

  return (
    <div className="z-20 fixed inset-0 flex justify-center items-center bg-slate-950/80 backdrop-blur-sm px-4">
      <Card className="space-y-4 border-blue-500/30 w-full max-w-lg">
        <div className="flex justify-between items-center gap-3">
          <div>
            <p className="text-cyan-300/70 text-xs uppercase tracking-[0.35em]">Create</p>
            <h2 className="font-semibold text-white text-xl">New incident</h2>
          </div>
          <Button variant="ghost" type="button" onClick={onClose}>
            Close
          </Button>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          {/* Address search bar - only shown in navbar button mode (addressRequired) */}
          {addressRequired && canUseAddressSearch && (
            <div className="space-y-2">
              <label className="text-slate-300 text-sm" htmlFor="address-search">
                Rechercher une adresse *
              </label>
              <AddressSearchBar
                onSelect={handleAddressSelect}
                onInputChange={handleClearCoordinates}
              />
              {selectedAddress && (
                <p className="text-emerald-400 text-xs">
                  📍 {selectedAddress}
                </p>
              )}
            </div>
          )}

          <div className="space-y-2">
            <label className="text-slate-300 text-sm" htmlFor="title">
              Title *
            </label>
            <input
              id="title"
              type="text"
              value={title}
              onChange={handleInputChange(setTitle)}
              className="bg-slate-900/60 px-3 py-2 border border-blue-500/20 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500/40 w-full text-white text-sm"
              required
            />
          </div>

          <div className="space-y-2">
            <label className="text-slate-300 text-sm" htmlFor="description">
              Description
            </label>
            <textarea
              id="description"
              value={description}
              onChange={handleInputChange(setDescription)}
              className="bg-slate-900/60 px-3 py-2 border border-blue-500/20 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500/40 w-full text-white text-sm"
              rows={3}
            />
          </div>

          <div className="gap-3 grid grid-cols-2">
            <div className="space-y-2">
              <label className="text-slate-300 text-sm" htmlFor="severity">
                Severity
              </label>
              <input
                id="severity"
                type="range"
                min={1}
                max={5}
                value={severity}
                onChange={(e) => setSeverity(Number.parseInt(e.target.value, 10))}
                className="w-full"
              />
              <p className="text-slate-400 text-xs">Current: {severity}</p>
            </div>

            <div className="space-y-2">
              <label className="text-slate-300 text-sm" htmlFor="eventType">
                Event type *
              </label>
              <select
                id="eventType"
                value={eventTypeCode}
                onChange={(e) => setEventTypeCode(e.target.value)}
                className="bg-slate-900/60 px-3 py-2 border border-blue-500/20 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500/40 w-full text-white text-sm"
                required
              >
                {eventTypes.map((type) => (
                  <option key={type.code} value={type.code}>
                    {type.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {error && <p className="text-rose-300 text-sm">{error}</p>}

          <div className="flex justify-end gap-3">
            <Button type="button" variant="ghost" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting || !hasCoords || !title.trim()}>
              {isSubmitting ? 'Creating…' : 'Create incident'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
