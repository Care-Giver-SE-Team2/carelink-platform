import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../../shared/api/client'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import SelfServiceError from './SelfServiceError'
import styles from './Caregiver.module.css'

type AlertSummary = { incidentId: number; visitId: number; source: string; scheduledStart: string; checkInDueAt: string; triggeredAt: string; timeZone: string; workPackAvailable: boolean }
type AlertPage = { items: AlertSummary[]; page: number; size: number; total: number }
const time = (value: string) => value.replace('T', ' ') + ' (SGT)'
export default function SystemAlertsPage() {
  const [page, setPage] = useState(0)
  const load = useCallback((signal: AbortSignal) => api<AlertPage>(`/caregivers/me/system-alerts?page=${page}&size=20`, { signal }), [page])
  const { result, reload } = useCaregiverQuery(`system-alerts:${page}`, load, true)
  return <div className={styles.page}><div className={styles.heading}><div><p className={styles.eyebrow}>Caregiver workspace</p><h1>System alerts</h1><p className={styles.muted}>Missed check-in alerts for visits assigned to you.</p></div></div><div className={styles.toolbar}><button className={styles.refresh} onClick={reload}>Refresh</button></div>
    <p>Automatic missed check-in facts for visits assigned to you when the alert was triggered. These are separate from reports you submitted.</p>
    {result.status === 'loading' && <p role="status">Loading system alerts…</p>}
    {result.status === 'error' && <SelfServiceError error={result.error} retry={reload} subject="your system alerts" />}
    {result.status === 'success' && <>
      {result.data.items.length === 0 && <p>No system alerts in this page.</p>}
      {result.data.items.map(item => <article key={item.incidentId} className={styles.card}><h2>Missed check-in · Visit {item.visitId}</h2><p>Triggered: {time(item.triggeredAt)}</p><Link to={`/caregiver/system-alerts/${item.incidentId}`}>Open alert {item.incidentId}</Link></article>)}
      <div className={styles.pager}><button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button><span> Page {page + 1} </span><button disabled={(page + 1) * 20 >= result.data.total} onClick={() => setPage(page + 1)}>Next</button></div>
    </>}
  </div>
}
export function SystemAlertDetailPage() {
  const { incidentId = '' } = useParams()
  const load = useCallback((signal: AbortSignal) => api<AlertSummary>(`/caregivers/me/system-alerts/${encodeURIComponent(incidentId)}`, { signal }), [incidentId])
  const { result, reload } = useCaregiverQuery(`system-alert:${incidentId}`, load, true)
  return <div className={styles.page}><Link className={styles.back} to="/caregiver/system-alerts">← System alerts</Link><div className={styles.heading}><h1>Missed check-in alert</h1></div><div className={styles.toolbar}><button className={styles.refresh} onClick={reload}>Refresh</button></div>
    {result.status === 'loading' && <p role="status">Loading alert…</p>}
    {result.status === 'error' && <SelfServiceError error={result.error} retry={reload} subject="this system alert" />}
    {result.status === 'success' && <article className={styles.card}>
      <h2>Visit {result.data.visitId} · Alert {result.data.incidentId}</h2>
      <p>Source: automatic missed check-in detection, not a report submitted by you.</p>
      <p>Scheduled start at trigger: {time(result.data.scheduledStart)}</p><p>Check-in threshold at trigger: {time(result.data.checkInDueAt)}</p><p>Detected: {time(result.data.triggeredAt)}</p>
      <p>This alert does not block late check-in within the allowed window. Check-in does not automatically close the incident.</p>
      {result.data.workPackAvailable ? <Link to={`/caregiver/visits/${result.data.visitId}`}>Open current work pack</Link> : <p>Only your original alert summary is available. The current work pack is not available through this alert; it may have been cancelled or reassigned.</p>}
    </article>}
  </div>
}
