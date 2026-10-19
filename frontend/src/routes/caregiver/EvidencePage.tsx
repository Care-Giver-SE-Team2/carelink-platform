import { useCallback, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError } from '../../shared/api/client'
import { getWorkPack } from '../../features/caregiver/api'
import type { WorkPack } from '../../features/caregiver/api'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import { useSelfServiceWrite, isAccessFailure } from './selfService'
import { useUnsavedCaregiverForm } from './useUnsavedCaregiverForm'
import SelfServiceError from './SelfServiceError'
import styles from './Caregiver.module.css'

type Evidence = { id: number; visitId: number; kind: string; verificationStatus: string; capturedAt: string | null; taskId: number | null; fileName: string | null; contentType: string | null; byteSize: number | null; available: boolean; downloadPath: string | null }
type EvidenceList = { items: Evidence[]; page: number; size: number; total: number; maxFiles: number }
export default function EvidencePage() {
  const { visitId = '' } = useParams()
  return <div className={styles.page}><Link className={styles.back} to={`/caregiver/visits/${encodeURIComponent(visitId)}`}>← Visit work pack</Link><div className={styles.heading}><h1>Visit evidence</h1></div>
    <p>PHOTO and SIGNATURE support JPEG/PNG only, up to 5 MiB and 20 megapixels. A signature image is not elder confirmation or identity verification. Uploaded evidence remains Unverified and does not complete a task.</p>
    <p>Captured time means server submission time, not the camera's timestamp. Do not include unnecessary personal information. Evidence is not required to check out.</p>
    {/^[1-9]\d*$/.test(visitId) ? <EvidenceWorkspace key={visitId} visitId={visitId} /> : <p role="alert">Invalid visit.</p>}
  </div>
}
function EvidenceWorkspace({ visitId }: { visitId: string }) {
  const [editing, setEditing] = useState<WorkPack | null>(null)
  const [page, setPage] = useState(0)
  const [notice, setNotice] = useState('')
  const load = useCallback(async (signal: AbortSignal) => {
    const pack = await getWorkPack(visitId, signal)
    const files = await api<EvidenceList>(`/visits/${visitId}/evidence?page=${page}&size=20`, { signal })
    return { pack, files }
  }, [visitId, page])
  const { result, reload, retainedData } = useCaregiverQuery(`evidence:${visitId}:${page}`, load, !editing)
  const data = result.status === 'success' ? result.data : result.status === 'loading' ? retainedData : undefined
  return <>
    <button className={styles.button} disabled={!!editing} onClick={reload}>Refresh evidence</button>
    {result.status === 'loading' && <p role="status">Loading evidence…</p>}
    {result.status === 'error' && <SelfServiceError error={result.error} retry={reload} subject="this visit's evidence" />}
    {notice && <p role="status">{notice}</p>}
    {data && <>
      {data.files.items.length === 0 && <p>No evidence recorded.</p>}
      {data.files.items.map(file => <article className={styles.card} key={file.id}><h2>{file.kind} · {file.verificationStatus}</h2><p>{file.fileName || 'Legacy evidence without safe file metadata'} · {file.capturedAt?.replace('T', ' ') || 'Submission time unavailable'} (SGT)</p>
        {file.available && file.downloadPath ? <a href={file.downloadPath} download>Download evidence {file.id}</a> : <p>File unavailable. Legacy references are not treated as local paths.</p>}
      </article>)}
      <div className={styles.pager}><button disabled={!!editing || page === 0} onClick={() => setPage(page - 1)}>Previous</button><span> Page {page + 1} </span><button disabled={!!editing || (page + 1) * 20 >= data.files.total} onClick={() => setPage(page + 1)}>Next</button></div>
      {data.pack.visit.status === 'IN_PROGRESS' && !editing && <button className={styles.button} onClick={() => { setEditing(data.pack); setNotice('') }}>Add evidence image</button>}
      {data.pack.visit.status !== 'IN_PROGRESS' && <p>Uploads are available only after check-in and before check-out. Existing evidence is read-only.</p>}
      {editing && <EvidenceUpload pack={editing} close={() => setEditing(null)} saved={() => { setEditing(null); setNotice('Evidence saved — Unverified. Your visit version has changed; refresh the work pack before another command.'); reload() }} />}
    </>}
  </>
}
function EvidenceUpload({ pack, close, saved }: { pack: WorkPack; close: () => void; saved: () => void }) {
  const [file, setFile] = useState<File | null>(null)
  const [kind, setKind] = useState('PHOTO')
  const [task, setTask] = useState('')
  const [key, setKey] = useState(() => crypto.randomUUID())
  const [validation, setValidation] = useState('')
  const mutation = useSelfServiceWrite()
  const uncertain = mutation.error !== null && (!(mutation.error instanceof ApiError) || mutation.error.status >= 500)
  const conflict = mutation.error instanceof ApiError && mutation.error.status === 409
  useUnsavedCaregiverForm(file !== null)
  function submit(event: FormEvent) {
    event.preventDefault()
    if (!file || !['image/png', 'image/jpeg'].includes(file.type) || file.size === 0 || file.size > 5 * 1024 * 1024) { setValidation('Choose a non-empty JPEG or PNG of at most 5 MiB.'); return }
    setValidation('')
    const data = new FormData(); data.append('file', file); data.append('kind', kind); data.append('expectedVersion', String(pack.visit.version)); data.append('clientRequestId', key); if (task) data.append('taskId', task)
    void mutation.send(async signal => {
      try { return await api(`/visits/${pack.visit.id}/evidence`, { method: 'POST', body: data, signal }) }
      catch (failure) {
        // Release the selected private image when the account loses access.
        if (isAccessFailure(failure)) setFile(null)
        throw failure
      }
    }, saved)
  }
  if (isAccessFailure(mutation.error)) return <SelfServiceError error={mutation.error} retry={close} subject="this visit's evidence" />
  return <form className={styles.formPanel} aria-label="Upload evidence" onSubmit={submit} noValidate>
    <fieldset disabled={mutation.pending || uncertain || conflict}>
      <label>Evidence kind<select value={kind} onChange={event => { setKind(event.target.value); setKey(crypto.randomUUID()) }}><option>PHOTO</option><option>SIGNATURE</option></select></label>
      <label>Related task (optional)<select value={task} onChange={event => { setTask(event.target.value); setKey(crypto.randomUUID()) }}><option value="">No task selected</option>{pack.tasks.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
      <label>Evidence image<input type="file" accept="image/jpeg,image/png" onChange={event => { setFile(event.target.files?.[0] ?? null); setKey(crypto.randomUUID()) }} /></label>
    </fieldset>
    {validation && <p role="alert">{validation}</p>}
    {mutation.error !== null && <p role="alert">{uncertain ? 'Upload result unknown. Keep this file and request unchanged; retry this request or close and check the saved list before a new submission.' : mutation.error instanceof ApiError ? mutation.error.message : 'Unable to upload.'}</p>}
    <button className={styles.button} disabled={mutation.pending || conflict}>{uncertain ? 'Retry unchanged upload' : 'Upload image'}</button>
    <button type="button" className={styles.button} disabled={mutation.pending} onClick={() => { if (!file || window.confirm('Close this selected image? If upload result is unknown, refresh the saved list before submitting again.')) close() }}>Close upload</button>
  </form>
}
