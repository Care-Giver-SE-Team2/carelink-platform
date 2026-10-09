import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate } from 'react-router-dom'

import { decideChange } from '../../../features/absences/api'
import { settledLine, visitTime } from '../../../features/absences/presentation'
import type { FamilyChange, FamilyDecision } from '../../../features/absences/types'
import { useFamilyChanges, useRefreshAbsences } from '../../../features/absences/useAbsenceQueries'
import { problemDetail } from '../../../features/incidents/presentation'
import { ApiError } from '../../../shared/api/client'
import styles from './FamilyRosterChanges.module.css'

/**
 * The family's part of UC-MG04, steps 4 and 5: when a caregiver is away, the
 * family is told who would come instead and may keep that person, pick another
 * of the suggestions, move the visit, or skip it - until the time shown, after
 * which the institution goes ahead with its suggestion and says so.
 *
 * @author Wang Ziyu
 */
export function FamilyRosterChangesPage() {
  const { data, error, isPending, refetch } = useFamilyChanges()
  const waiting = (data ?? []).filter((change) => change.status === 'AWAITING_FAMILY')
  const others = (data ?? []).filter((change) => change.status !== 'AWAITING_FAMILY')

  // The landing page is the only sign-in screen.
  if (error instanceof ApiError && error.status === 401) return <Navigate to="/" replace />

  return (
    <div className={styles.page}>
      <header className={styles.hero}>
        <p className={styles.eyebrow}>Your family's care</p>
        <h1>Visit changes</h1>
        <p className={styles.meta}>When a caregiver is away, we suggest who comes instead.</p>
      </header>

      {isPending && (
        <div className={`${styles.state} ${styles.loading}`} role="status">
          <span className={styles.spinner} aria-hidden="true" />
          Loading changes…
        </div>
      )}
      {error && (
        <section className={styles.state} role="alert">
          <h2>Unable to load visit changes</h2>
          <p>{problemDetail(error)}</p>
          <button type="button" onClick={() => refetch()}>Try again</button>
        </section>
      )}
      {data && data.length === 0 && (
        <section className={styles.state}>
          <h2>No changes</h2>
          <p>Every visit is going ahead with its usual caregiver.</p>
        </section>
      )}

      {data && data.length > 0 && (
        // Desktop: what needs an answer on the left, what is settled on the right; stacked on a phone.
        <div className={styles.split}>
          <section className={styles.column} aria-label="Waiting for your answer">
            <h2 className={styles.label}>Waiting for your answer</h2>
            {waiting.length > 0 ? <>
              <p className={styles.note}>
                Choose before the time shown; if we have not heard from you by then, the suggested caregiver will come.
              </p>
              {waiting.map((change) => (
                <PendingChange key={change.id} change={change} />
              ))}
            </> : <p className={styles.caughtUp}>Every change to your visits has been answered.</p>}
          </section>

          {others.length > 0 && (
            <section className={styles.column} aria-label="Earlier changes">
              <h2 className={styles.label}>Earlier changes</h2>
              <ul className={styles.list}>
                {others.map((change) => (
                  <li key={change.id}>
                    <p className={styles.rowTitle}>{visitTime(change.visitStart)}</p>
                    <p className={styles.rowSub}>
                      {change.elderName} ·{' '}
                      {change.status === 'UNCOVERED'
                        ? 'We are still finding a caregiver; a manager is handling it.'
                        : settledLine(change)}
                    </p>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
      )}
    </div>
  )
}

/** One change waiting for the family: the suggestion, the other options, and the time to answer by. */
function PendingChange({ change }: { change: FamilyChange }) {
  const refreshAll = useRefreshAbsences()
  const [caregiverId, setCaregiverId] = useState<number | null>(change.suggestedCaregiverId)
  const [newStart, setNewStart] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const suggested = change.options.find((option) => option.caregiverId === change.suggestedCaregiverId)

  async function send(decision: FamilyDecision) {
    setSending(true)
    setError(null)
    try {
      await decideChange(change.id, decision)
      await refreshAll()
    } catch (failure) {
      setError(failure)
    } finally {
      setSending(false)
    }
  }

  function keep(event: FormEvent) {
    event.preventDefault()
    send({ choice: 'CHANGE_CAREGIVER', caregiverId: caregiverId ?? undefined })
  }

  function move(event: FormEvent) {
    event.preventDefault()
    send({ choice: 'RESCHEDULE', newStart: newStart.length === 16 ? newStart + ':00' : newStart })
  }

  return (
    <article className={styles.card} aria-label={`Change to the visit on ${visitTime(change.visitStart)}`}>
      <div>
        <p className={styles.rowTitle}>{visitTime(change.visitStart)}</p>
        <p className={styles.rowSub}>{change.elderName}</p>
      </div>
      <p className={styles.body}>
        {change.usualCaregiverName} is away for this visit.
        {suggested ? ` We suggest ${suggested.name}${suggested.reason ? ` (${suggested.reason.toLowerCase()})` : ''}.` : ''}
      </p>
      <p className={styles.deadline}>Please answer by {visitTime(change.respondBy)}</p>

      <form onSubmit={keep} className={styles.block}>
        <fieldset>
          <legend className={styles.fieldLabel}>Who should come</legend>
          <div className={styles.options}>
            {change.options.map((option) => (
              <label key={option.caregiverId} className={styles.option}>
                <input
                  type="radio"
                  name={`caregiver-${change.id}`}
                  checked={caregiverId === option.caregiverId}
                  onChange={() => setCaregiverId(option.caregiverId)}
                />
                <span>
                  <strong>{option.name}</strong>
                  {option.reason && <small>{option.reason}</small>}
                </span>
              </label>
            ))}
          </div>
        </fieldset>
        <button type="submit" className={styles.primary} disabled={sending || caregiverId === null}>
          Confirm caregiver
        </button>
      </form>

      <form onSubmit={move} className={styles.block}>
        <label className={styles.fieldLabel} htmlFor={`move-${change.id}`}>Or move the visit to</label>
        <div className={styles.inline}>
          <input
            id={`move-${change.id}`}
            type="datetime-local"
            value={newStart}
            onChange={(event) => setNewStart(event.target.value)}
          />
          <button type="submit" disabled={sending || !newStart}>Move visit</button>
        </div>
      </form>

      <div className={styles.block}>
        <button type="button" className={styles.quiet} disabled={sending} onClick={() => send({ choice: 'SKIP' })}>
          Skip this visit
        </button>
      </div>

      {error !== null && (
        <p className={styles.error} role="alert">
          {problemDetail(error)}
        </p>
      )}
    </article>
  )
}
