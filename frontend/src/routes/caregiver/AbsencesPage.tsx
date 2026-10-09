import { useState } from 'react'
import type { FormEvent } from 'react'
import { listOwnAbsences, requestAbsence } from '../../features/absences/api'
import type { AbsenceStatus, AbsenceType } from '../../features/absences/types'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import { LastFetched } from './components'
import { dateLabel, titleCase, todayInSingapore } from './format'
import { isAccessFailure, serviceError, useSelfServiceWrite } from './selfService'
import SelfServiceError from './SelfServiceError'
import styles from './Caregiver.module.css'
import EnglishDateField from './EnglishDateField'
import { isCalendarDate } from './calendarDate'

export default function AbsencesPage() {
  const { result, reload } = useCaregiverQuery('own-absences', listOwnAbsences, true)
  const mutation = useSelfServiceWrite()
  const [type, setType] = useState<AbsenceType>('ANNUAL')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [reason, setReason] = useState('')
  const [filter, setFilter] = useState<AbsenceStatus | 'ALL'>('ALL')
  const [validation, setValidation] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const accessError = isAccessFailure(mutation.error) ? mutation.error : result.status === 'error' && isAccessFailure(result.error) ? result.error : null
  if (accessError && (startDate || endDate || reason || submitted)) {
    setStartDate(''); setEndDate(''); setReason(''); setSubmitted(false)
  }
  if (accessError) return <SelfServiceError error={accessError} retry={reload} subject="your leave" />

  function submit(event: FormEvent) {
    event.preventDefault()
    setValidation('')
    if (!isCalendarDate(startDate) || !isCalendarDate(endDate) || endDate < startDate) { setValidation('Choose valid dates in YYYY-MM-DD format, with the end on or after the start.'); return }
    if (endDate < todayInSingapore()) { setValidation('Choose leave that ends today or later (Singapore time).'); return }
    if (reason.length > 255) { setValidation('Use at most 255 characters for the reason.'); return }
    setSubmitted(false)
    void mutation.send(signal => requestAbsence({ type, startDate, endDate, reason: reason.trim() || undefined }, signal), () => {
      setSubmitted(true); setStartDate(''); setEndDate(''); setReason(''); setFilter('ALL'); reload()
    })
  }

  return <div className={styles.page}>
    <div className={styles.heading}><div><p className={styles.eyebrow}>Caregiver workspace</p><h1>My leave</h1><p className={styles.muted}>Request whole-day leave and check your manager's decision.</p></div><button className={styles.button} onClick={reload}>Refresh</button></div>
    <p className={styles.readOnly}>Both dates are included, in Singapore time. Submission requests approval. Your manager approves or rejects it, then arranges any roster changes separately.</p>
    <form className={styles.formPanel} onSubmit={submit} aria-label="Request leave" noValidate>
      <h2>Request leave</h2>
      <fieldset disabled={mutation.pending}>
        <label>Leave type<select value={type} onChange={e => setType(e.target.value as AbsenceType)}>{(['SICK', 'ANNUAL', 'EMERGENCY', 'OTHER'] as const).map(t => <option key={t} value={t}>{titleCase(t)}</option>)}</select></label>
        <div className={styles.dateForm}><EnglishDateField label="Start date" value={startDate} onChange={setStartDate} /><EnglishDateField label="End date" value={endDate} onChange={setEndDate} /></div>
        <label>Reason (optional)<textarea maxLength={255} value={reason} onChange={e => setReason(e.target.value)} rows={3} /></label>
        <p className={styles.muted}>{reason.length}/255 characters</p>
        <button className={styles.button} type="submit">{mutation.pending ? 'Submitting…' : 'Submit leave request'}</button>
      </fieldset>
      {validation && <p role="alert">{validation}</p>}
      {mutation.error !== null && <p role="alert">{serviceError(mutation.error)}</p>}
    </form>
    {submitted && <p className={styles.readOnly} role="status">Leave request submitted, awaiting manager approval.{result.status === 'error' ? ' The list could not be refreshed; use Refresh to check it. Do not submit the same request again.' : ''}</p>}
    <h2 className={styles.sectionTitle}>My requests</h2>
    <label className={styles.filterLabel}>Request status<select value={filter} onChange={e => setFilter(e.target.value as typeof filter)}>{['ALL', 'PENDING', 'APPROVED', 'REJECTED'].map(s => <option key={s} value={s}>{s === 'ALL' ? 'All requests' : titleCase(s)}</option>)}</select></label>
    {result.status === 'loading' && <p role="status">Loading your leave…</p>}
    {result.status === 'error' && <SelfServiceError error={result.error} retry={reload} subject="your leave" />}
    {result.status === 'success' && <>
      <LastFetched at={result.receivedAt} subject="leave record" />
      {result.data.length === 0 ? <p className={styles.empty}>No leave requests yet.</p> : result.data.filter(a => filter === 'ALL' || a.status === filter).length === 0 ? <p className={styles.empty}>No requests with this status.</p> : result.data.filter(a => filter === 'ALL' || a.status === filter).map(a => <article className={styles.card} key={a.id} aria-label={`Leave request ${a.id}`}>
        <div className={styles.cardTop}><h3>{titleCase(a.type)} leave</h3><span className={styles.badge}>{titleCase(a.status)}</span></div>
        <p>{dateLabel(a.startDate)} – {dateLabel(a.endDate)} (both days included)</p><p>{a.reason || 'No reason provided.'}</p>
      </article>)}
    </>}
  </div>
}
