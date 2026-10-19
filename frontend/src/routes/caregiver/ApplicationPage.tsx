import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { editApplication, getAdmission, resubmitApplication, updateContact } from '../../features/admission/api'
import type { Admission } from '../../features/admission/api'
import type { CaregiverProfile } from '../../features/caregiver/api'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import { ApiError } from '../../shared/api/client'
import { phoneError, normalizeSgPhone } from '../../shared/validation/sg'
import { useSelfServiceWrite } from './selfService'
import ApplicationFields from './ApplicationFields'
import { normalizedDetails, validateApplication } from '../../features/admission/validation'
import SelfServiceError from './SelfServiceError'
import styles from './Caregiver.module.css'
import { useUnsavedCaregiverForm } from './useUnsavedCaregiverForm'

export default function ApplicationPage({ profile, refreshProfile }: { profile: CaregiverProfile; refreshProfile: () => void }) {
  const [editing, setEditing] = useState<Admission | null>(null)
  const [contactEditing, setContactEditing] = useState<Admission | null>(null)
  const { result, reload, retainedData } = useCaregiverQuery('own-admission', getAdmission, !editing && !contactEditing)
  const mutation = useSelfServiceWrite()
  const [resubmission, setResubmission] = useState<{ version: number; key: string } | null>(null)
  const [notice, setNotice] = useState('')
  if (result.status === 'loading' && !retainedData) return <p role="status">Loading application…</p>
  if (result.status === 'error') return <SelfServiceError error={result.error} retry={reload} subject="your application" />
  const data = result.status === 'success' ? result.data : retainedData!
  const application = data.application
  const message = mutation.error instanceof ApiError ? mutation.error.message : 'The result could not be confirmed. Refresh before retrying; keep the same request key.'
  return <div className={styles.page}>
    <div className={styles.heading}><h1>My application</h1><button className={styles.button} disabled={!!editing || !!contactEditing || mutation.pending || result.status === 'loading'} onClick={() => { reload(); refreshProfile(); setResubmission(null) }}>Refresh</button></div>
    <p className={styles.readOnly}>Admission and certificate review are independent. No certificate is required for your manager to approve admission. Individual services may still require particular qualifications.</p>
    <p>Account: {data.username} · Work status: {data.workStatus}</p>
    <p role="status">{application ? `Application ${application.id} · ${application.status} · Submission ${application.roundNumber}` : data.compatibility === 'LEGACY_ACTIVE' ? 'Existing active caregiver — no application history recorded.' : 'Existing onboarding profile — contact your manager.'}</p>
    {!data.canWork && <p>Work access is restricted. You may check your application, upload optional certificates and prepare work preferences.</p>}
    {application && <article className={styles.card}><h2>{application.details.fullName}</h2><p>Submitted: {application.submittedAt.replace('T', ' ')} (SGT)</p>
      <p>Mobile: {application.details.phone} · Preferred area: {application.details.requestedSector || 'Not specified'}</p>
      {application.reviewReason && <p>Manager's reason: {application.reviewReason}</p>}
      {application.reviewedAt && <p>Reviewed: {application.reviewedAt.replace('T', ' ')} (SGT)</p>}
    </article>}
    <Link to="/caregiver/credentials">Optional certificates</Link>
    {notice && <p role="status">{notice}</p>}
    {data.canEdit && !editing && <button className={styles.button} onClick={() => { setEditing(data); setNotice('') }}>Edit application</button>}
    {editing && data.canEdit && <EditForm source={editing} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); setNotice('Application details saved.'); reload(); refreshProfile() }} />}
    {data.canResubmit && !editing && <button className={styles.button} disabled={mutation.pending} onClick={() => {
      const attempt = resubmission ?? { version: data.version, key: crypto.randomUUID() }; setResubmission(attempt)
      void mutation.send(signal => resubmitApplication(attempt.version, attempt.key, signal), () => { setResubmission(null); setNotice('Application resubmitted — awaiting admission approval.'); reload() })
    }}>Resubmit application</button>}
    {mutation.error !== null && <p role="alert">{message}</p>}
    {data.canWork && !contactEditing && <button className={styles.button} onClick={() => setContactEditing(data)}>Edit contact details</button>}
    {data.canWork && contactEditing && <ContactForm profile={{ ...profile, phone: contactEditing.phone }} admission={contactEditing} onCancel={() => setContactEditing(null)} onSaved={() => { setContactEditing(null); reload(); refreshProfile(); setNotice('Contact details saved.') }} />}
    {data.history.length > 0 && <details><summary>Application history</summary><ol>{data.history.map(entry => <li key={entry.version + ':' + entry.action}>{entry.occurredAt.replace('T', ' ')} (SGT) · {entry.action} · {entry.snapshot.status}{entry.snapshot.reviewReason && ` · ${entry.snapshot.reviewReason}`}</li>)}</ol></details>}
  </div>
}
function EditForm({ source, onCancel, onSaved }: { source: Admission; onCancel: () => void; onSaved: () => void }) {
  const [values, setValues] = useState(source.application!.details)
  const [key, setKey] = useState(() => crypto.randomUUID())
  const [validation, setValidation] = useState('')
  const mutation = useSelfServiceWrite()
  const uncertain = mutation.error !== null && (!(mutation.error instanceof ApiError) || mutation.error.status >= 500)
  useUnsavedCaregiverForm(JSON.stringify(values) !== JSON.stringify(source.application!.details))
  function submit(event: FormEvent) {
    event.preventDefault(); const invalid = validateApplication(values); setValidation(invalid); if (invalid) return
    void mutation.send(signal => editApplication(normalizedDetails(values), source.version, key, signal), onSaved)
  }
  return <form className={styles.formPanel} aria-label="Edit application" onSubmit={submit} noValidate>
    <fieldset disabled={mutation.pending || uncertain}><ApplicationFields values={values} onChange={next => { setValues(next); setKey(crypto.randomUUID()) }} /></fieldset>
    {validation && <p role="alert">{validation}</p>}
    {mutation.error !== null && <p role="alert">{mutation.error instanceof ApiError ? mutation.error.message : 'Unknown result. Refresh to check; retry only the same unchanged request.'}</p>}
    <button className={styles.button} disabled={mutation.pending} type="submit">{uncertain ? 'Retry unchanged request' : 'Save application'}</button>
    <button className={styles.button} type="button" disabled={mutation.pending} onClick={() => {
      if (JSON.stringify(values) === JSON.stringify(source.application!.details) || window.confirm('Discard these unsaved changes? If a save result is unknown, check the application before making another change.')) onCancel()
    }}>Close editor</button>
  </form>
}
function ContactForm({ profile, admission, onSaved, onCancel }: { profile: CaregiverProfile; admission: Admission; onSaved: () => void; onCancel: () => void }) {
  const [phone, setPhone] = useState(profile.phone ?? '')
  const [key, setKey] = useState(() => crypto.randomUUID())
  const [invalid, setInvalid] = useState('')
  const mutation = useSelfServiceWrite()
  const uncertain = mutation.error !== null && (!(mutation.error instanceof ApiError) || mutation.error.status >= 500)
  useUnsavedCaregiverForm(phone !== (profile.phone ?? ''))
  function save(event: FormEvent) {
    event.preventDefault(); const problem = phone.trim() ? phoneError(phone, { mobile: true }) : 'Enter your mobile number.'; setInvalid(problem ?? ''); if (problem) return
    void mutation.send(signal => updateContact(normalizeSgPhone(phone)!, admission.version, key, signal), onSaved)
  }
  return <form className={styles.formPanel} aria-label="Contact details" onSubmit={save} noValidate><h2>Contact details</h2>
    <label>Current mobile number<input type="tel" value={phone} disabled={mutation.pending || uncertain} onChange={e => { setPhone(e.target.value); setKey(crypto.randomUUID()) }} /></label>
    {invalid && <p role="alert">{invalid}</p>}{mutation.error !== null && <p role="alert">{mutation.error instanceof ApiError ? mutation.error.message : 'Unknown result. Refresh before retrying.'}</p>}
    <button className={styles.button} disabled={mutation.pending}>{uncertain ? 'Retry unchanged request' : 'Save contact details'}</button>
    <button className={styles.button} disabled={mutation.pending} type="button" onClick={() => { if (phone === (profile.phone ?? '') || window.confirm('Discard this contact draft? Check saved contact details before starting a new request if the result is unknown.')) onCancel() }}>Close editor</button>
  </form>
}
