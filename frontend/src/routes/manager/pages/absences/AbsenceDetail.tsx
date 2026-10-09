import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import {
  approveAbsence,
  assignCaregiver,
  confirmCoverage,
  rejectAbsence,
  rerosterAbsence,
} from '../../../../features/absences/api'
import {
  absenceDays,
  absenceStatusLabels,
  absenceTypeLabels,
  changeStatusLabels,
  checkResultLabels,
  objectiveLabels,
  objectives,
  settledLine,
  visitTime,
} from '../../../../features/absences/presentation'
import type {
  AbsenceCase,
  ChangeView,
  ReRosterOutcome,
  RosteringObjective,
} from '../../../../features/absences/types'
import { useAbsence, useRefreshAbsences } from '../../../../features/absences/useAbsenceQueries'
import { problemDetail } from '../../../../features/incidents/presentation'
import { ManagerShell } from '../../components/ManagerShell'
import { ExceptionFeedback } from '../exceptions/ExceptionFeedback'
import styles from './Absences.module.css'

/**
 * UC-MG04 steps 2 to 7 for one absence: re-roster now, see every vacated visit
 * with the replacements the search found and why each candidate ranked where
 * they did - or what excluded them - follow the families' answers, and confirm
 * the coverage once nothing is left open.
 */
