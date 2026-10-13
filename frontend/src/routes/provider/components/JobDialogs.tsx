import { useState } from 'react'
import type { FormEvent } from 'react'
import { fromSgtInput, toSgtInput } from '../../../features/provider-platform/format'
import type { DeclineReason, Job } from '../../../features/provider-platform/types'
import { Button, Dialog, Field, TextArea, TextInput } from './ui'
import styles from './JobDialogs.module.css'

const REASONS: { value: DeclineReason; label: string }[] = [
  { value: 'OUTSIDE_HOURS', label: 'Outside our service hours' },
  { value: 'NO_PRACTITIONER', label: 'No practitioner available' },
  { value: 'OUTSIDE_AREA', label: 'Outside our service area' },
  { value: 'OTHER', label: 'Other' },
]

/** Asks why the provider turns the job down; CareLink then offers it to another provider. */
export function DeclineDialog({
  job,
  busy,
  onCancel,
  onDecline,
}: {
  job: Job
  busy: boolean
  onCancel: () => void
  onDecline: (body: { reason: DeclineReason; note: string | null }) => void
}) {
  const [reason, setReason] = useState<DeclineReason | null>(null)
  const [note, setNote] = useState('')

  return (
    <Dialog
      title={
        <>
          Decline <span className={styles.mono}>{job.id}</span>?
        </>
      }
      onClose={onCancel}
      footer={
        <>
          <Button onClick={onCancel} disabled={busy}>
            Cancel
          </Button>
          <Button
            variant="danger"
            disabled={reason === null}
            busy={busy && 'Declining…'}
            onClick={() => reason && onDecline({ reason, note: note.trim() || null })}
          >
            Decline job
          </Button>
        </>
      }
    >
      <fieldset className={styles.reasons}>
        <legend className={styles.legend}>Reason</legend>
        {REASONS.map((r) => (
          <label key={r.value} className={styles.radio}>
            <input type="radio" name="decline-reason" value={r.value} checked={reason === r.value} onChange={() => setReason(r.value)} />
            {r.label}
          </label>
        ))}
      </fieldset>
      <Field label="Note to CareLink" htmlFor="decline-note" optional>
        <TextArea id="decline-note" rows={3} className={styles.note} value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
      </Field>
      <p className={styles.hint}>CareLink will offer the job to another provider.</p>
    </Dialog>
  )
}

/**
 * Records when the work actually started and ended — the scheduled times to begin with — and a
 * note for the family's report. All three are needed, and the end must come after the start.
 */
export function CompleteDialog({
  job,
  busy,
  onCancel,
  onComplete,
}: {
  job: Job
  busy: boolean
  onCancel: () => void
  onComplete: (body: { actualStart: string; actualEnd: string; note: string }) => void
}) {
  const [start, setStart] = useState(() => toSgtInput(job.scheduledStart))
  const [end, setEnd] = useState(() => toSgtInput(new Date(Date.parse(job.scheduledStart) + job.durationMin * 60_000).toISOString()))
  const [note, setNote] = useState('')
  const [attempted, setAttempted] = useState(false)

  const startAt = fromSgtInput(start)
  const endAt = fromSgtInput(end)
  const timesError = !startAt || !endAt ? 'Enter both times.' : Date.parse(endAt) <= Date.parse(startAt) ? 'The end must be after the start.' : null
  const noteError = note.trim() ? null : 'Add a note for the family.'

  function submit(e?: FormEvent) {
    e?.preventDefault()
    setAttempted(true)
    if (timesError || noteError || !startAt || !endAt) return
    onComplete({ actualStart: startAt, actualEnd: endAt, note: note.trim() })
  }

  return (
    <Dialog
      title={
        <>
          Mark <span className={styles.mono}>{job.id}</span> complete
        </>
      }
      onClose={onCancel}
      footer={
        <>
          <Button onClick={onCancel} disabled={busy}>
            Cancel
          </Button>
          <Button variant="primary" busy={busy && 'Saving…'} onClick={() => submit()}>
            Mark complete
          </Button>
        </>
      }
    >
      <form className={styles.form} onSubmit={submit} noValidate>
        <div className={styles.times}>
          <Field label="Actual start" htmlFor="complete-start">
            <TextInput id="complete-start" mono type="datetime-local" value={start} onChange={(e) => setStart(e.target.value)} invalid={attempted && Boolean(timesError)} required />
          </Field>
          <Field label="Actual end" htmlFor="complete-end" error={attempted ? (timesError ?? undefined) : undefined} errorId="complete-times-error">
            <TextInput
              id="complete-end"
              mono
              type="datetime-local"
              value={end}
              onChange={(e) => setEnd(e.target.value)}
              invalid={attempted && Boolean(timesError)}
              aria-describedby={attempted && timesError ? 'complete-times-error' : undefined}
              required
            />
          </Field>
        </div>
        <Field
          label="Completion note"
          htmlFor="complete-note"
          helper="Shared with the family in their CareLink report."
          error={attempted ? (noteError ?? undefined) : undefined}
          errorId="complete-note-error"
        >
          <TextArea
            id="complete-note"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            invalid={attempted && Boolean(noteError)}
            aria-describedby={attempted && noteError ? 'complete-note-error' : undefined}
            maxLength={1000}
            required
          />
        </Field>
      </form>
    </Dialog>
  )
}
