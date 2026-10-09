import { useEffect } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useParams } from 'react-router-dom'
import { ApiError } from '../../../shared/api/client'
import { useFamilyIncident } from '../../../features/incidents/useFamilyIncident'
import type { FamilyIncidentDetail } from '../../../features/incidents/familyTypes'
import type { IncidentStatus } from '../../../features/incidents/types'
import { familyIncidentNotificationId } from '../../../features/notifications/presentation'
import styles from './FamilyIncident.module.css'

const statusLabels: Record<IncidentStatus, string> = {
  OPEN: 'Reported', ACKNOWLEDGED: 'Awaiting care response', IN_PROGRESS: 'Being handled',
  RESOLVED: 'Resolved', UNRESOLVED_ESCALATED: 'Escalated — awaiting response',
}
const categoryLabels = { SOS: 'Emergency call', MEDICAL: 'Health concern', FALL: 'Fall', SERVICE: 'Service concern', OTHER: 'Other concern' }
const sourceLabels = { CAREGIVER: 'Caregiver report', ELDER_SOS: 'Elder emergency call', ELDER_SERVICE_DISPUTE: 'Elder service concern', SYSTEM_MISSED_CHECKIN: 'Service monitoring' }
const severityLabels = { LOW: 'Low severity', MEDIUM: 'Medium severity', HIGH: 'High severity' }
const timeFormat = new Intl.DateTimeFormat('en-SG', { timeZone: 'Asia/Singapore', day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })

/** Family incident reading and an explicit awareness action; no manager handling controls.
 * @author Wang Zhili
 */
export function FamilyIncidentPage() {
  const { id = '' } = useParams()
  const notificationId = familyIncidentNotificationId(useLocation().state, id)
  const incident = useFamilyIncident(id, notificationId)
  const { resource } = incident
  return <div className={styles.page}>
    <div className={styles.navigation}>
      <Link to="/family/home">Back to home</Link>
      {resource.status === 'success' && <button onClick={incident.refresh} disabled={!!incident.pause || incident.view === 'saving' || incident.notification === 'saving' || incident.acknowledgement === 'saving'}>Refresh</button>}
    </div>
    {incident.pause && <p className={styles.loading} role="status">{incident.pause === 'offline' ? 'Updates paused while offline.' : 'Updates paused while this tab is hidden.'} Details will be checked again when this page is visible and online.</p>}
    {!incident.pause && resource.status === 'loading' && <p className={styles.loading} role="status">Loading incident details…</p>}
    {resource.status === 'error' && <IncidentFeedback error={resource.error} retry={incident.refresh} disabled={!!incident.pause} />}
    {resource.status === 'success' && <IncidentContent key={id} detail={resource.data} incident={incident} />}
  </div>
}

