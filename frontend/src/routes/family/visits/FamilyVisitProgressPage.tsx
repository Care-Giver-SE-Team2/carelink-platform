import type { ReactNode } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { ApiError } from '../../../shared/api/client'
import { serviceLabel, visitDate, visitStatusLabels, visitTime } from '../../../features/schedule/presentation'
import { useFamilyVisitProgress } from '../../../features/visits/useFamilyVisitProgress'
import type { VisitPart } from '../../../features/visits/useFamilyVisitProgress'
import type { FamilyVisitTask } from '../../../features/visits/types'
import styles from './FamilyVisitProgress.module.css'

const taskLabels: Record<FamilyVisitTask['status'], string> = {
  PENDING: 'Pending', DONE: 'Done', SKIPPED: 'Skipped', REFUSED: 'Refused',
}

/** Displays recorded care facts with independent section freshness and automatic refresh.
 * @author Wang Zhili
 */
export function FamilyVisitProgressPage() {
  const { visitId = '' } = useParams()
  const { resource, refresh, refreshing, retrySeconds, pause } = useFamilyVisitProgress(visitId)
  const disabled = refreshing || pause !== null
  return <div className={styles.progressPage}>
    <div className={styles.navigation}>
      <Link to="/family/schedule"><span aria-hidden="true">←&nbsp;</span>Back to schedule</Link>
      {resource.status === 'ready' && <button onClick={refresh}
        disabled={disabled}>Refresh progress</button>}
    </div>
    <header className={styles.hero}>
      <p className={styles.eyebrow}>YOUR FAMILY'S CARE</p>
      <h1>Visit progress</h1>
      <p>Recorded care activity for this visit. Checks for updates automatically while this page is open.</p>
    </header>
    {pause && <p className={styles.empty} role="status">
      Updates paused {pause === 'offline' ? 'while offline' : 'while this tab is hidden'}. Displayed information may be out of date.
    </p>}
    {retrySeconds && <p className={styles.empty} role="status">Updates unavailable. Automatic retry interval: {retrySeconds} seconds.</p>}
    {resource.status === 'loading' && !pause && <p className={styles.loading} role="status">Loading visit progress…</p>}
    {resource.status === 'error' && <VisitFeedback error={resource.error} onRetry={refresh} disabled={disabled} />}
    {resource.status === 'ready' && <div className={styles.sections}>
      <ProgressSection title="Visit details" resource={resource.parts.details} onRetry={refresh} disabled={disabled} paused={!!pause}>
        {(visit) => <>
          <div className={styles.overview}>
            <strong>{serviceLabel(visit.serviceType)}</strong>
            <span className={styles.badge} data-status={visit.status}>{visitStatusLabels[visit.status]}</span>
          </div>
          <p className={styles.reference}>Visit #{visit.id} · Elder profile #{visit.elderId}</p>
          <dl className={styles.facts}>
            <div><dt>Planned start</dt><dd><VisitTime value={visit.scheduledStart} /></dd></div>
            <div><dt>Planned end</dt><dd><VisitTime value={visit.scheduledEnd} /></dd></div>
            <div><dt>Checked in</dt><dd><VisitTime value={visit.checkedInAt} /></dd></div>
            <div><dt>Checked out</dt><dd><VisitTime value={visit.checkedOutAt} /></dd></div>
          </dl>
          <p>{visit.caregiverId === null ? 'Caregiver awaiting assignment' : 'Caregiver assigned'}</p>
          <p className={styles.timestamp}>Visit details checked <VisitTime value={visit.asOf} /></p>
        </>}
      </ProgressSection>
      <ProgressSection title="Service timeline" resource={resource.parts.timeline} onRetry={refresh} disabled={disabled} paused={!!pause}>
        {(entries, loadedAt) => <>
          {entries.length === 0 ? <p className={styles.empty}>No service updates recorded yet.</p>
            : <ol className={styles.timeline} aria-label="Service updates">
              {entries.map((entry) => <li key={entry.id}>
                <p>{visitStatusLabels[entry.fromState]} → {visitStatusLabels[entry.toState]}</p>
                <VisitTime value={entry.occurredAt} />
              </li>)}
            </ol>}
          <p className={styles.timestamp}>Timeline loaded <VisitTime value={loadedAt} /></p>
        </>}
      </ProgressSection>
      <ProgressSection title="Task progress" resource={resource.parts.tasks} onRetry={refresh} disabled={disabled} paused={!!pause}>
        {(tasks, loadedAt) => {
          const done = tasks.filter((task) => task.status === 'DONE').length
          return <>
            {tasks.length === 0 ? <p className={styles.empty}>No task records yet.</p> : <>
              <p className={styles.taskCount}>{done} of {tasks.length} tasks completed</p>
              <progress className={styles.completion} aria-label="Completed tasks" value={done} max={tasks.length} />
              <ul className={styles.tasks} aria-label="Visit tasks">
                {tasks.map((task) => <li key={task.id}>
                  <div className={styles.taskHeading}><strong>{task.name}</strong>
                    <span className={styles.badge} data-status={task.status}>{taskLabels[task.status]}</span></div>
                  {task.completedAt && <p className={styles.timestamp}>Completion time: <VisitTime value={task.completedAt} /></p>}
                  {!task.completedAt && task.status === 'DONE' && <p className={styles.timestamp}>Completion time not recorded.</p>}
                </li>)}
              </ul>
            </>}
            <p className={styles.timestamp}>Tasks loaded <VisitTime value={loadedAt} /></p>
          </>
        }}
      </ProgressSection>
      <p className={styles.note}>All times are in Singapore time (SGT). Each section is loaded separately.</p>
    </div>}
  </div>
}

