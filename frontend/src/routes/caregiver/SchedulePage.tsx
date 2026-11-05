import { useCallback, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { getMySchedule } from '../../features/caregiver/api'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import { LastFetched, QueryError } from './components'
import { useScheduleChanges } from '../../features/caregiver/useScheduleChanges'
import { addDays, dateLabel, titleCase, todayInSingapore, visitTime } from './format'
import styles from './Caregiver.module.css'
import CredentialReminders from './CredentialReminders'

export default function SchedulePage() {
  const [params, setParams] = useSearchParams()
  const today = todayInSingapore()
  const from = params.get('dateFrom') ?? today
  const to = params.get('dateTo') ?? addDays(today, 6)
  const query = new URLSearchParams({ dateFrom: from, dateTo: to }).toString()
  const load = useCallback((signal: AbortSignal) => getMySchedule(query, signal), [query])
  const { result, reload } = useCaregiverQuery(query, load, true)
  const changes = useScheduleChanges(query, result)
  function choose(dateFrom: string, dateTo: string) { setParams({ dateFrom, dateTo }) }
  return <div className={styles.page}>
    <div className={styles.heading}><div><p className={styles.eyebrow}>Caregiver workspace</p><h1>My schedule</h1><p className={styles.muted}>Your assigned visits, one day at a time.</p></div></div><div className={styles.toolbar}><button className={styles.refresh} onClick={reload}>Refresh</button></div>
    <div className={styles.filters}><div className={styles.quick}>
      <button className={styles.button} onClick={() => choose(today, today)}>Today</button>
      <button className={styles.button} onClick={() => choose(today, addDays(today, 6))}>Next 7 days</button>
    </div><DateFilter key={query} from={from} to={to} onApply={choose} /><p className={styles.muted}>All times are Singapore time (SGT).</p></div>
    {result.status === 'loading' && <p role="status">Loading your schedule…</p>}
    {result.status === 'error' && <QueryError error={result.error} retry={reload} back={'/caregiver?' + query} />}
    {result.status === 'success' && <>
      <LastFetched at={result.receivedAt} />
      {changes.length > 0 && <div role="status" className={styles.readOnly}><strong>Schedule updated</strong><ul>{changes.map(message => <li key={message}>{message}</li>)}</ul></div>}
      <div className={styles.cardTop}><h2 className={styles.sectionTitle}>Assigned visits · {result.data.upcomingVisits.length}</h2><span className={styles.muted}>{dateLabel(result.data.dateFrom)} – {dateLabel(result.data.dateTo)}</span></div>
      {result.data.upcomingVisits.length === 0 && <div className={styles.empty}><strong>No assigned visits in this period</strong><p>Choose another date range to view your schedule.</p></div>}
      {result.data.upcomingVisits.map(visit => <article className={styles.card} key={visit.id}>
        <div className={styles.cardTop}><span className={styles.time}>{visitTime(visit.scheduledStart)}{visit.scheduledEnd ? ' – ' + visitTime(visit.scheduledEnd) : ''}</span><span className={styles.badge}>{titleCase(visit.status)}</span></div>
        <h2>{visit.elderName}</h2><p className={styles.muted}>{titleCase(visit.serviceType)} · Visit #{visit.id}</p>
        {visit.status === 'CANCELLED' ? <p className={styles.muted}>Cancelled — summary only. Work pack unavailable.</p>
          : <Link className={styles.linkButton} to={'/caregiver/visits/' + visit.id + '?' + query}>View work pack →</Link>}
      </article>)}
      <CredentialReminders alerts={result.data.certificationAlerts} context={result.data.credentialAlertContext} />
    </>}
  </div>
}
function DateFilter({ from, to, onApply }: { from: string; to: string; onApply: (from: string, to: string) => void }) {
  const [start, setStart] = useState(from); const [end, setEnd] = useState(to); const [error, setError] = useState('')
  function submit(event: FormEvent) {
    event.preventDefault()
    if (!start || !end || end < start || (Date.parse(end) - Date.parse(start)) / 86400000 > 30) { setError('Choose both dates in order, up to 31 days.'); return }
    setError(''); onApply(start, end)
  }
  return <form onSubmit={submit}><div className={styles.dateForm}>
    <label>From<input type="date" value={start} onChange={e => setStart(e.target.value)} required /></label>
    <label>To<input type="date" value={end} onChange={e => setEnd(e.target.value)} required /></label>
    <button className={styles.button} type="submit">Apply dates</button>
  </div>{error && <p role="alert">{error}</p>}</form>
}
