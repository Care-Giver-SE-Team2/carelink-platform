import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { initialiseCsrf } from '../../../features/auth/api'
import type { FamilyElderProfile } from '../../../features/family-elders/api'
import { useFamilyElderProfiles } from '../../../features/family-elders/queries'
import { serviceApplicationKey } from '../../../features/service-applications/queries'
import { submitServiceApplication } from '../../../features/service-applications/api'
import { groupCareActivities, useCareActivities } from '../../../features/careplan/careActivities'
import { ApiError } from '../../../shared/api/client'
import { useSelectedElder } from '../components/selectedElder'
import { ElderSnapshot } from './ElderSnapshot'
import styles from '../intake/FamilyIntake.module.css'
import formStyles from '../intake/IntakeForm.module.css'

export function ServiceApplicationCreatePage() {
  const profiles = useFamilyElderProfiles()
  const { elderId, setElderId } = useSelectedElder()
  const eligible = profiles.data?.filter((elder) => elder.accessScope === 'FULL') ?? []
  // Honour the shared selection. Never silently apply for someone else after access is lost.
  const selected = elderId === null ? eligible[0] : eligible.find((elder) => elder.id === elderId)
  const selectedId = selected?.id
  useEffect(() => {
    if (elderId === null && selectedId !== undefined) setElderId(selectedId)
  }, [elderId, selectedId, setElderId])
  return <div className={styles.page}>
    <header className={styles.detailHeading}><p className={styles.eyebrow}>CARE SERVICES</p><h1>New service application</h1>
      <p className={styles.subtitle}>Choose a linked elder and the care they need.</p>
    </header>
    {profiles.isError ? <section className={styles.state}><p role="alert">Unable to load your elders. Check your binding or try again.</p>
      <button onClick={() => void profiles.refetch()}>Try again</button></section> : profiles.isPending ? <p role="status">Loading your elders…</p> : eligible.length === 0 ?
      <section className={styles.state}><h2>No elders available for a service application</h2><p>You need an active binding with full access. Read-only bindings allow viewing only.</p>
        <Link to="/family/family-bindings">Review binding requests</Link><p><Link to="/family/elders">My elders</Link></p>
      </section> : <ServiceForm key={selected?.id ?? 'none'} elders={eligible} selected={selected}
        select={setElderId} refreshing={profiles.isFetching} refresh={() => profiles.refetch()} />}
  </div>
}