export default function AbsenceDetail() {
  const id = Number(useParams().id)
  const { data, error, isPending, refetch } = useAbsence(id)
  const refreshAll = useRefreshAbsences()
  const [objective, setObjective] = useState<RosteringObjective>('CONTINUITY')
  const [working, setWorking] = useState(false)
  const [actionError, setActionError] = useState<unknown>(null)
  const [lastRun, setLastRun] = useState<ReRosterOutcome | null>(null)

  async function act(action: () => Promise<unknown>) {
    setWorking(true)
    setActionError(null)
    try {
      await action()
      await refreshAll()
    } catch (failure) {
      setActionError(failure)
    } finally {
      setWorking(false)
    }
  }

  return (
    <ManagerShell
      headerContext={
        <span className={styles.crumbs}>
          <Link to="/manager/absences">Absences</Link> / {data ? data.absence.caregiverName : '…'}
        </span>
      }
    >
      <div className={styles.page}>
        {isPending && <p className={styles.note}>Loading the absence…</p>}
        {error && <ExceptionFeedback error={error} onRetry={() => refetch()} />}
        {data && (
          <>
            <Header data={data} />

            {actionError !== null && (
              <p className={styles.inlineError} role="alert">
                {problemDetail(actionError)}
              </p>
            )}

            {data.absence.status === 'PENDING' && (
              <div className={styles.formActions}>
                <button type="button" disabled={working} onClick={() => act(() => approveAbsence(id))}>
                  Approve
                </button>
                <button
                  type="button"
                  className={styles.secondary}
                  disabled={working}
                  onClick={() => act(() => rejectAbsence(id))}
                >
                  Reject
                </button>
              </div>
            )}

            {data.absence.status === 'APPROVED' && (
              <section className={styles.panel} aria-label="Re-roster">
                <div className={styles.formRow}>
                  <label>
                    Rank replacements by
                    <select
                      value={objective}
                      onChange={(event) => setObjective(event.target.value as RosteringObjective)}
                    >
                      {objectives.map((value) => (
                        <option key={value} value={value}>
                          {objectiveLabels[value]}
                        </option>
                      ))}
                    </select>
                  </label>
                  <button
                    type="button"
                    disabled={working}
                    onClick={() =>
                      act(async () => {
                        const result = await rerosterAbsence(id, objective)
                        setLastRun(result.outcome)
                      })
                    }
                  >
                    {working ? 'Working…' : 'Re-roster now'}
                  </button>
                  <button
                    type="button"
                    className={styles.secondary}
                    disabled={working || !canConfirm(data)}
                    onClick={() => act(() => confirmCoverage(id))}
                  >
                    Confirm coverage
                  </button>
                </div>
                {lastRun && <p className={styles.note}>{runLine(lastRun)}</p>}
                <p className={styles.note}>
                  Families have two hours to answer, less when a visit is close; after that the best
                  replacement is put on the visit and the family is told.
                </p>
              </section>
            )}

            {data.notYetRerostered.length > 0 && (
              <section aria-label="Not yet re-rostered">
                <h2 className={styles.sectionTitle}>Not yet re-rostered ({data.notYetRerostered.length})</h2>
                <ul className={styles.plainList}>
                  {data.notYetRerostered.map((visit) => (
                    <li key={visit.visitId}>
                      <span className={styles.mono}>{visitTime(visit.start)}</span> · {visit.elderName}
                      {visit.serviceType ? ` · ${visit.serviceType}` : ''}
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {data.changes.length > 0 && (
              <section aria-label="Vacated visits">
                <h2 className={styles.sectionTitle}>Vacated visits ({data.changes.length})</h2>
                <ul className={styles.cards}>
                  {data.changes.map((change) => (
                    <ChangeCard
                      key={change.id}
                      change={change}
                      working={working}
                      onAssign={(caregiverId) => act(() => assignCaregiver(id, change.id, caregiverId))}
                    />
                  ))}
                </ul>
              </section>
            )}
          </>
        )}
      </div>
    </ManagerShell>
  )
}

function Header({ data }: { data: AbsenceCase }) {
  const absence = data.absence
  return (
    <div className={styles.pageHead}>
      <h1>
        {absence.caregiverName} — {absenceDays(absence.startDate, absence.endDate)}
      </h1>
      <span className={styles.meta}>
        {absenceTypeLabels[absence.type]} · {absenceStatusLabels[absence.status]}
        {absence.reason ? ` · ${absence.reason}` : ''}
        {absence.coverageConfirmedAt ? ` · coverage confirmed ${visitTime(absence.coverageConfirmedAt)}` : ''}
      </span>
    </div>
  )
}

/**
 * One vacated visit: where it stands, and every candidate the search considered.
 * Where the manager may hand-pick, each candidate who passed every rule can be
 * put on the visit; the server checks them again against the roster as it is now.
 */
function ChangeCard({
  change,
  working,
  onAssign,
}: {
  change: ChangeView
  working: boolean
  onAssign: (caregiverId: number) => void
}) {
  return (
    <li className={styles.card}>
      <div className={styles.cardHead}>
        <span className={styles.mono}>{visitTime(change.visitStart)}</span>
        <span className={styles.cardTitle}>{change.elderName}</span>
        <span className={styles.statusTag} data-status={change.status}>
          {changeStatusLabels[change.status]}
        </span>
      </div>

      {change.status === 'AWAITING_FAMILY' && (
        <p className={styles.cardMeta}>
          Suggested {change.proposedCaregiver?.name ?? '—'}; the family answers by{' '}
          <span className={styles.mono}>{visitTime(change.respondBy)}</span>
        </p>
      )}
      {change.status === 'RESOLVED' && <p className={styles.cardMeta}>{settledLine(change)}</p>}
      {change.status === 'UNCOVERED' && (
        <p className={styles.cardMeta}>
          Nobody is free.{' '}
          {change.incidentId !== null && (
            <Link to={'/manager/exceptions/' + change.incidentId}>Exception EXC-{change.incidentId}</Link>
          )}{' '}
          is in the queue; re-roster again once somebody is free.
        </p>
      )}
      {change.note && <p className={styles.cardNote}>{change.note}</p>}

      {change.candidates.length > 0 && (
        <table className={styles.table}>
          <thead>
            <tr>
              <th>#</th>
              <th>Candidate</th>
              <th>Score</th>
              <th>Why</th>
              {change.managerMayAssign && <th aria-label="Assign" />}
            </tr>
          </thead>
          <tbody>
            {change.candidates.map((candidate) => (
              <tr key={candidate.caregiverId} data-outcome={candidate.outcome}>
                <td className={styles.mono}>{candidate.rank ?? '—'}</td>
                <td>
                  {candidate.name}
                  {candidate.outcome === 'SELECTED' && <span className={styles.selected}> · on the visit</span>}
                </td>
                <td className={styles.mono}>{candidate.score ?? '—'}</td>
                <td>
                  <details>
                    <summary>
                      {candidate.excludedBy ? `Excluded: ${candidate.reason ?? candidate.excludedBy}` : candidate.reason}
                    </summary>
                    <ul className={styles.checks}>
                      {candidate.checks.map((check, index) => (
                        <li key={index} data-result={check.result}>
                          <span className={styles.mono}>{checkResultLabels[check.result]}</span> {check.name ?? check.code}
                          {check.detail ? ` — ${check.detail}` : ''}
                        </li>
                      ))}
                    </ul>
                  </details>
                </td>
                {change.managerMayAssign && (
                  <td>
                    {candidate.outcome === 'SUGGESTED' && (
                      <button
                        type="button"
                        className={styles.secondary}
                        disabled={working}
                        aria-label={`Assign ${candidate.name}`}
                        onClick={() => onAssign(candidate.caregiverId)}
                      >
                        Assign
                      </button>
                    )}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </li>
  )
}

function canConfirm(data: AbsenceCase): boolean {
  const absence = data.absence
  return absence.status === 'APPROVED' && !absence.coverageConfirmedAt && absence.notYetRerostered === 0
    && absence.awaitingFamily === 0 && data.changes.length > 0
}

function runLine(outcome: ReRosterOutcome): string {
  if (outcome.searched === 0) return 'Nothing left to re-roster: every vacated visit already has a change.'
  return `Searched ${outcome.searched}: ${outcome.offered} offered to families, ${outcome.settled} settled at once, `
    + `${outcome.uncovered} uncovered.`
}
