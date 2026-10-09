import { useState } from 'react'
import { Navigate } from 'react-router-dom'

import { visitTime } from '../../../features/absences/presentation'
import { problemDetail } from '../../../features/incidents/presentation'
import { ApiError } from '../../../shared/api/client'
import { decideSpotCheck } from '../../../features/spot-checks/api'
import { endingLine, stageLabels } from '../../../features/spot-checks/presentation'
import type { SpotCheck } from '../../../features/spot-checks/types'
import { useRefreshSpotChecks, useSpotChecks } from '../../../features/spot-checks/useSpotCheckQueries'
import styles from '../changes/FamilyRosterChanges.module.css'

/**
 * The family's part of UC-MG08 (the former UC-FM07): a manager would like to
 * watch one of the elder's visits, and nobody comes to watch unless the family
 * agrees. Declining needs a reason and changes nothing about normal service.
 * Conclusions are shown here once they are recorded.
 *
 * @author Wang Ziyu
 */
export function FamilySpotChecksPage() {
  const { data, error, isPending, refetch } = useSpotChecks()
  const waiting = (data ?? []).filter((check) => check.stage === 'AWAITING_FAMILY')
  const others = (data ?? []).filter((check) => check.stage !== 'AWAITING_FAMILY')

  // The landing page is the only sign-in screen.
  if (error instanceof ApiError && error.status === 401) return <Navigate to="/" replace />

  return (
    <div className={styles.page}>
      <header className={styles.hero}>
        <p className={styles.eyebrow}>Your family's care</p>
        <h1>Spot checks</h1>
        <p className={styles.meta}>A manager may ask to be present at a visit to check the quality of care.</p>
      </header>

      {isPending && (
        <div className={`${styles.state} ${styles.loading}`} role="status">
          <span className={styles.spinner} aria-hidden="true" />
          Loading spot checks…
        </div>
      )}
      {error && (
        <section className={styles.state} role="alert">
          <h2>Unable to load spot checks</h2>
          <p>{problemDetail(error)}</p>
          <button type="button" onClick={() => refetch()}>Try again</button>
        </section>
      )}
      {data && data.length === 0 && (
        <section className={styles.state}>
          <h2>No spot checks</h2>
          <p>Nobody has asked to watch a visit.</p>
        </section>
      )}

      {data && data.length > 0 && (
        // Desktop: what needs an answer on the left, booked and concluded checks on the right; stacked on a phone.
        <div className={styles.split}>
          <section className={styles.column} aria-label="Waiting for your answer">
            <h2 className={styles.label}>Waiting for your answer</h2>
            {waiting.length > 0 ? <>
              <p className={styles.note}>Nobody comes to watch unless you agree, and you can see what they concluded.</p>
              {waiting.map((check) => (
                <PendingCheck key={check.id} check={check} />
              ))}
            </> : <p className={styles.caughtUp}>No request to watch a visit is waiting for you.</p>}
          </section>

          {others.length > 0 && (
            <section className={styles.column} aria-label="Other spot checks">
              <h2 className={styles.label}>Other spot checks</h2>
              <ul className={styles.list}>
                {others.map((check) => (
                  <li key={check.id}>
                    <p className={styles.rowTitle}>{visitTime(check.visitTime)}</p>
                    <p className={styles.rowSub}>{check.elderName} · {check.caregiverName}</p>
                    <p className={styles.rowSub}>{check.stage === 'SCHEDULED' ? stageLabels.SCHEDULED : endingLine(check)}</p>
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

/** One request waiting for the family: agree, or decline with a reason. */
function PendingCheck({ check }: { check: SpotCheck }) {
  const refresh = useRefreshSpotChecks()
  const [reason, setReason] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<unknown>(null)

  async function answer(approve: boolean) {
    setSending(true)
    setError(null)
    try {
      await decideSpotCheck(check.id, approve, approve ? undefined : reason.trim())
      await refresh()
    } catch (failure) {
      setError(failure)
    } finally {
      setSending(false)
    }
  }

  return (
    <article className={styles.card} aria-label={`Spot check of the visit on ${visitTime(check.visitTime)}`}>
      <div>
        <p className={styles.rowTitle}>{visitTime(check.visitTime)}</p>
        <p className={styles.rowSub}>{check.elderName}</p>
      </div>
      <p className={styles.body}>
        A manager would like to be present at this visit with {check.caregiverName}. Why: {check.purpose}
      </p>
      <div className={styles.block}>
        <button type="button" className={styles.primary} disabled={sending} onClick={() => answer(true)}>
          Agree
        </button>
      </div>
      <div className={styles.block}>
        <label className={styles.fieldLabel} htmlFor={`decline-${check.id}`}>Or decline, and tell us why</label>
        <div className={styles.inline}>
          <input
            id={`decline-${check.id}`}
            type="text"
            maxLength={255}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
          <button type="button" disabled={sending || !reason.trim()} onClick={() => answer(false)}>
            Decline
          </button>
        </div>
      </div>
      {error !== null && (
        <p className={styles.error} role="alert">
          {problemDetail(error)}
        </p>
      )}
    </article>
  )
}
