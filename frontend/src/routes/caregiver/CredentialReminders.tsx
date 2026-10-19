import type { CredentialAlert, CredentialAlertContext } from '../../features/caregiver/api'
import { Link } from 'react-router-dom'
import { dateLabel } from './format'
import styles from './Caregiver.module.css'

function deadline(alert: CredentialAlert) {
  const days = alert.daysUntilExpiry
  if (days === undefined) return ''
  if (days === 0) return 'Valid through today (SGT).'
  const count = Math.abs(days)
  const unit = count === 1 ? 'day' : 'days'
  return days < 0 ? `Expired ${count} ${unit} ago.` : `Expires in ${count} ${unit}.`
}

function renewalNote(alert: CredentialAlert) {
  switch (alert.renewalState) {
    case 'PENDING_REVIEW': return 'Renewal pending review. This reminder remains until an approved replacement takes effect.'
    case 'REJECTED': return 'Renewal not approved. Contact your manager about the next steps.'
    case 'APPROVED_NOT_EFFECTIVE': return alert.renewalValidFrom
      ? `Renewal approved; effective ${dateLabel(alert.renewalValidFrom)}. The current reminder remains until then.`
      : 'Renewal approved but not yet effective. Contact your manager to confirm the start date.'
    case 'REVOKED': return 'Replacement revoked. Contact your manager; this does not make an old certificate valid again.'
    case 'CHECK_REQUIRED': return 'Credential records need review. Contact your manager; no renewal has been assumed complete.'
    default: return 'Contact your manager about renewal.'
  }
}

/** Server-calculated dates remain independent of the roster filter and the browser timezone. */
export default function CredentialReminders({ alerts, context }: { alerts: CredentialAlert[]; context?: CredentialAlertContext }) {
  return <section aria-labelledby="credential-reminders-title">
    <h2 id="credential-reminders-title" className={styles.sectionTitle}>Credential reminders</h2>
    <p className={styles.muted}>{context
      ? `Assessed ${dateLabel(context.asOfDate)} (SGT) · ${context.warningDays}-day warning window.`
      : 'Reminders are assessed using the current Singapore date.'} Changing schedule dates does not change this assessment.</p>
    {context?.reviewRequired && <p role="alert" className={styles.alert}>Some credential records need your manager’s review. Reminders have not been automatically cleared for those records.</p>}
    {alerts.length === 0 && <p className={styles.muted}>No credential expiry reminders.</p>}
    {alerts.map(alert => <article key={alert.id} className={styles.alert + (alert.warning === 'EXPIRED' ? ' ' + styles.expired : '')}>
      <strong>{alert.name} · {alert.warning === 'EXPIRED' ? 'Expired' : alert.daysUntilExpiry === 0 ? 'Expires today' : 'Expiring soon'}</strong>
      <p>{deadline(alert)} Expiry date: {dateLabel(alert.expiryDate)}</p>
      {alert.certificateNo && <p>Certificate: {alert.certificateNo}</p>}
      <p>{renewalNote(alert)}</p>
      <Link to={'/caregiver/credentials/' + alert.id}>View certificate and renewal options</Link>
    </article>)}
    <p className={styles.muted}>These are expiry reminders, not confirmation that all credentials are approved or that you meet rostering requirements.</p>
  </section>
}
