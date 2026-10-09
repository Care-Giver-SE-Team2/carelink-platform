import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { initialiseCsrf } from '../../../features/auth/api'
import type { FamilyElderProfile } from '../../../features/family-elders/api'
import { useFamilyElderProfiles } from '../../../features/family-elders/queries'
import { serviceApplicationKey } from '../../../features/service-applications/queries'
import { careLabels, submitServiceApplication } from '../../../features/service-applications/api'
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
    <div className={styles.detailNav}><Link to="/family/service-applications">← Back to service applications</Link></div>
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
  const [needs, setNeeds] = useState<string[]>([])
  const [other, setOther] = useState('')
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
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
    const careNeeds = [...new Set([...needs, ...other.split('\n')].map((need) => need.trim()).filter(Boolean))]
    if (!careNeeds.length || careNeeds.length > 20 || careNeeds.some((need) => [...need].length > 100)) {
      setError('Choose at least one care service, up to 20 in total. Each service can contain up to 100 characters.'); return
    }
    if ([...notes.trim()].length > 2000) { setError('Notes must be 2000 characters or fewer.'); return }
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
            {elders.map((elder) => <option key={elder.id} value={elder.id}>{elder.fullName} (#{elder.id})</option>)}
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
        <fieldset className={formStyles.choices}><legend>Choose care services</legend>
          {Object.entries(careLabels).map(([value, label]) => <label key={value}><input type="checkbox" checked={needs.includes(value)}
            onChange={(event) => setNeeds(event.target.checked ? [...needs, value] : needs.filter((need) => need !== value))} />{label}</label>)}
        </fieldset>
        <div className={formStyles.field}><label htmlFor="service-other">Other care needs</label>
          <textarea id="service-other" rows={3} value={other} onChange={(event) => setOther(event.target.value)} aria-describedby="service-other-hint" />
          <p id="service-other-hint" className={formStyles.hint}>One service per line; up to 20 services in total, 100 characters each.</p>
        </div>
        <div className={formStyles.field}><label htmlFor="service-notes">Notes for this application (optional)</label>
          <textarea id="service-notes" rows={4} value={notes} onChange={(event) => setNotes(event.target.value)} aria-describedby="service-notes-hint" />
          <p id="service-notes-hint" className={formStyles.hint}>Up to 2000 characters. These notes apply to this request.</p>
        </div>
      </section>
    </fieldset>
    <aside className={formStyles.side}>
      <p className={formStyles.intro}>Basic details come from My elders. You only need to choose care services and add any notes.</p>
      <section className={formStyles.next}><h2>After submission</h2><p>Your application will be saved as Submitted, waiting for review.</p></section>
      <div className={formStyles.submitArea}>
        <button type="submit" disabled={busy || !selected || incomplete || blocked || refreshing}>{busy ? 'Submitting…' : 'Submit service application'}</button>
        <Link to="/family/service-applications">Cancel and return to applications</Link>
      </div>
    </aside>
  </form>
}
