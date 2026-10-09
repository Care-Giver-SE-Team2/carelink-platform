import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ApiError } from '../../../shared/api/client'
import {
  createFamilyValueAddedServiceRequest,
  fetchFamilyValueAddedServices,
} from '../../../features/value-added-services/api'
import type { ValueAddedServiceRequest } from '../../../features/value-added-services/types'
import styles from './FamilyValueAddedServices.module.css'

/** The server asks for at least this much notice; the picker starts there. */
const MIN_NOTICE_HOURS = 2

function earliestRequestTime(now = new Date()): string {
  const earliest = new Date(now.getTime() + MIN_NOTICE_HOURS * 60 * 60 * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${earliest.getFullYear()}-${pad(earliest.getMonth() + 1)}-${pad(earliest.getDate())}T${pad(earliest.getHours())}:${pad(earliest.getMinutes())}`
}

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
  const [sent, setSent] = useState<string | null>(null)

  const services = catalogue.data ?? []
  const chosen = services.find((service) => service.id === serviceId) ?? services[0] ?? null

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!chosen || !schedule) {
      setError('Choose a service and a date and time.')
      return
    }
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
      setError(cause instanceof ApiError ? cause.message : 'Unable to book the service.')
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
    <form className={styles.form} onSubmit={(event) => void submit(event)}>
      <label>
        <span>Service</span>
        <select value={chosen?.id ?? ''} disabled={busy} onChange={(event) => setServiceId(Number(event.target.value))}>
          {services.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}
        </select>
      </label>
      {chosen?.description && <p className={styles.muted}>{chosen.description} · about {chosen.durationMinutes / 60} h</p>}
      <label>
        <span>Date and time</span>
        <input type="datetime-local" min={earliestRequestTime()} value={schedule} disabled={busy}
          onChange={(event) => setSchedule(event.target.value)} />
      </label>
      <label>
        <span>Instructions for the caregiver (optional)</span>
        <textarea value={note} maxLength={1000} disabled={busy} onChange={(event) => setNote(event.target.value)} />
      </label>
      <div className={styles.actions}>
        <button type="submit" disabled={busy}>{busy ? 'Booking...' : 'Book service'}</button>
      </div>
    </form>
  </section>
}
