import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../../shared/api/client'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import { todayInSingapore } from './format'
import SelfServiceError from './SelfServiceError'
import styles from './Caregiver.module.css'

type Summary = { visitId: number; currentVisitStatus: string; firstOwnActionAt: string; lastOwnActionAt: string; ownCheckInAt: string | null; ownCheckOutAt: string | null; commandCount: number }
type Month = { items: Summary[]; total: number; coverageNote: string }
type Fact = { action: string; sourceId: number; visitVersion: number; recordedAt: string
  task: { taskId: number; status: string; outcome: string | null; caregiverNote: string | null } | null
  health: { healthFlag: string; healthNote: string | null; readings: { metric: string; value: number; unit: string }[] } | null
  evidence: { evidenceId: number; kind: string; verificationStatus: string; fileName: string; downloadPath: string } | null
  report: { id: number; category: string; severity: string; description: string; status: string } | null }
type History = { visitId: number; facts: Fact[]; spotChecks: { id: number; result: string; checkedAt: string | null; ownResponse: string | null }[]; total: number }
const timestamp = (value: string | null) => value ? value.replace('T', ' ') + ' (SGT)' : 'No attributable record'
export default function HistoryPage() {
  const [month, setMonth] = useState(() => todayInSingapore().slice(0, 7))
  const [draft, setDraft] = useState(month)
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [invalid, setInvalid] = useState('')
  const load = useCallback((signal: AbortSignal) => api<Month>(`/caregivers/me/history?month=${encodeURIComponent(month)}&page=${page}&size=20${status ? '&status=' + status : ''}`, { signal }), [month, page, status])
  const { result, reload } = useCaregiverQuery(`history:${month}:${status}:${page}`, load, true)
  return <div className={styles.page}><div className={styles.heading}><div><p className={styles.eyebrow}>Caregiver workspace</p><h1>My execution history</h1><p className={styles.muted}>Your own submitted actions, grouped by visit.</p></div></div><div className={styles.toolbar}><button className={styles.refresh} onClick={reload}>Refresh</button></div>
    <p>Read-only records of your own submitted actions, grouped by Visit. The month uses actual action times in Singapore, not the current schedule. No export or access to another caregiver's work is provided.</p>
    <form className={styles.dateForm} onSubmit={event => { event.preventDefault(); if (!/^(20\d{2}|2100)-(0[1-9]|1[0-2])$/.test(draft)) { setInvalid('Enter a month as YYYY-MM.'); return } setInvalid(''); setMonth(draft); setPage(0) }}>
      <label>Month (YYYY-MM)<input inputMode="numeric" placeholder="YYYY-MM" value={draft} onChange={event => setDraft(event.target.value)} /></label><button className={styles.button}>Apply month</button>
      <label>Current Visit status<select value={status} onChange={event => { setStatus(event.target.value); setPage(0) }}><option value="">All statuses</option>{['SCHEDULED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'VERIFIED', 'AUTO_CLOSED', 'EXCEPTION', 'CANCELLED'].map(value => <option key={value}>{value}</option>)}</select></label>
    </form>
    {invalid && <p role="alert">{invalid}</p>}
    {result.status === 'loading' && <p role="status">Loading your history…</p>}
    {result.status === 'error' && <SelfServiceError error={result.error} retry={reload} subject="your history" />}
    {result.status === 'success' && <><p className={styles.readOnly}>{result.data.coverageNote}</p>
      {result.data.items.length === 0 && <p>No attributable actions found for this month and status.</p>}
      {result.data.items.map(item => <article key={item.visitId} className={styles.card}><h2>Visit {item.visitId} · {item.currentVisitStatus}</h2><p>My check-in: {timestamp(item.ownCheckInAt)}</p><p>My check-out: {timestamp(item.ownCheckOutAt)}</p><p>{item.commandCount} recorded actions · Latest: {timestamp(item.lastOwnActionAt)}</p><Link to={`/caregiver/history/${item.visitId}`}>View my facts for Visit {item.visitId}</Link></article>)}
      <Pager page={page} total={result.data.total} change={setPage} />
    </>}
  </div>
}
export function HistoryDetailPage() {
  const { visitId = '' } = useParams()
  const [page, setPage] = useState(0)
  const load = useCallback((signal: AbortSignal) => api<History>(`/caregivers/me/history/${encodeURIComponent(visitId)}?page=${page}&size=20`, { signal }), [visitId, page])
  const { result, reload } = useCaregiverQuery(`history-detail:${visitId}:${page}`, load, true)
  return <div className={styles.page}><Link className={styles.back} to="/caregiver/history">← My execution history</Link><div className={styles.heading}><h1>My recorded facts · Visit {visitId}</h1></div><div className={styles.toolbar}><button className={styles.refresh} onClick={reload}>Refresh</button></div>
    <p>This history does not grant access to the current work pack. Only your own submissions and your concluded spot checks are shown; missing older provenance is not reconstructed.</p>
    {result.status === 'loading' && <p role="status">Loading recorded facts…</p>}
    {result.status === 'error' && <SelfServiceError error={result.error} retry={reload} subject="your recorded facts" />}
    {result.status === 'success' && <>
      {result.data.facts.map(fact => <article className={styles.card} key={`${fact.action}:${fact.visitVersion}:${fact.sourceId}`}><h2>{fact.action.replaceAll('_', ' ')}</h2><p>{timestamp(fact.recordedAt)} · Visit version {fact.visitVersion} · Source {fact.sourceId}</p>
        {fact.task && <p>Task {fact.task.taskId}: {fact.task.status} · {fact.task.outcome} {fact.task.caregiverNote}</p>}
        {fact.health && <><p>{fact.health.healthFlag} · {fact.health.healthNote}</p><ul>{fact.health.readings.map(reading => <li key={reading.metric}>{reading.metric}: {reading.value} {reading.unit}</li>)}</ul></>}
        {fact.evidence && <><p>{fact.evidence.kind} · {fact.evidence.verificationStatus} · {fact.evidence.fileName}</p><a href={fact.evidence.downloadPath} download>Download my evidence {fact.evidence.evidenceId}</a></>}
        {fact.report && <p>My report {fact.report.id}: {fact.report.category} · {fact.report.severity} · {fact.report.status}<br />{fact.report.description}</p>}
      </article>)}
      <Pager page={page} total={result.data.total} change={setPage} />
      <h2>My concluded spot checks for this Visit</h2>{result.data.spotChecks.length === 0 ? <p>No concluded spot checks linked to this Visit.</p> : result.data.spotChecks.map(check => <article className={styles.card} key={check.id}><p>Check {check.id}: {check.result} · {timestamp(check.checkedAt)}</p><p>My response: {check.ownResponse || 'Not responded'}</p><Link to={`/caregiver/spot-checks?spotCheckId=${check.id}`}>View my spot check</Link></article>)}
    </>}
  </div>
}
function Pager({ page, total, change }: { page: number; total: number; change: (page: number) => void }) {
  return <div className={styles.pager}><button disabled={page === 0} onClick={() => change(page - 1)}>Previous</button><span> Page {page + 1} </span><button disabled={(page + 1) * 20 >= total} onClick={() => change(page + 1)}>Next</button></div>
}
