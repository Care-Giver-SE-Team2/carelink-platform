import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../../shared/api/client'
import { getCredential, getCredentials, getCredentialTypes, submitCredential } from '../../features/credentials/api'
import type { Credential, CredentialPage, CredentialType, Submission } from '../../features/credentials/api'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import EnglishDateField from './EnglishDateField'
import { isCalendarDate } from './calendarDate'
import { dateLabel, titleCase, todayInSingapore } from './format'
import { serviceError } from './selfService'
import SelfServiceError from './SelfServiceError'
import { LastFetched } from './components'
import styles from './Caregiver.module.css'

type FormSource = { action: 'NEW' | 'RENEWAL' | 'RESUBMIT'; credential: Credential | null; catalogue: CredentialType[] }
const approved = (status: string) => ['PUBLISHED', 'EXPIRING', 'EXPIRED'].includes(status)

export default function CredentialsPage() {
  const { credentialId } = useParams()
  const [page, setPage] = useState(0)
  const [source, setSource] = useState<FormSource | null>(null)
  const [receipt, setReceipt] = useState<number | null>(null)
  const [writeDenied, setWriteDenied] = useState<ApiError | null>(null)
  const load = useCallback(async (signal: AbortSignal): Promise<CredentialPage> => credentialId
    ? { items: [await getCredential(credentialId, signal)], page: 0, size: 1, total: 1 }
    : getCredentials(page, signal), [credentialId, page])
  const { result, reload } = useCaregiverQuery('own-credentials:' + (credentialId ?? page), load, true)
  const types = useCaregiverQuery('own-credential-types', getCredentialTypes, true)
  const accessError = [result, types.result].find(r => r.status === 'error' && r.error instanceof ApiError && [401, 403, 404].includes(r.error.status))
  useEffect(() => { setSource(null); setReceipt(null) }, [credentialId])
  useEffect(() => { if (accessError) { setSource(null); setReceipt(null) } }, [accessError])
  if (writeDenied) return <SelfServiceError error={writeDenied} retry={() => { setWriteDenied(null); reload(); types.reload() }} subject="your credentials" />
  if (accessError?.status === 'error') return <SelfServiceError error={accessError.error} retry={() => { reload(); types.reload() }} subject="your credentials" />

  return <div className={styles.page}>
    <div className={styles.heading}><div><p className={styles.eyebrow}>Caregiver workspace</p><h1>My credentials</h1><p className={styles.muted}>Submit certificates and follow your manager's review.</p></div><button className={styles.button} onClick={() => { reload(); types.reload() }}>Refresh</button></div>
    {credentialId && <Link to="/caregiver/credentials">All my credentials</Link>}
    <p className={styles.readOnly}>Uploading does not approve a certificate or grant rostering eligibility. Pending renewals do not clear expiry reminders. Your manager reviews certificates separately.</p>
    {result.status === 'loading' && <p role="status">Loading your credentials…</p>}
    {result.status === 'error' && <SelfServiceError error={result.error} retry={reload} subject="your credentials" />}
    {types.result.status === 'error' && <SelfServiceError error={types.result.error} retry={types.reload} subject="the credential catalogue" />}
    {types.result.status === 'success' && types.result.data.length === 0 && <p role="status">The credential catalogue has not been configured. Ask your manager; no demo types are added automatically.</p>}
    {result.status === 'success' && types.result.status === 'success' && types.result.data.length > 0 && !source &&
      <button className={styles.button} onClick={() => { setReceipt(null); setSource({ action: 'NEW', credential: null, catalogue: types.result.status === 'success' ? types.result.data : [] }) }}>Add certificate</button>}
    {receipt && <p role="status" className={styles.readOnly}>Submitted — awaiting manager review. <Link to={'/caregiver/credentials/' + receipt}>Open submitted certificate</Link></p>}
    {source && <CertificateForm key={source.action + ':' + source.credential?.id} source={source} types={source.catalogue}
      onCancel={() => setSource(null)} onDenied={failure => { setSource(null); setReceipt(null); setWriteDenied(failure) }} onSaved={id => { setSource(null); setReceipt(id); reload() }} />}
    {result.status === 'success' && <>
      <LastFetched at={result.receivedAt} subject="credential record" />
      {result.data.items.length === 0 && <p className={styles.empty}>No credentials recorded yet.</p>}
      {result.data.items.map(row => <details key={row.id} open={credentialId !== undefined || !row.historical}>
        <summary>{row.historical ? 'History' : 'Current record'} — {row.credentialTypeName} · Certificate {row.id} · {titleCase(row.status)}</summary>
        <article className={styles.card} aria-label={`Certificate ${row.id}`}>
        <div className={styles.cardTop}><h2><Link to={'/caregiver/credentials/' + row.id}>{row.credentialTypeName}</Link></h2><span className={styles.badge}>{titleCase(row.status)}</span></div>
        <p>Certificate number: {row.certificateNo || 'Not provided'} · Issuing body: {row.issuingBody || 'Not provided'}</p>
        <p>Valid from: {row.validFrom ? dateLabel(row.validFrom) : 'Not specified'} · Valid until: {row.expiryDate ? dateLabel(row.expiryDate) : 'No expiry'}</p>
        <p className={styles.muted}>{!approved(row.status) ? 'Not an approved credential.' : row.validFrom && row.validFrom > todayInSingapore() ? 'Approved but not yet effective.' : row.expiryDate && row.expiryDate < todayInSingapore() ? 'Expired.' : 'Approved; expiry includes the named day (SGT). Eligibility is assessed separately.'}</p>
        {row.renewsCredentialId && <p>Replaces <Link to={'/caregiver/credentials/' + row.renewsCredentialId}>certificate {row.renewsCredentialId}</Link>.</p>}
        {row.resubmitsCredentialId && <p>Resubmission of <Link to={'/caregiver/credentials/' + row.resubmitsCredentialId}>rejected certificate {row.resubmitsCredentialId}</Link>; the original decision and files are retained.</p>}
        {row.submittedAt && <p>Submitted: {row.submittedAt.replace('T', ' ')} (SGT)</p>}
        {row.reviewNote && <p>Review reason: {row.reviewNote}</p>}
        {row.reviewedAt && <p>Reviewed: {row.reviewedAt.replace('T', ' ')} (SGT)</p>}
        <h3>Documents</h3>
        {row.documents.length === 0 ? <p>No document available.</p> : <ul>{row.documents.map(document => <li key={document.id}>
          {document.contentAvailable ? <a href={`/api/credentials/${row.id}/documents/${document.id}/content`}>{document.fileName}</a> : <span>{document.fileName} — temporarily unavailable</span>}
          {' '}({document.contentType}, {Math.ceil(document.byteSize / 1024)} KiB)
        </li>)}</ul>}
        {!source && types.result.status === 'success' && approved(row.status) && <button className={styles.button} onClick={() => setSource({ action: 'RENEWAL', credential: row, catalogue: types.result.status === 'success' ? types.result.data : [] })}>Renew certificate</button>}
        {!source && types.result.status === 'success' && row.status === 'REJECTED' && <button className={styles.button} onClick={() => setSource({ action: 'RESUBMIT', credential: row, catalogue: types.result.status === 'success' ? types.result.data : [] })}>Resubmit certificate</button>}
      </article></details>)}
      {!credentialId && <div className={styles.heading}><button disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</button><span>Page {page + 1} · {result.data.total} records (including history)</span><button disabled={(page + 1) * 20 >= result.data.total} onClick={() => setPage(p => p + 1)}>Next</button></div>}
    </>}
  </div>
}

