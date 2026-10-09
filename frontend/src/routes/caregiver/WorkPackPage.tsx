import { useCallback } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { getWorkPack } from '../../features/caregiver/api'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import { LastFetched, QueryError } from './components'
import { titleCase, visitTime } from './format'
import styles from './Caregiver.module.css'
import ExecutionPanel, { ExecutionTasks } from './ExecutionPanel'
import { useExecutionCommands } from './useExecutionCommands'
import { ApiError } from '../../shared/api/client'
import HealthPanel from './HealthPanel'

export default function WorkPackPage() {
  const { visitId = '' } = useParams()
  const [params] = useSearchParams()
  const backParams = new URLSearchParams()
  for (const name of ['dateFrom', 'dateTo']) if (params.has(name)) backParams.set(name, params.get(name)!)
  const back = '/caregiver' + (backParams.size ? '?' + backParams.toString() : '')
  return <div className={styles.page}><Link className={styles.back} to={back}>← My schedule</Link>
    {!/^[1-9]\d*$/.test(visitId) ? <div role="alert" className={styles.error}>Visit not found</div> : <WorkPack key={visitId} id={visitId} back={back} />}
  </div>
}
function WorkPack({ id, back }: { id: string; back: string }) {
  const load = useCallback((signal: AbortSignal) => getWorkPack(id, signal), [id])
  const { result, reload } = useCaregiverQuery(id, load, true)
  const command = useExecutionCommands(reload)
  const accessError = command.accessError ?? (result.status === 'error' && result.error instanceof ApiError && [401,403,404,409].includes(result.error.status) ? result.error : null)
  if (accessError && (Object.keys(command.drafts).length || command.attempt || command.fix || command.note || command.saved || command.locating || command.hasHealthDraft)) command.clearProtected()
  if (accessError) return <QueryError error={accessError} retry={command.recover} back={back} />
  if (result.status === 'loading') return <p role="status">Loading assigned work pack…</p>
  if (result.status === 'error') return <QueryError error={result.error} retry={reload} back={back} />
  const pack = result.data
  return <>
    <p className={styles.eyebrow}>Assigned visit · #{pack.visit.id}</p>
    <div className={styles.heading}><div><h1>{pack.elder.preferredName}</h1><p className={styles.muted}>{titleCase(pack.visit.serviceType)}</p></div><button className={styles.button} disabled={command.write.pending} onClick={reload}>Refresh</button></div>
    {pack.execution?.allowedActions.includes('REPORT_INCIDENT') && !command.blocked && <Link className={styles.linkButton} to={'/caregiver/visits/' + id + '/report-incident' + (back.includes('?') ? back.slice(back.indexOf('?')) : '')}>Report incident</Link>}
    <ExecutionPanel pack={pack} command={command} />
    <LastFetched at={result.receivedAt} />
    <section className={styles.card} aria-label="Visit details"><div className={styles.cardTop}><strong>Visit details</strong><span className={styles.badge}>{titleCase(pack.visit.status)}</span></div>
      <dl className={styles.details}>
        <div><dt>Starts · Singapore time</dt><dd>{visitTime(pack.visit.scheduledStart)}</dd></div>
        <div><dt>Ends · Singapore time</dt><dd>{pack.visit.scheduledEnd ? visitTime(pack.visit.scheduledEnd) : 'Not provided'}</dd></div>
        <div><dt>Service address</dt><dd>{pack.elder.serviceAddress || 'Not provided'}</dd></div>
        <div><dt>Sector</dt><dd>{pack.elder.postalSector || 'Not provided'}</dd></div>
        <div><dt>Languages</dt><dd>{pack.elder.languageNeeds.join(', ') || 'Not provided'}</dd></div>
        <div><dt>Access notes</dt><dd>{pack.elder.accessNotes || 'Not provided'}</dd></div>
        <div><dt>Emergency notes</dt><dd>{pack.elder.emergencyNotes || 'Not provided'}</dd></div>
      </dl>
    </section>
    {pack.carePlanId != null ? <section className={styles.card} aria-label="Assigned care plan"><p className={styles.eyebrow}>Assigned care plan</p>
      <h2>{pack.carePlanVersion != null ? 'Version ' + pack.carePlanVersion : 'No plan linked'}</h2>
      <p className={styles.muted}>Plan #{pack.carePlanId} · This is the version assigned to this visit.</p>
      {pack.serviceInstructions.length > 0 && <ul>{pack.serviceInstructions.map((instruction, i) => <li key={i}>{instruction}</li>)}</ul>}
    </section> : <section className={styles.card} aria-label="Extra service"><p className={styles.eyebrow}>Extra service</p>
      {/* A visit no care plan produced: an extra service the elder asked for and the family approved. */}
      <h2>{pack.serviceInstructions[0] ?? pack.visit.serviceType}</h2>
      <p className={styles.muted}>Requested on top of the elder's care plan. It becomes your one task when you check in.</p>
      {pack.serviceInstructions.length > 1 && <><p className={styles.eyebrow}>Instructions</p>
        <ul>{pack.serviceInstructions.slice(1).map((instruction, i) => <li key={i}>{instruction}</li>)}</ul></>}
    </section>}
    <ExecutionTasks pack={pack} command={command} />
    {pack.healthObservation && <HealthPanel pack={pack} command={command} revision={result.receivedAt} />}
    <section className={styles.card} aria-label="Required evidence"><h2>Required evidence</h2><p className={styles.muted}>Requirements for the tasks assigned to this visit.</p>
      {pack.requiredEvidenceKinds.length ? <div className={styles.pills}>{pack.requiredEvidenceKinds.map(kind => <span className={styles.badge} key={kind}>{titleCase(kind)}</span>)}</div> : <p>No evidence requirements recorded.</p>}
    </section>
  </>
}
