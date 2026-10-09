import { useCallback, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { getWorkPack } from '../../features/caregiver/api'
import { reportIncident, type ReportInput } from '../../features/caregiver-incidents/api'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import { QueryError } from './components'
import { isAccessFailure, useSelfServiceWrite } from './selfService'
import { commandError, unknownResult } from './commandErrors'
import styles from './Caregiver.module.css'
import { ApiError } from '../../shared/api/client'

export default function ReportIncidentPage() {
  const { visitId = '' } = useParams()
  const [params] = useSearchParams()
  const dates = new URLSearchParams()
  for (const key of ['dateFrom', 'dateTo']) if (params.has(key)) dates.set(key, params.get(key)!)
  const back = '/caregiver/visits/' + visitId + (dates.size ? '?' + dates.toString() : '')
  return <div className={styles.page}><Link className={styles.back} to={back}>← Assigned work pack</Link>
    {/^[1-9]\d*$/.test(visitId) ? <ReportForm key={visitId} id={visitId} /> : <p role="alert">Visit not found</p>}</div>
}
function ReportForm({ id }: { id: string }) {
  const navigate = useNavigate()
  const load = useCallback((signal: AbortSignal) => getWorkPack(id, signal), [id])
  const { result, reload } = useCaregiverQuery('report-visit-' + id, load, true)
  const write = useSelfServiceWrite()
  const [category, setCategory] = useState('')
  const [severity, setSeverity] = useState('')
  const [description, setDescription] = useState('')
  const [attempt, setAttempt] = useState<ReportInput | null>(null)
  const [validation, setValidation] = useState('')
  const accessError = isAccessFailure(write.error) ? write.error : result.status === 'error' && result.error instanceof ApiError && [401,403,404,409].includes(result.error.status) ? result.error : null
  if (accessError && (description || category || severity || attempt)) { setDescription(''); setCategory(''); setSeverity(''); setAttempt(null) }
  if (accessError) return <QueryError error={accessError} retry={() => { write.clearError(); reload() }} back="/caregiver" />
  const pack = result.status === 'success' ? result.data : null
  const uncertain = write.error != null && unknownResult(write.error)
  function submit(event: FormEvent) {
    event.preventDefault()
    if (!pack) return
    if (!category || !severity || !description.trim() || description.trim().length > 2000) { setValidation('Choose a category and severity, and write 1 to 2,000 characters.'); return }
    setValidation('')
    const payload = attempt ?? { visitId: Number(id), expectedVersion: pack.visit.version, category, severity, description: description.trim(), clientRequestId: crypto.randomUUID() }
    setAttempt(payload)
    void write.send(signal => reportIncident(payload, signal), saved => navigate('/caregiver/incidents/' + saved.report.id))
  }
  return <><h1>Report incident</h1><p className={styles.readOnly}>Record observed facts and actions, not a diagnosis. For an emergency, follow your organisation's emergency contact procedure now. Online submission does not mean someone has taken over.</p>
    {result.status === 'loading' && <p role="status">Checking assigned visit…</p>}
    {result.status === 'error' && <QueryError error={result.error} retry={reload} back="/caregiver" />}
    {validation && <p role="alert">{validation}</p>}{write.error != null && <p role="alert">{commandError(write.error)}</p>}
    {uncertain && <Link to="/caregiver/incidents" target="_blank" rel="noopener noreferrer">Open my reports in a new tab before retrying</Link>}
    <form className={styles.formPanel} onSubmit={submit}><fieldset disabled={!pack || write.pending || attempt != null}>
      <legend>Visit #{id}</legend>
      <label>Category<select value={category} onChange={e => setCategory(e.target.value)} required><option value="">Choose category</option>{['SOS', 'MEDICAL', 'FALL', 'SERVICE', 'OTHER'].map(v => <option key={v}>{v}</option>)}</select></label>
      <label>Severity<select value={severity} onChange={e => setSeverity(e.target.value)} required><option value="">Choose severity</option>{['LOW', 'MEDIUM', 'HIGH'].map(v => <option key={v}>{v}</option>)}</select></label>
      <label>Observed facts and actions<textarea rows={5} value={description} onChange={e => setDescription(e.target.value)} maxLength={2000} required /></label>
    </fieldset>
      <button className={styles.button} disabled={!pack || write.pending || (pack.execution ? !pack.execution.allowedActions.includes('REPORT_INCIDENT') && !attempt : pack.visit.status === 'CANCELLED')}>{write.pending ? 'Submitting…' : attempt ? 'Retry same request' : 'Submit report'}</button>
      {attempt && write.error != null && !uncertain && <button type="button" className={styles.button} onClick={() => { setAttempt(null); reload() }}>Refresh visit and edit</button>}
    </form>
    <p>Submitting during open work pauses the visit. Reports after completion do not undo completion.</p>
    {pack?.execution && !pack.execution.allowedActions.includes('REPORT_INCIDENT') && <p role="status">A new report is not currently allowed for this visit. Refresh or contact your manager.</p>}
  </>
}
