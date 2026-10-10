import { useEffect, useId, useMemo, useState } from 'react'
import { ApiError } from '../../../shared/api/client'
import {
  createElderValueAddedServiceRequest,
  fetchElderValueAddedServiceRequests,
  fetchValueAddedServices,
  withdrawElderValueAddedServiceRequest,
} from '../../../features/value-added-services/api'
import type { ValueAddedService, ValueAddedServiceRequest } from '../../../features/value-added-services/types'
import {
  BOOKING_TIME_CODES, MIN_NOTICE_HOURS, STEP_MINUTES, bookingHint, bookingTimeError, bookingWindow,
} from '../../../features/value-added-services/bookingTime'
import { problemCode, serverFieldErrors } from '../../../shared/validation/serverErrors'
import { ElderShell } from '../components/ElderShell'
import {
  ActionStack,
  ChoiceButton,
  InfoCard,
  InfoNote,
  ScreenColumns,
  ScreenFooter,
  ScreenHeader,
  Slot,
  SpeakButton,
  StatusNote,
  WideButton,
} from '../components/ElderUi'
import { greeting } from '../lib/greeting'
import styles from '../Elder.module.css'

/** "About 1 hour", "About 1.5 hours". */
function durationText(minutes: number): string {
  const hours = minutes / 60
  return `About ${hours} hour${hours === 1 ? '' : 's'}`
}

/** A request the elder can still take back: not yet answered, or booked and not yet done. */
function withdrawable(request: ValueAddedServiceRequest): boolean {
  return request.status === 'PENDING_APPROVAL' || request.status === 'DISPATCHED'
}

