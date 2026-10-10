import type { WorkPack } from '../../features/caregiver/api'
import type { useExecutionCommands } from './useExecutionCommands'
import { commandError } from './commandErrors'
import { titleCase, visitTime } from './format'
import styles from './Caregiver.module.css'

export default function ExecutionPanel({ pack, command }: { pack: WorkPack; command: ReturnType<typeof useExecutionCommands> }) {
  const state = pack.execution
  if (!state) return <p className={styles.readOnly}>Execution controls are unavailable. Refresh or contact your manager.</p>
  const check = state.allowedActions.includes('CHECK_IN')
  return <section className={styles.formPanel} aria-label="Visit execution"><h2>Visit execution</h2>
    {command.saved && <p role="status">{command.saved}</p>}
    {command.validation && <p role="alert">{command.validation}</p>}
    {command.write.error != null && <p role="alert">{commandError(command.write.error)}</p>}
    {command.attempt && !command.write.pending && <><button className={styles.button} onClick={command.retry}>Retry same request</button>
      {!command.uncertain && <button className={styles.button} onClick={command.edit}>Refresh visit and edit</button>}</>}
    {state.checkedInAt ? <><p>Checked in: {visitTime(state.checkedInAt)} (SGT){state.lateArrival ? ' · Late arrival' : ''}</p>
      <p>Location record: {state.locationSource === 'MANUAL_LOCATION_NOTE' ? 'Manual location note — not GPS verified' : state.locationSource === 'GPS' ? 'GPS supplied by device — no geofence verification' : 'Not available'}</p></> : <>
      <p>Check-in window: {visitTime(state.checkInOpensAt)} – {visitTime(state.checkInClosesAt)} (SGT).</p>
      {check ? <form onSubmit={e => { e.preventDefault(); command.start(pack.visit.id, pack.visit.version) }}>
        <fieldset disabled={command.blocked}><legend>Record arrival location</legend>
          <label>Location source<select value={command.mode} onChange={e => command.setMode(e.target.value as 'GPS' | 'MANUAL_LOCATION_NOTE')}><option value="GPS">GPS</option><option value="MANUAL_LOCATION_NOTE">Manual location note (not GPS verified)</option></select></label>
          {command.mode === 'GPS' ? <><button type="button" className={styles.button} onClick={command.locate}>Get GPS location</button>{command.fix && <p role="status">Device location obtained. Accuracy: {Math.round(command.fix.accuracy)} m.</p>}</>
            : <label>Manual location note<textarea value={command.note} maxLength={500} rows={3} onChange={e => command.setNote(e.target.value)} required /></label>}
          <button className={styles.button}>Check in and start service</button>
        </fieldset>
      </form> : <p>Check-in is blocked: {state.blockedReason === 'VISIT_CHECK_IN_WINDOW' ? 'outside the time window' : state.blockedReason === 'VISIT_TASKS_REQUIRED' ? 'no valid plan task assigned — contact your manager' : 'current visit state or service strategy does not permit check-in'}.</p>}
    </>}
    {state.checkedOutAt && <p>Checked out: {visitTime(state.checkedOutAt)} (SGT) · Completed</p>}
    {state.allowedActions.includes('CHECK_OUT') && <>
      <p>Check out records the end of your service. No manager or elder approval is required.</p>
      {pack.tasks.some(task => task.status === 'PENDING') && <p className={styles.readOnly}>Some tasks are still pending. You may check out; their recorded status will not change.</p>}
      {pack.requiredEvidenceKinds.length > 0 && <p className={styles.readOnly}>Evidence cannot be uploaded or verified here yet. Missing evidence does not prevent check-out.</p>}
      <p className={styles.readOnly}>Save any task or health drafts you want to keep before checking out. New task results and health readings cannot be submitted after check-out.</p>
      <button className={styles.button} disabled={command.blocked} onClick={() => command.finishVisit(pack.visit.id, pack.visit.version)}>Check out</button>
    </>}
    {pack.visit.status === 'EXCEPTION' && !check && <p className={styles.readOnly}>Execution paused. Reporting or resolving an incident does not automatically resume this visit.</p>}
    {pack.visit.status === 'EXCEPTION' && check && <p className={styles.readOnly}>This previous missed-check-in alert does not prevent check-in. The alert remains recorded.</p>}
    <p className={styles.readOnly}>Task results are not check-out. Evidence upload is not included here; elder confirmation is a separate step after service completion.</p>
  </section>
}
export function ExecutionTasks({ pack, command }: { pack: WorkPack; command: ReturnType<typeof useExecutionCommands> }) {
  const canWrite = pack.execution?.allowedActions.includes('TASK_RESULT')
  return <section className={styles.card} aria-label="Visit tasks"><h2>Tasks · {pack.tasks.filter(t => t.status === 'DONE').length} done / {pack.tasks.length}</h2>
    {!pack.tasks.length && <p className={styles.muted}>{pack.serviceInstructions.length && pack.visit.status === 'SCHEDULED' ? 'Assigned plan tasks will be initialized when you check in.' : 'No tasks have been attached. Ask your manager to check this visit.'}</p>}
    {pack.tasks.map(task => {
      const draft = command.drafts[task.id] ?? { status: 'DONE' as const, outcome: '', caregiverNote: '' }
      const change = (field: string, value: string) => command.setDrafts(old => ({ ...old, [task.id]: { ...draft, [field]: value } }))
      return <article className={styles.task} key={task.id}><div className={styles.cardTop}><strong>{task.name}</strong><span className={styles.badge}>{titleCase(task.status)}</span></div>
        {task.outcome && <p>Outcome: {task.outcome}</p>}{task.caregiverNote && <p>Caregiver note: {task.caregiverNote}</p>}{task.completedAt && <p>Completed: {visitTime(task.completedAt)} (SGT)</p>}
        {canWrite && task.status === 'PENDING' && <form className={styles.formPanel} onSubmit={e => { e.preventDefault(); command.finishTask(pack.visit.id, task.id, pack.visit.version) }}>
          <fieldset disabled={command.blocked}><legend>Record result · {task.name}</legend>
            <label>Result<select value={draft.status} onChange={e => change('status', e.target.value)}><option value="DONE">Done</option><option value="SKIPPED">Skipped</option><option value="REFUSED">Refused</option></select></label>
            <label>Outcome (optional)<input value={draft.outcome} maxLength={255} onChange={e => change('outcome', e.target.value)} /></label>
            <label>{draft.status === 'DONE' ? 'Caregiver note (optional)' : 'Factual reason (required)'}<textarea value={draft.caregiverNote} maxLength={500} rows={3} onChange={e => change('caregiverNote', e.target.value)} required={draft.status !== 'DONE'} /></label>
            <button className={styles.button}>Save task result</button>
          </fieldset>
        </form>}
      </article>
    })}
  </section>
}