function VisitTime({ value }: { value: string | null }) {
  return value ? <time dateTime={value}>{visitDate(value)}, {visitTime(value)}</time> : <span>Not recorded</span>
}

function ProgressSection<T>({ title, resource, onRetry, disabled, paused, children }: {
  title: string; resource: VisitPart<T>; onRetry: () => void; disabled: boolean; paused: boolean; children: (data: T, loadedAt: string) => ReactNode
}) {
  const previous = resource.status === 'success' ? resource : resource.previous
  return <section className={styles.section} aria-label={title}>
    <h2>{title}</h2>
    {resource.status === 'loading' && <p role="status" className={styles.empty}>
      {paused ? 'Waiting to resume.' : previous ? 'Updating… Displayed information may be out of date.' : `Loading ${title.toLowerCase()}…`}
    </p>}
    {resource.status === 'error' && <div role="alert" className={styles.sectionError}>
      <p>Unable to load {title.toLowerCase()}. Refresh to try again.</p>
      {previous && <p>Showing last loaded information; it may be out of date.</p>}
      <button onClick={onRetry} disabled={disabled}>Retry {title.toLowerCase()}</button>
    </div>}
    {previous && children(previous.data, previous.loadedAt)}
  </section>
}

function VisitFeedback({ error, onRetry, disabled }: { error: unknown; onRetry: () => void; disabled: boolean }) {
  const status = error instanceof ApiError ? error.status : undefined
  // The landing page is the only sign-in screen.
  if (status === 401) return <Navigate to="/" replace />
  const forbidden = status === 403
  const missing = status === 404
  const invalid = status === 400
  return <section className={styles.section} role="alert">
    <h2>{forbidden ? 'Visit access unavailable' : missing ? 'Visit not found' : invalid ? 'Invalid visit link' : 'Unable to load visit progress'}</h2>
    <p className={styles.empty}>{forbidden ? 'This visit is not available to your account. Your access may have changed.'
      : missing ? 'This visit could not be found. Return to your schedule to choose another.'
      : invalid ? 'Check the visit link or return to your schedule.'
      : 'Check your connection and try again.'}</p>
    {!missing && !invalid && <button onClick={onRetry} disabled={disabled}>Try again</button>}
    {forbidden && <Link to="/">Sign in with another account</Link>}
  </section>
}