function IncidentContent({ detail, incident }: { detail: FamilyIncidentDetail; incident: ReturnType<typeof useFamilyIncident> }) {
  const { recordView, recordNotificationRead, note, setNote } = incident
  // This effect runs only after the successful detail has been committed to the screen.
  useEffect(() => { recordView(); recordNotificationRead() }, [recordView, recordNotificationRead])
  const receipt = detail.acknowledgement
  const confirming = incident.acknowledgement === 'saving'
  function confirm(event: FormEvent) {
    event.preventDefault()
    if (note.length <= 255) incident.acknowledge(note)
  }
  return <article aria-labelledby="incident-heading">
    <header className={styles.hero}>
      <p className={styles.eyebrow}>Your family's care</p>
      <h1 id="incident-heading">Incident #{detail.id}</h1>
      <p>Elder profile #{detail.elderId}{detail.visitId !== null && ` · Visit #${detail.visitId}`}</p>
      <div className={styles.tags}>
        <span className={styles.badge} data-severity={detail.severity}>{severityLabels[detail.severity]}</span>
        <span className={styles.badge}>{statusLabels[detail.status]}</span>
      </div>
    </header>
    <section className={styles.card} aria-labelledby="details-heading">
      <h2 id="details-heading">What happened</h2>
      <p className={styles.description}>{detail.description ?? 'No description was recorded.'}</p>
      <dl className={styles.facts}>
        <div><dt>Category</dt><dd>{categoryLabels[detail.category]}</dd></div>
        <div><dt>Reported through</dt><dd>{sourceLabels[detail.source]}</dd></div>
        <div><dt>Reported</dt><dd><IncidentTime value={detail.reportedAt} /></dd></div>
        {detail.resolvedAt && <div><dt>Resolved</dt><dd><IncidentTime value={detail.resolvedAt} /></dd></div>}
      </dl>
    </section>
    <section className={styles.card} aria-labelledby="awareness-heading">
      <h2 id="awareness-heading">Your awareness</h2>
      {detail.acknowledgeBy ? <p className={styles.deadline}>Your response time: <IncidentTime value={detail.acknowledgeBy} />. You can still confirm awareness after this time.</p>
        : <p className={styles.muted}>No personal response time is recorded. You can still confirm awareness.</p>}
      {receipt.viewedAt && <p className={styles.timestamp}>First viewed <IncidentTime value={receipt.viewedAt} /></p>}
      {incident.notification === 'saving' && <p className={styles.muted} role="status">Marking notification as read…</p>}
      {incident.notification === 'saved' && <p className={styles.timestamp}>Notification marked as read.</p>}
      {incident.notification === 'error' && <div className={styles.error} role="alert">
        <p>The notification could not be marked as read. You can still confirm awareness.</p>
        <button onClick={incident.recordNotificationRead}>Retry notification read</button>
      </div>}
      {incident.view === 'saving' && <p className={styles.muted} role="status">Saving your viewing receipt…</p>}
      {incident.view === 'error' && <div className={styles.error} role="alert">
        <p>Your viewing receipt could not be saved. You can still confirm awareness.</p>
        <button onClick={incident.recordView}>Retry viewing receipt</button>
      </div>}
      {receipt.acknowledgedAt ? <div className={styles.confirmed} role="status">
        <strong>Awareness confirmed</strong>
        <p>First confirmed <IncidentTime value={receipt.acknowledgedAt} /></p>
        {receipt.responseNote !== null && <p className={styles.description}>{receipt.responseNote}</p>}
      </div> : <form onSubmit={confirm} className={styles.form}>
        <label htmlFor="awareness-note">Optional note</label>
        <textarea id="awareness-note" rows={3} maxLength={255} value={note} disabled={confirming} onChange={(event) => setNote(event.target.value)} aria-describedby="note-limit" />
        <p id="note-limit" className={styles.muted}>{note.length}/255 characters</p>
        {incident.acknowledgement === 'error' && <p className={styles.error} role="alert">We could not confirm that your acknowledgement was saved. Try again; repeated confirmation keeps the first saved time.</p>}
        <button className={styles.primary} type="submit" disabled={confirming || note.length > 255}>{confirming ? 'Confirming…' : 'I am aware'}</button>
      </form>}
      <p className={styles.muted}>Confirming awareness records that you know about this incident. It does not mean the incident is resolved or replace the care team's response.</p>
    </section>
    <p className={styles.timestamp}>Times are shown in Singapore time.</p>
  </article>
}

function IncidentTime({ value }: { value: string }) {
  return <time dateTime={value}>{timeFormat.format(new Date(value))}</time>
}

function IncidentFeedback({ error, retry, disabled }: { error: unknown; retry: () => void; disabled: boolean }) {
  const status = error instanceof ApiError ? error.status : undefined
  if (status === 401) return <Navigate to="/" replace />
  const headings: Record<number, string> = { 400: 'Invalid incident link', 403: 'Incident access unavailable', 404: 'Incident not found' }
  return <section className={styles.card} role="alert">
    <h2>{headings[status ?? 0] ?? 'Unable to load this incident'}</h2>
    <p>{status === 403 ? 'This incident is no longer available to your family account.'
      : status === 404 ? 'This incident could not be found.' : status === 400 ? 'Check the incident link.' : 'Check your connection and try again.'}</p>
    {status !== 400 && status !== 404 && <button onClick={retry} disabled={disabled}>Try again</button>}
    {status === 403 && <Link to="/">Sign in with another account</Link>}
  </section>
}
