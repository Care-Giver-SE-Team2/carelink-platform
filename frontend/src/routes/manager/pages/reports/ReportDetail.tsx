import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'

import { appendAmendment } from '../../../../features/reports/api'
import {
  amendmentKindLabels,
  audienceLabels,
  audienceNotes,
  generatedByLabels,
  problemDetail,
  reportPeriod,
  reportTime,
  statusLabels,
} from '../../../../features/reports/presentation'
import type { ReportAmendmentKind } from '../../../../features/reports/types'
import { useReportDetail } from '../../../../features/reports/useReportQueries'
import { ManagerShell } from '../../components/ManagerShell'
import { ReportFeedback } from './ReportFeedback'
import { ReportSectionCard } from './ReportSectionCard'
import styles from './Reports.module.css'

/**
 * UC-MG07 — one filed report, as its reader will see it.
 *
 * The sections come in the order every reader's version shares, each as a
 * card laid out for its kind (ReportSectionCard), with the gaps named at the
 * top when the period's data was not complete, the disclaimer at the bottom
 * when this reader gets one, and the notes under that. A report filed before
 * sections carried numbers and series shows its text alone.
 *
 * There is nothing here to edit or delete, because a filed report cannot be
 * either. The only thing a manager can do is append a note - a correction, or
 * a follow-up on something the report recorded; the report is read again
 * afterwards rather than having the note pushed onto it here, so what is on
 * the screen is what is on file.
 */
export default function ReportDetail() {
  const { id } = useParams()
  const reportId = Number(id)
  const valid = Number.isSafeInteger(reportId) && reportId > 0

  const detail = useReportDetail(reportId)

  const [note, setNote] = useState('')
  const [kind, setKind] = useState<ReportAmendmentKind>('CORRECTION')
  const [busy, setBusy] = useState(false)
  const [amendError, setAmendError] = useState<unknown>(null)

  if (!valid) {
    return (
      <ManagerShell>
        <div className={styles.page}>
          <p className={styles.note}>That is not a report number.</p>
          <Link className={styles.linkButton} to="/manager/reports">
            Back to reports
          </Link>
        </div>
      </ManagerShell>
    )
  }

  if (detail.resource.status === 'loading') {
    return (
      <ManagerShell headerContext={<>Reports / RPT-{reportId}</>}>
        <div className={styles.page}>
          <p className={styles.note}>Loading the report…</p>
        </div>
      </ManagerShell>
    )
  }

  if (detail.resource.status === 'error') {
    return (
      <ManagerShell headerContext={<>Reports / RPT-{reportId}</>}>
        <div className={styles.page}>
          <ReportFeedback error={detail.resource.error} onRetry={detail.refresh} />
          <Link className={styles.linkButton} to="/manager/reports">
            Back to reports
          </Link>
        </div>
      </ManagerShell>
    )
  }

  const report = detail.resource.data

  async function append(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setAmendError(null)
    try {
      await appendAmendment(report.id, note, kind)
      setNote('')
      setKind('CORRECTION')
      detail.refresh()
    } catch (error) {
      setAmendError(error)
    } finally {
      setBusy(false)
    }
  }

  return (
    <ManagerShell
      headerContext={<>Reports / RPT-{report.id}</>}
      headerRight={
        <>
          <span className={styles.readerTag}>{audienceLabels[report.audience]}</span>
          <span className={styles.statusTag}>{statusLabels[report.status]}</span>
          <span className={styles.headerNote}>filed {reportTime(report.createdAt)}</span>
        </>
      }
    >
      <div className={styles.page}>
        <div className={styles.reportHead}>
          <p className={styles.eyebrow}>
            {audienceLabels[report.audience]} version · RPT-{report.id}
          </p>
          <h1>
            {audienceLabels[report.audience]} report — Elder #{report.elderId}
          </h1>
          <p className={styles.meta}>
            {reportPeriod(report.periodStart, report.periodEnd)} · {generatedByLabels[report.generatedBy]}
          </p>
          <p className={styles.readerNote}>{audienceNotes[report.audience]}</p>
        </div>

        {!report.dataComplete && (
          <section className={styles.incomplete} aria-label="Data incomplete">
            <h2>Data incomplete</h2>
            <ul>
              {report.missingItems.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </section>
        )}

        <div className={styles.cards}>
          {report.sections.map((section) => (
            <ReportSectionCard key={section.title} section={section} />
          ))}
        </div>

        {report.disclaimer && <p className={styles.disclaimer}>{report.disclaimer}</p>}

        <section className={styles.card}>
          <h2 className={styles.cardTitle}>Corrections and follow-ups — appended, never edited</h2>
          {report.amendments.length === 0 ? (
            <p className={styles.note}>Nothing has been appended.</p>
          ) : (
            <ol className={styles.amendments}>
              {report.amendments.map((amendment) => (
                <li key={amendment.id} className={styles.amendment}>
                  <span className={amendment.kind === 'FOLLOW_UP' ? styles.followUpTag : styles.correctionTag}>
                    {amendmentKindLabels[amendment.kind ?? 'CORRECTION']}
                  </span>
                  <span className={styles.amendmentMeta}>
                    {reportTime(amendment.createdAt)} · user #{amendment.authorUserId}
                  </span>
                  <span className={styles.amendmentNote}>{amendment.note}</span>
                </li>
              ))}
            </ol>
          )}
        </section>

        <form className={styles.panel} onSubmit={append}>
          <label>
            Kind of note
            <select value={kind} onChange={(event) => setKind(event.target.value as ReportAmendmentKind)}>
              <option value="CORRECTION">Correction — the report said something wrong or missing</option>
              <option value="FOLLOW_UP">Follow-up — what was done about something it recorded</option>
            </select>
          </label>
          <label>
            {amendmentKindLabels[kind]}
            <textarea
              value={note}
              onChange={(event) => setNote(event.target.value)}
              rows={3}
              maxLength={1000}
            />
          </label>
          <div className={styles.panelActions}>
            <button type="submit" disabled={busy}>
              {busy ? 'Appending…' : `Append ${amendmentKindLabels[kind].toLowerCase()}`}
            </button>
          </div>
        </form>

        {amendError !== null && (
          <p className={styles.inlineError} role="alert">
            {problemDetail(amendError)}
          </p>
        )}

        <Link className={styles.linkButton} to="/manager/reports">
          Back to reports
        </Link>
      </div>
    </ManagerShell>
  )
}
