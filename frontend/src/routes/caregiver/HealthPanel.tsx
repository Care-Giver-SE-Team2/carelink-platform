import { useCallback, useEffect, useState } from 'react'
import type { WorkPack } from '../../features/caregiver/api'
import { getHealthRecords, type HealthFlag } from '../../features/caregiver-execution/api'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import type { useExecutionCommands } from './useExecutionCommands'
import { ApiError } from '../../shared/api/client'
import { visitTime } from './format'
import styles from './Caregiver.module.css'

const flags: Record<HealthFlag, string> = { NO_CONCERN: 'No concern marked', ATTENTION: 'Needs attention', MEDICAL_REVIEW: 'Medical review recommended' }
const metrics: Record<string, string> = { systolic: 'Systolic', diastolic: 'Diastolic', pulse: 'Pulse', temperature: 'Temperature' }

export default function HealthPanel({ pack, command, revision }: { pack: WorkPack; command: ReturnType<typeof useExecutionCommands>; revision: number }) {
  const [page, setPage] = useState(0)
  const load = useCallback((signal: AbortSignal) => getHealthRecords(pack.visit.id, page, signal), [pack.visit.id, page])
  const { result, reload } = useCaregiverQuery(`${pack.visit.id}:${page}:${revision}`, load)
  const failure = result.status === 'error' ? result.error : null
  const denied = failure instanceof ApiError && [401, 403, 404, 409].includes(failure.status)
  const setAccessError = command.setHealthAccessError
  useEffect(() => { if (denied) setAccessError(failure) }, [denied, failure, setAccessError])
  const observation = pack.healthObservation!
  const canWrite = pack.execution?.allowedActions.includes('HEALTH_RECORD')
  const showForm = canWrite && (!observation.healthFlag || command.healthEditing)
  const draft = command.healthDraft
  return <section className={styles.card} aria-label="Vital signs and health observation"><h2>Vital signs &amp; health observation</h2>
    <p className={styles.muted}>Record measured values only. Leave unmeasured readings blank. Readings are not assessed automatically. Health observations do not send an incident alert.</p>
    <p>Latest observation: {observation.healthFlag ? flags[observation.healthFlag] : 'Not recorded'}</p>
    {observation.healthNote && <p>{observation.healthNote}</p>}
    {showForm && <form className={styles.formPanel} onSubmit={event => { event.preventDefault(); command.recordHealth(pack.visit.id, pack.visit.version) }}>
      <fieldset disabled={command.blocked}><legend>Record vital signs and observation</legend>
        <div className={styles.details}>{(['systolic', 'diastolic', 'pulse', 'temperature'] as const).map(metric => <label key={metric}>
          {metrics[metric]} · {metric === 'temperature' ? '°C' : metric === 'pulse' ? 'bpm' : 'mmHg'}
          <input type="number" min={metric === 'temperature' ? '0.01' : '1'} max="999999.99" step={metric === 'temperature' ? '0.01' : '1'} value={draft[metric]} onChange={event => command.setHealthDraft(old => ({ ...old, [metric]: event.target.value }))} />
        </label>)}</div>
        <label>Health observation<select value={draft.healthFlag} onChange={event => command.setHealthDraft(old => ({ ...old, healthFlag: event.target.value as HealthFlag | '' }))} required>
          <option value="">Select an observation</option>{Object.entries(flags).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select></label>
        <label>Health note<textarea value={draft.healthNote} maxLength={1000} rows={3} onChange={event => command.setHealthDraft(old => ({ ...old, healthNote: event.target.value }))} /></label>
        <p className={styles.muted}>Explain any concern or why no readings were measured. Use Report incident if an incident needs to be reported.</p>
        <button className={styles.button}>Save health record</button>
      </fieldset>
    </form>}
    {canWrite && !showForm && <button className={styles.button} disabled={command.blocked} onClick={command.anotherMeasurement}>Record another measurement</button>}
    {!canWrite && <p className={styles.readOnly}>New health records are available only during a checked-in, active visit.</p>}
    <h3>Saved health records</h3>
    {result.status === 'loading' && <p role="status">Loading health records…</p>}
    {result.status === 'error' && !denied && <div role="alert"><p>Health records could not be loaded.</p><button className={styles.button} onClick={reload}>Retry loading health records</button></div>}
    {result.status === 'success' && <>
      {!result.data.items.length && <p>No health records saved on this page.</p>}
      {result.data.items.map(record => <article className={styles.task} key={record.id}>
        <strong>Recorded {visitTime(record.recordedAt)} (SGT) · #{record.id}</strong><p>{flags[record.healthFlag]}</p>
        {record.readings.length ? <ul>{record.readings.map(reading => <li key={reading.metric}>{metrics[reading.metric] ?? reading.metric}: {reading.value} {reading.unit}</li>)}</ul> : <p>No readings measured.</p>}
        {record.healthNote && <p>{record.healthNote}</p>}
      </article>)}
      <div className={styles.quick}><button className={styles.button} disabled={page === 0} onClick={() => setPage(old => old - 1)}>Previous health records</button>
        <span>Page {page + 1}</span><button className={styles.button} disabled={(page + 1) * result.data.size >= result.data.total} onClick={() => setPage(old => old + 1)}>Next health records</button></div>
    </>}
  </section>
}