export default function ValueAddedServices() {
  const [services, setServices] = useState<ValueAddedService[]>([])
  const [requests, setRequests] = useState<ValueAddedServiceRequest[]>([])
  const [serviceId, setServiceId] = useState<number | null>(null)
  const [schedule, setSchedule] = useState('')
  const [note, setNote] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<{ schedule?: string; note?: string }>({})
  const id = useId()
  const [confirmingId, setConfirmingId] = useState<number | null>(null)
  const [withdrawingId, setWithdrawingId] = useState<number | null>(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([fetchValueAddedServices(), fetchElderValueAddedServiceRequests()])
      .then(([catalogue, history]) => {
        if (cancelled) return
        setServices(catalogue)
        setRequests(history)
        setServiceId(catalogue[0]?.id ?? null)
      })
      .catch(() => !cancelled && setError('Unable to load extra services.'))
      .finally(() => !cancelled && setLoading(false))
    return () => { cancelled = true }
  }, [])

  const selected = useMemo(() => services.find((item) => item.id === serviceId) ?? null, [services, serviceId])

  async function submit() {
    if (serviceId === null || !selected) {
      setError('Choose a service.')
      return
    }
    const found = {
      schedule: bookingTimeError(schedule, selected.durationMinutes),
      note: [...note.trim()].length > 1000 ? 'Please keep this under 1000 characters.' : undefined,
    }
    setFieldErrors(found)
    if (found.schedule || found.note) return
    setSubmitting(true)
    setError(null)
    setMessage(null)
    try {
      const saved = await createElderValueAddedServiceRequest({
        valueAddedServiceId: serviceId,
        requestedSchedule: schedule,
        specialInstructions: note.trim() || null,
      })
      setRequests((current) => [saved, ...current])
      setNote('')
      setSchedule('')
      setMessage('Request sent. Status: Pending approval.')
    } catch (cause) {
      const fields = serverFieldErrors(cause, { requestedSchedule: 'schedule', specialInstructions: 'note' })
      if (BOOKING_TIME_CODES.has(problemCode(cause) ?? '') && cause instanceof ApiError) setFieldErrors({ schedule: cause.message })
      else if (fields.schedule || fields.note) setFieldErrors(fields)
      else setError(cause instanceof ApiError ? cause.message : 'Unable to send the service request.')
    } finally {
      setSubmitting(false)
    }
  }

  async function withdraw(id: number) {
    setWithdrawingId(id)
    setError(null)
    setMessage(null)
    try {
      const saved = await withdrawElderValueAddedServiceRequest(id)
      setRequests((current) => current.map((request) => (request.id === id ? saved : request)))
      setMessage('Request cancelled. Your family has been told.')
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Unable to cancel the request.')
    } finally {
      setWithdrawingId(null)
      setConfirmingId(null)
    }
  }

  return (
    <ElderShell>
      <ScreenColumns
        left={
          <>
            <Slot order={1}>
              <ScreenHeader
                backTo="/elder"
                eyebrow={greeting()}
                title="What do you need?"
                subtitle="Choose an available service and when you would like it."
              />
            </Slot>
            <Slot order={2}>{error && <StatusNote tone="problem">{error}</StatusNote>}</Slot>
            <Slot order={3}>{message && <StatusNote tone="success">{message}</StatusNote>}</Slot>
            <Slot order={5}>
              <div className={styles.form}>
                <h2 className={styles.sectionTitle}>My requests</h2>
                {requests.length === 0 ? (
                  <InfoNote>No requests yet.</InfoNote>
                ) : (
                  requests.map((request) => (
                    <InfoCard key={request.id}>
                      <h3 className={styles.familyName}>{request.serviceName}</h3>
                      <p className={styles.familyRelation}>
                        Status: <strong>{request.status}</strong>
                      </p>
                      <p className={styles.familyMeta}>
                        Requested for:{' '}
                        {request.requestedSchedule
                          ? new Date(request.requestedSchedule).toLocaleString()
                          : 'Not specified'}
                      </p>
                      {request.specialInstructions && (
                        <p className={styles.familyRelation}>{request.specialInstructions}</p>
                      )}
                      {request.visitId && (
                        <p className={styles.familyMeta}>Work order visit: #{request.visitId}</p>
                      )}
                      {withdrawable(request) &&
                        (confirmingId === request.id ? (
                          <ActionStack>
                            <WideButton
                              onClick={() => void withdraw(request.id)}
                              disabled={withdrawingId === request.id}
                            >
                              {withdrawingId === request.id ? 'Cancelling…' : 'Yes, cancel it'}
                            </WideButton>
                            <WideButton
                              variant="secondary"
                              onClick={() => setConfirmingId(null)}
                              disabled={withdrawingId === request.id}
                            >
                              Keep it
                            </WideButton>
                          </ActionStack>
                        ) : (
                          <WideButton variant="secondary" onClick={() => setConfirmingId(request.id)}>
                            Cancel this request
                          </WideButton>
                        ))}
                    </InfoCard>
                  ))
                )}
              </div>
            </Slot>
            <Slot order={6}>
              <p className={styles.meta}>This records a service request only. No payment is taken here.</p>
            </Slot>
          </>
        }
        right={
          <Slot order={4}>
            {loading ? (
              <p className={styles.lead}>Loading extra services…</p>
            ) : services.length === 0 ? (
              <InfoCard>
                <h2 className={styles.sectionTitle}>No extra services available</h2>
              </InfoCard>
            ) : (
              <InfoCard>
                <div className={styles.form}>
                  <h2 className={styles.sectionTitle}>New request</h2>
                  <ActionStack>
                    {services.map((item) => (
                      <ChoiceButton
                        key={item.id}
                        label={item.name}
                        selected={item.id === serviceId}
                        onSelect={() => setServiceId(item.id)}
                        disabled={submitting}
                      />
                    ))}
                  </ActionStack>
                  {selected?.description && <p className={styles.lead}>{selected.description}</p>}
                  {selected && <p className={styles.familyMeta}>{durationText(selected.durationMinutes)}</p>}
                  <p className={styles.familyMeta}>Ask at least {MIN_NOTICE_HOURS} hours ahead.</p>
                  <div className={styles.fieldLabel}>
                    <label htmlFor={`${id}-schedule`}>Requested date and time</label>
                    <input
                      id={`${id}-schedule`}
                      type="datetime-local"
                      {...bookingWindow()}
                      step={STEP_MINUTES * 60}
                      value={schedule}
                      disabled={submitting}
                      aria-invalid={!!fieldErrors.schedule}
                      aria-describedby={[fieldErrors.schedule && `${id}-schedule-error`, selected && `${id}-schedule-hint`]
                        .filter(Boolean).join(' ') || undefined}
                      onChange={(event) => {
                        setSchedule(event.target.value)
                        setFieldErrors((current) => ({ ...current, schedule: undefined }))
                      }}
                    />
                    {fieldErrors.schedule && <span id={`${id}-schedule-error`} className={styles.fieldError}>{fieldErrors.schedule}</span>}
                    {selected && <span id={`${id}-schedule-hint`} className={`${styles.familyMeta} ${styles.hint}`}>
                      {bookingHint(selected.durationMinutes)}
                    </span>}
                  </div>
                  <div className={styles.fieldLabel}>
                    <label htmlFor={`${id}-note`}>Anything we should know?</label>
                    <textarea
                      id={`${id}-note`}
                      value={note}
                      maxLength={1000}
                      disabled={submitting}
                      aria-invalid={!!fieldErrors.note}
                      aria-describedby={fieldErrors.note ? `${id}-note-error` : undefined}
                      onChange={(event) => {
                        setNote(event.target.value)
                        setFieldErrors((current) => ({ ...current, note: undefined }))
                      }}
                      placeholder="Optional instructions"
                    />
                    {fieldErrors.note && <span id={`${id}-note-error`} className={styles.fieldError}>{fieldErrors.note}</span>}
                  </div>
                  <WideButton onClick={submit} disabled={submitting}>
                    {submitting ? 'Sending…' : `Request ${selected?.name ?? 'service'}`}
                  </WideButton>
                </div>
              </InfoCard>
            )}
          </Slot>
        }
        footer={
          <ScreenFooter>
            <SpeakButton />
          </ScreenFooter>
        }
      />
    </ElderShell>
  )
}
