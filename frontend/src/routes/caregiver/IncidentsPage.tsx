import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getReport, listReports, type IncidentReport } from '../../features/caregiver-incidents/api'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import { LastFetched } from './components'
import SelfServiceError from './SelfServiceError'
import { titleCase, visitTime } from './format'
import styles from './Caregiver.module.css'

export default function IncidentsPage() {
  const [page, setPage] = useState(0)
  const load = useCallback((signal: AbortSignal) => listReports(page, signal), [page])
  const { result, reload } = useCaregiverQuery('reports-' + page, load, true)
  return <div className={styles.page}>
    <div className={styles.heading}><div><h1>My reports</h1><p>Reports you submitted. A report does not mean a responder has taken over.</p></div><button className={styles.button} onClick={reload}>Refresh</button></div>
    {result.status === 'loading' && <p role="status">Loading your reports…</p>}
    {result.status === 'error' && <SelfServiceError error={result.error} retry={reload} subject="your reports" />}
    {result.status === 'success' && <><LastFetched at={result.receivedAt} subject="report" />
      {!result.data.items.length && <p className={styles.empty}>No reports on this page.</p>}
      {result.data.items.map(({ report }) => <section className={styles.card} key={report.id}><ReportSummary report={report} /><Link className={styles.linkButton} to={'/caregiver/incidents/' + report.id}>Open report #{report.id}</Link></section>)}
      <button className={styles.button} disabled={!page} onClick={() => setPage(p => p - 1)}>Previous</button>{' '}
      <button className={styles.button} disabled={(page + 1) * result.data.size >= result.data.totalElements} onClick={() => setPage(p => p + 1)}>Next</button>
    </>}
  </div>
}
function ReportSummary({ report }: { report: IncidentReport }) {
  return <><h2>Report #{report.id} · Visit #{report.visitId}</h2><p>{titleCase(report.category)} · {titleCase(report.severity)} · {titleCase(report.status)}</p>
    <p>Reported: {visitTime(report.reportedAt)} (SGT)</p><p>Response deadline: {report.respondBy ? visitTime(report.respondBy) + ' (SGT)' : 'Not available'}</p></>
}
export function IncidentDetailPage() {
  const { incidentId = '' } = useParams()
  const valid = /^[1-9]\d*$/.test(incidentId)
  return <div className={styles.page}><Link className={styles.back} to="/caregiver/incidents">← My reports</Link>
    {valid ? <Detail key={incidentId} id={incidentId} /> : <p role="alert">Report unavailable.</p>}</div>
}
function Detail({ id }: { id: string }) {
  const load = useCallback((signal: AbortSignal) => getReport(id, signal), [id])
  const { result, reload } = useCaregiverQuery('report-' + id, load, true)
  if (result.status === 'loading') return <p role="status">Loading report…</p>
  if (result.status === 'error') return <><p role="alert">Report unavailable or could not be loaded. Only reports you submitted can be opened here.</p><SelfServiceError error={result.error} retry={reload} subject="this report" /></>
  return <section className={styles.card}><h1>Submitted report</h1><ReportSummary report={result.data.report} />
    <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{result.data.report.description}</p>
    {result.data.report.resolvedAt && <p>Resolved: {visitTime(result.data.report.resolvedAt)} (SGT)</p>}
    <p className={styles.readOnly}>Resolution does not automatically resume or complete the visit. Contact your manager for further arrangements.</p>
    <LastFetched at={result.receivedAt} subject="report" /><button className={styles.button} onClick={reload}>Refresh</button>
  </section>
}