function CertificateForm({ source, types, onCancel, onSaved, onDenied }: { source: FormSource; types: CredentialType[]; onCancel: () => void; onSaved: (id: number) => void; onDenied: (failure: ApiError) => void }) {
  const [typeId, setTypeId] = useState(source.credential?.credentialTypeId ?? types[0]?.id ?? 0)
  const [number, setNumber] = useState(source.credential?.certificateNo ?? '')
  const [issuer, setIssuer] = useState(source.credential?.issuingBody ?? '')
  const [validFrom, setValidFrom] = useState(todayInSingapore())
  const [expiryDate, setExpiryDate] = useState('')
  const [noExpiry, setNoExpiry] = useState(false)
  const [files, setFiles] = useState<File[]>([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [uncertain, setUncertain] = useState(false)
  const frozen = useRef<Submission | null>(null)
  const request = useRef<AbortController | null>(null)
  const dirty = files.length > 0 || number !== (source.credential?.certificateNo ?? '') || issuer !== (source.credential?.issuingBody ?? '')
    || validFrom !== todayInSingapore() || expiryDate !== '' || noExpiry
  useEffect(() => () => request.current?.abort(), [])
  useEffect(() => {
    const guard = (event: BeforeUnloadEvent) => { if (dirty || frozen.current) { event.preventDefault(); event.returnValue = '' } }
    const navigationGuard = (event: MouseEvent) => {
      const link = event.target instanceof Element ? event.target.closest('a[href]') : null
      if (link && (dirty || frozen.current) && !window.confirm(frozen.current
        ? 'The submission result may be unconfirmed. Leaving loses the retry key. Stay and retry the same submission first. Leave anyway?'
        : 'Discard this unsubmitted certificate draft?')) { event.preventDefault(); event.stopPropagation() }
    }
    window.addEventListener('beforeunload', guard)
    document.addEventListener('click', navigationGuard, true)
    return () => { window.removeEventListener('beforeunload', guard); document.removeEventListener('click', navigationGuard, true) }
  }, [dirty])
  async function send(event?: FormEvent) {
    event?.preventDefault(); setError('')
    if (!frozen.current) {
      if (!issuer.trim() || issuer.trim().length > 150 || number.trim().length > 100 || !isCalendarDate(validFrom)
        || (!noExpiry && (!isCalendarDate(expiryDate) || expiryDate < validFrom || expiryDate < todayInSingapore()))) { setError('Choose valid dates and an issuing body. Expiry must include today or later, unless No expiry is selected.'); return }
      if (files.length < 1 || files.length > 3 || files.some(file => file.size === 0 || file.size > 5 * 1024 * 1024 || !/\.(jpe?g|png|pdf)$/i.test(file.name))) { setError('Select 1 to 3 JPEG, PNG or PDF documents, each non-empty and at most 5 MiB.'); return }
      frozen.current = { credentialTypeId: typeId, certificateNo: number.trim(), issuingBody: issuer.trim(), validFrom, expiryDate, noExpiry, files, clientRequestId: crypto.randomUUID() }
    }
    setPending(true)
    const controller = new AbortController(); request.current = controller
    try {
      const receipt = await submitCredential(source.action, source.credential?.id ?? null, frozen.current, controller.signal)
      if (!controller.signal.aborted) { frozen.current = null; onSaved(receipt.credentialId) }
    } catch (failure) {
      if (controller.signal.aborted) return
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) { frozen.current = null; onDenied(failure); return }
      const unknown = !(failure instanceof ApiError) || failure.status >= 500
      setUncertain(unknown)
      if (!unknown) frozen.current = null
      setError(unknown ? 'The result is not confirmed. Check My credentials with Refresh, then retry the same request if needed. Do not submit a new copy.' : serviceError(failure))
    } finally { if (!controller.signal.aborted) setPending(false) }
  }
  return <form className={styles.formPanel} onSubmit={send} aria-label="Submit certificate" noValidate>
    <h2>{source.action === 'NEW' ? 'Add certificate' : source.action === 'RENEWAL' ? 'Renew certificate' : 'Resubmit certificate'}</h2>
    <p>1–3 JPEG/PNG/PDF files · 5 MiB per file. PDF downloads only: simple unencrypted documents, at most 20 pages. Do not include unrelated personal information.</p>
    <fieldset disabled={pending || uncertain}>
      <label>Credential type<select value={typeId} disabled={source.credential !== null} onChange={e => setTypeId(Number(e.target.value))}>{types.map(type => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label>
      <label>Certificate number (optional)<input maxLength={100} value={number} onChange={e => setNumber(e.target.value)} /></label>
      <label>Issuing body<input maxLength={150} required value={issuer} onChange={e => setIssuer(e.target.value)} /></label>
      <EnglishDateField label="Valid from" value={validFrom} onChange={setValidFrom} />
      <label><input type="checkbox" checked={noExpiry} onChange={e => { setNoExpiry(e.target.checked); setExpiryDate('') }} /> No expiry</label>
      {!noExpiry && <EnglishDateField label="Valid until" value={expiryDate} onChange={setExpiryDate} />}
      <label>Documents<input type="file" multiple accept="image/jpeg,image/png,application/pdf" onChange={e => setFiles(Array.from(e.target.files ?? []))} /></label>
      <ul>{files.map((file, index) => <li key={index}>{file.name} ({Math.ceil(file.size / 1024)} KiB) <button type="button" onClick={() => setFiles(current => current.filter((_, i) => i !== index))}>Remove {file.name}</button></li>)}</ul>
      <button className={styles.button} type="submit">{pending ? 'Submitting…' : 'Submit for review'}</button>
    </fieldset>
    {error && <p role="alert">{error}</p>}
    {uncertain && <button className={styles.button} disabled={pending} type="button" onClick={() => void send()}>Retry the same submission</button>}
    <button type="button" disabled={pending || uncertain} onClick={() => { if (!dirty || window.confirm('Discard this unsubmitted certificate draft?')) onCancel() }}>Cancel</button>
  </form>
}
