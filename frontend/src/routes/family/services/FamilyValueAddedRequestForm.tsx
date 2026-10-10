import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ApiError } from '../../../shared/api/client'
import {
  createFamilyValueAddedServiceRequest,
  fetchFamilyValueAddedServices,
} from '../../../features/value-added-services/api'
import type { ValueAddedServiceRequest } from '../../../features/value-added-services/types'
import {
  BOOKING_TIME_CODES, MIN_NOTICE_HOURS, STEP_MINUTES, bookingHint, bookingTimeError, bookingWindow,
} from '../../../features/value-added-services/bookingTime'
import { problemCode, serverFieldErrors } from '../../../shared/validation/serverErrors'
import styles from './FamilyValueAddedServices.module.css'

/**
 * A family member books an extra service for the elder they follow. Booking it is their
 * approval, so it goes straight to the care team; a family member who may only read the
 * elder's care is told so by the server.
 */
export function FamilyValueAddedRequestForm({
  elderId,
  onCreated,
}: {
  elderId: number
  onCreated: (request: ValueAddedServiceRequest) => void
}) {
  const catalogue = useQuery({ queryKey: ['family-value-added-services'], queryFn: fetchFamilyValueAddedServices })
  const [serviceId, setServiceId] = useState<number | null>(null)
  const [schedule, setSchedule] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<{ schedule?: string; note?: string }>({})
  const id = useId()
  const [sent, setSent] = useState<string | null>(null)

  const services = catalogue.data ?? []
  const chosen = services.find((service) => service.id === serviceId) ?? services[0] ?? null

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!chosen) {
      setError('Choose a service.')
      return
    }
    const found = {
      schedule: bookingTimeError(schedule, chosen.durationMinutes),
      note: [...note.trim()].length > 1000 ? 'Use 1000 characters or fewer.' : undefined,
    }
    setFieldErrors(found)
    if (found.schedule || found.note) return
    setBusy(true)
    setError(null)
    setSent(null)
    try {
      const created = await createFamilyValueAddedServiceRequest({
        elderId,
        valueAddedServiceId: chosen.id,
        requestedSchedule: schedule,
        specialInstructions: note.trim() || null,
      })
      onCreated(created)
      setSchedule('')
      setNote('')
      setSent(`${chosen.name} is booked. The care team has been told.`)
    } catch (cause) {
      const fields = serverFieldErrors(cause, { requestedSchedule: 'schedule', specialInstructions: 'note' })
      if (BOOKING_TIME_CODES.has(problemCode(cause) ?? '') && cause instanceof ApiError) setFieldErrors({ schedule: cause.message })
      else if (fields.schedule || fields.note) setFieldErrors(fields)
      else setError(cause instanceof ApiError ? cause.message : 'Unable to book the service.')
    } finally {
      setBusy(false)
    }
  }

  if (catalogue.isError) return <section className={styles.card}><p className={styles.muted}>Unable to load extra services.</p></section>
  if (catalogue.isPending) return <section className={styles.card}><p className={styles.muted}>Loading extra services...</p></section>
  if (services.length === 0) return null

  return <section className={styles.card} aria-labelledby="family-request-title">
    <h2 id="family-request-title">Book a service</h2>
    <p className={styles.muted}>Booking it is your approval: it goes straight to the care team. Ask at least {MIN_NOTICE_HOURS} hours ahead.</p>
    {error && <div className={styles.error} role="alert">{error}</div>}
    {sent && <p role="status">{sent}</p>}
    <form className={styles.form} noValidate onSubmit={(event) => void submit(event)}>
      <label>
        <span>Service</span>
        <select value={chosen?.id ?? ''} disabled={busy} onChange={(event) => setServiceId(Number(event.target.value))}>
          {services.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}
        </select>
      </label>
      {chosen?.description && <p className={styles.muted}>{chosen.description} · about {chosen.durationMinutes / 60} h</p>}
      <div className={styles.formField}>
        <label htmlFor={`${id}-schedule`}>Date and time</label>
        <input id={`${id}-schedule`} type="datetime-local" {...bookingWindow()} step={STEP_MINUTES * 60} value={schedule}
          disabled={busy} aria-invalid={!!fieldErrors.schedule}
          aria-describedby={[fieldErrors.schedule && `${id}-schedule-error`, chosen && `${id}-schedule-hint`].filter(Boolean).join(' ') || undefined}
          onChange={(event) => { setSchedule(event.target.value); setFieldErrors((current) => ({ ...current, schedule: undefined })) }} />
        {fieldErrors.schedule && <span id={`${id}-schedule-error`} className={styles.fieldError}>{fieldErrors.schedule}</span>}
        {chosen && <span id={`${id}-schedule-hint`} className={styles.fieldHint}>{bookingHint(chosen.durationMinutes)}</span>}
      </div>
      <div className={styles.formField}>
        <label htmlFor={`${id}-note`}>Instructions for the caregiver (optional)</label>
        <textarea id={`${id}-note`} value={note} maxLength={1000} disabled={busy} aria-invalid={!!fieldErrors.note}
          aria-describedby={fieldErrors.note ? `${id}-note-error` : undefined}
          onChange={(event) => { setNote(event.target.value); setFieldErrors((current) => ({ ...current, note: undefined })) }} />
        {fieldErrors.note && <span id={`${id}-note-error`} className={styles.fieldError}>{fieldErrors.note}</span>}
      </div>
      <div className={styles.actions}>
        <button type="submit" disabled={busy}>{busy ? 'Booking...' : 'Book service'}</button>
      </div>
    </form>
  </section>
}