// Switching elders starts a clean request, including notes and any in-flight submission.
function ServiceForm({ elders, selected, select, refreshing, refresh }: {
  elders: FamilyElderProfile[]; selected?: FamilyElderProfile; select: (id: number) => void
  refreshing: boolean; refresh: () => Promise<unknown>
}) {
  const navigate = useNavigate()
  const client = useQueryClient()
  const activities = useCareActivities()
  const [needs, setNeeds] = useState<string[]>([])
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<{ careNeeds?: string; notes?: string }>({})
  const [uncertain, setUncertain] = useState(false)
  const [blocked, setBlocked] = useState(false)
  const request = useRef<AbortController | null>(null)
  const alert = useRef<HTMLDivElement>(null)
  useEffect(() => () => request.current?.abort(), [])
  useEffect(() => { if (error) alert.current?.focus() }, [error])
  const incomplete = !!selected && (!selected.fullName.trim() || !selected.address?.trim() || !/^\d{6}$/.test(selected.postalCode ?? ''))

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (request.current || !selected || incomplete || blocked || refreshing) return
    // Only catalog codes: the manager builds the care plan from this same list.
    const careNeeds = [...new Set(needs)]
    const found = {
      careNeeds: careNeeds.length ? undefined : 'Choose at least one care service.',
      notes: [...notes.trim()].length > 2000 ? 'Notes must be 2000 characters or fewer.' : undefined,
    }
    setFieldErrors(found)
    if (found.careNeeds || found.notes) return
    const controller = new AbortController()
    request.current = controller
    setBusy(true); setError(''); setUncertain(false)
    let sent = false
    try {
      await initialiseCsrf(controller.signal)
      if (controller.signal.aborted) return
      sent = true
      const saved = await submitServiceApplication({ elderId: selected.id, careNeeds, notes: notes.trim() || null }, controller.signal)
      if (controller.signal.aborted) return
      void client.invalidateQueries({ queryKey: serviceApplicationKey })
      navigate(`/family/service-applications/${saved.id}`, { replace: true, state: { submittedApplicationId: saved.id } })
    } catch (failure) {
      if (controller.signal.aborted) return
      const status = failure instanceof ApiError ? failure.status : undefined
      if (status === 401) { navigate('/', { replace: true }); return }
      if (status === 403 || status === 404) {
        setBlocked(true)
        setError('Submission not permitted. Your binding or session protection may have changed. Refresh your elder access before trying again.')
      } else if (status === 409) {
        setBlocked(true)
        setError('Complete the saved name, address and six-digit postal code in My elders, then refresh your elder access.')
      } else if (status === 400) setError('The application was not accepted. Check your care services and notes. Your entries have been kept.')
      else if (sent) {
        setUncertain(true)
        setError('Submission status unknown. Your application may have been saved. Check your applications before submitting again. Your entries have been kept.')
      } else setError('Unable to prepare submission. Your application has not been sent. Please try again.')
    } finally {
      if (!controller.signal.aborted) setBusy(false)
      if (request.current === controller) request.current = null
    }
  }

  return <form className={formStyles.form} onSubmit={submit} aria-busy={busy} noValidate>
    {error && <div ref={alert} tabIndex={-1} role="alert" className={formStyles.errorSummary}>
      <p>{error}</p>
      {uncertain && <Link to="/family/service-applications" target="_blank" rel="noopener noreferrer">Check my service applications (new tab)</Link>}
      {blocked && <button type="button" disabled={refreshing} onClick={async () => { await refresh(); setBlocked(false); setError('') }}>Refresh elder access</button>}
    </div>}
    <fieldset className={formStyles.controls} disabled={busy}>
      <section className={formStyles.section}>
        <div className={formStyles.sectionHeading}><span>01</span><h2>Linked elder</h2></div>
        <div className={formStyles.field}><label htmlFor="service-elder">Elder</label>
          <select id="service-elder" value={selected?.id ?? ''} onChange={(event) => select(Number(event.target.value))}>
            <option value="" disabled>Choose an elder with full access</option>
            {elders.map((elder) => <option key={elder.id} value={elder.id}>{elder.fullName}</option>)}
          </select>
          <p className={formStyles.hint}>Only elders with full access are available. Switching elders clears this application's services and notes.</p>
        </div>
        {selected && <>
          {!blocked && <ElderSnapshot details={selected} />}
          <p><Link to={`/family/elders/${selected.id}`}>View or update basic details in My elders</Link></p>
          {incomplete && <p role="status" className={formStyles.errorSummary}>Complete the elder's saved name, address and six-digit postal code before applying.</p>}
        </>}
      </section>
      <section className={formStyles.section}>
        <div className={formStyles.sectionHeading}><span>02</span><h2>Care services</h2></div>
        {activities.isError ? <p role="alert">Unable to load care services.{' '}
          <button type="button" onClick={() => void activities.refetch()}>Try again</button></p>
          : activities.isPending ? <p role="status">Loading care services…</p>
          : groupCareActivities(activities.data).map((group) => <fieldset key={group.category} className={formStyles.choices}>
            <legend>{group.category}</legend>
            {group.activities.map(({ code, label }) => <label key={code}><input type="checkbox" checked={needs.includes(code)}
              onChange={(event) => {
                setNeeds(event.target.checked ? [...needs, code] : needs.filter((need) => need !== code))
                setFieldErrors((current) => ({ ...current, careNeeds: undefined }))
              }} />{label}</label>)}
          </fieldset>)}
        {fieldErrors.careNeeds && <p className={formStyles.fieldError}>{fieldErrors.careNeeds}</p>}
        <div className={formStyles.field}><label htmlFor="service-notes">Notes for this application (optional)</label>
          <textarea id="service-notes" rows={4} value={notes} aria-invalid={!!fieldErrors.notes}
            aria-describedby={fieldErrors.notes ? 'service-notes-error service-notes-hint' : 'service-notes-hint'}
            onChange={(event) => { setNotes(event.target.value); setFieldErrors((current) => ({ ...current, notes: undefined })) }} />
          {fieldErrors.notes && <p id="service-notes-error" className={formStyles.fieldError}>{fieldErrors.notes}</p>}
          <p id="service-notes-hint" className={formStyles.hint}>Up to 2000 characters. Anything else the care team should know, such as preferred times.</p>
        </div>
      </section>
    </fieldset>
    <aside className={formStyles.side}>
      <section className={formStyles.next}><h2>After submission</h2><p>Your application will be saved as Submitted, waiting for review.</p></section>
      <div className={formStyles.submitArea}>
        <button type="submit" className={styles.primaryButton} disabled={busy || !selected || incomplete || blocked || refreshing}>{busy ? 'Submitting…' : 'Submit service application'}</button>
        <Link to="/family/service-applications">Cancel and return to applications</Link>
      </div>
    </aside>
  </form>
}
