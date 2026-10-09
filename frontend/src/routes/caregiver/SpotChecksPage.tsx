import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { listSpotChecks, respondToSpotCheck } from '../../features/spot-checks/api'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import { LastFetched } from './components'
import { visitTime } from './format'
import { isAccessFailure, serviceError, useSelfServiceWrite } from './selfService'
import SelfServiceError from './SelfServiceError'
import styles from './Caregiver.module.css'

type Draft = { text: string }
const load = (signal: AbortSignal) => listSpotChecks({}, signal)

export default function SpotChecksPage() {
  const { result, reload } = useCaregiverQuery('own-spot-checks', load, true)
  const mutation = useSelfServiceWrite()
  const [params, setParams] = useSearchParams()
  const target = params.get('spotCheckId')
  const validTarget = target !== null && /^[1-9]\d*$/.test(target) && Number.isSafeInteger(Number(target))
  const [filter, setFilter] = useState<'ALL' | 'UNANSWERED' | 'ANSWERED'>('ALL')
  const [drafts, setDrafts] = useState<Record<number, Draft>>({})
  const [validation, setValidation] = useState('')
  const [submittedId, setSubmittedId] = useState<number | null>(null)
  const located = useRef<string | null>(null)
  const accessError = isAccessFailure(mutation.error) ? mutation.error : result.status === 'error' && isAccessFailure(result.error) ? result.error : null
  const records = result.status === 'success' ? result.data : []
  const found = validTarget && records.some(check => check.id === Number(target))
  const effectiveFilter = target !== null ? 'ALL' : filter
  useEffect(() => {
    if (found && target !== located.current) {
      const card = document.getElementById('spot-check-' + target)
      card?.scrollIntoView?.({ block: 'nearest' }); card?.focus({ preventScroll: true })
      located.current = target
    }
  }, [found, target])
  const changeDraft = useCallback((id: number, text: string) => {
    setDrafts(old => ({ ...old, [id]: { text } }))
  }, [])
  function closeDraft(id: number) {
    setDrafts(old => { const next = { ...old }; delete next[id]; return next })
  }
  if (accessError && (Object.keys(drafts).length || submittedId !== null)) { setDrafts({}); setSubmittedId(null) }
  if (accessError) return <SelfServiceError error={accessError} retry={reload} subject="your spot checks" />

  function submit(event: FormEvent, id: number, text: string) {
    event.preventDefault(); setValidation('')
    const response = text.trim()
    if (!response || response.length > 500) { setValidation('Write a response of 1 to 500 characters.'); return }
    setSubmittedId(null)
    void mutation.send(signal => respondToSpotCheck(id, response, signal), saved => {
      closeDraft(saved.id); setSubmittedId(saved.id); reload()
    })
  }

  return <div className={styles.page}>
    <div className={styles.heading}><div><p className={styles.eyebrow}>Caregiver workspace</p><h1>Spot checks</h1><p className={styles.muted}>Read completed checks of your work and respond to the findings.</p></div><button className={styles.button} onClick={reload}>Refresh</button></div>
    <p className={styles.readOnly}>Only your completed conclusions are shown. Your response does not change the manager's conclusion. Saving a change replaces your current response.</p>
    <label className={styles.filterLabel}>Response status<select value={effectiveFilter} onChange={event => {
      const next = new URLSearchParams(params); next.delete('spotCheckId'); setParams(next, { replace: true }); setFilter(event.target.value as typeof filter)
    }}><option value="ALL">All conclusions</option><option value="UNANSWERED">Not answered</option><option value="ANSWERED">Answered</option></select></label>
    {validation && <p role="alert">{validation}</p>}
    {mutation.error !== null && <p role="alert">{serviceError(mutation.error)}</p>}
    {submittedId !== null && <p role="status" className={styles.readOnly}>Response saved for spot check #{submittedId}.{result.status === 'error' ? ' The list could not be refreshed; use Refresh to check it. Do not submit it again.' : ''}</p>}
    {result.status === 'loading' && <p role="status">Loading your spot checks…</p>}
    {result.status === 'error' && <SelfServiceError error={result.error} retry={reload} subject="your spot checks" />}
    {result.status === 'success' && <>
      <LastFetched at={result.receivedAt} subject="spot-check record" />
      {target !== null && !found && <p role="status" className={styles.alert}>This conclusion is currently unavailable. You can still read your own conclusions below.</p>}
      {records.length === 0 && <p className={styles.empty}>No completed spot checks yet.</p>}
      {records.length > 0 && records.filter(c => effectiveFilter === 'ALL' || (effectiveFilter === 'ANSWERED') === Boolean(c.caregiverResponse)).length === 0 && <p className={styles.empty}>No conclusions with this response status.</p>}
      {[...records].sort((a, b) => (b.checkedAt ?? '').localeCompare(a.checkedAt ?? '') || b.id - a.id)
        .filter(c => effectiveFilter === 'ALL' || (effectiveFilter === 'ANSWERED') === Boolean(c.caregiverResponse))
        .map(check => {
          const draft = drafts[check.id]
          const editing = draft !== undefined || !check.caregiverResponse
          const text = draft?.text ?? ''
          return <article key={check.id} id={'spot-check-' + check.id} tabIndex={-1} aria-label={`Spot check ${check.id}`} className={`${styles.card} ${found && check.id === Number(target) ? styles.highlighted : ''}`}>
            <div className={styles.cardTop}><h2>{check.elderName}</h2><span className={styles.badge}>{check.result === 'MEETS_STANDARD' ? 'Meets standard' : check.result === 'NEEDS_IMPROVEMENT' ? 'Needs improvement' : 'Result not provided'}</span></div>
            <p className={styles.time}>Visit: {visitTime(check.visitTime)}</p>
            <p>Purpose: {check.purpose || 'Not provided'}</p><p>Finding: {check.notes || 'No notes provided.'}</p>
            <p className={styles.muted}>Checked: {check.checkedAt ? visitTime(check.checkedAt) : 'Not provided'} · Spot check #{check.id}</p>
            {check.caregiverResponse && <><h3>Your saved response</h3><p>{check.caregiverResponse}</p></>}
            {editing ? <form className={styles.formPanel} aria-label={`Respond to spot check ${check.id}`} onSubmit={event => submit(event, check.id, text)}>
              <label htmlFor={'response-' + check.id}>Your response<textarea id={'response-' + check.id} rows={3} maxLength={500} value={text} disabled={mutation.pending} onChange={event => changeDraft(check.id, event.target.value)} /></label>
              <p className={styles.muted}>{text.length}/500 characters</p>
              <div className={styles.quick}><button className={styles.button} type="submit" disabled={mutation.pending || !text.trim()}>{mutation.pending ? 'Saving…' : check.caregiverResponse ? 'Save changes' : 'Submit response'}</button>
                {check.caregiverResponse && <button className={styles.button} type="button" disabled={mutation.pending} onClick={() => closeDraft(check.id)}>Cancel editing</button>}</div>
            </form> : <button className={styles.button} disabled={mutation.pending} onClick={() => changeDraft(check.id, check.caregiverResponse!)}>Edit response</button>}
          </article>
        })}
    </>}
  </div>
}
