import { formatShort, formatTime } from '../../../features/provider-platform/format'
import type { Job, TimelineStep } from '../../../features/provider-platform/types'
import { cx } from '../../../shared/components/ui/cx'
import { SectionLabel } from './ui'
import styles from './JobTimeline.module.css'

const LABELS: Record<TimelineStep['step'], string> = {
  dispatched: 'Dispatched',
  accepted: 'Accepted',
  completed: 'Completed',
  declined: 'Declined by you',
  cancelled: 'Cancelled by CareLink',
}

type Row = { label: string; at: string | null; tone: 'done' | 'latest' | 'stopped' | 'pending' }

/** What happened so far, then what is still to come for a job that's still live. */
function rows(job: Job): Row[] {
  const done = job.timeline
  const out: Row[] = done.map((t, i) => ({
    label: LABELS[t.step],
    at: t.at,
    tone:
      t.step === 'cancelled' || t.step === 'declined'
        ? 'stopped'
        : i === done.length - 1 && t.step !== 'dispatched'
          ? 'latest'
          : 'done',
  }))
  if (job.status === 'new') out.push({ label: 'Accepted', at: null, tone: 'pending' }, { label: 'Completed', at: null, tone: 'pending' })
  if (job.status === 'accepted') out.push({ label: 'Completed', at: null, tone: 'pending' })
  return out
}

/**
 * Dispatched → Accepted → Completed, as squares joined by a rule: a vertical card on a desktop,
 * three bars across on a phone. The latest step is green, a cancellation red, steps to come
 * hollow.
 */
export function JobTimeline({ job }: { job: Job }) {
  const steps = rows(job)
  return (
    <section className={styles.card} aria-labelledby="job-timeline-heading">
      <SectionLabel id="job-timeline-heading">Timeline</SectionLabel>
      <ol className={styles.steps} style={{ ['--steps' as string]: steps.length }}>
        {steps.map((row, i) => (
          <li key={`${row.label}-${i}`} className={cx(styles.step, styles[row.tone], steps[i + 1]?.tone === 'pending' && styles.beforePending)}>
            <span className={styles.marker} aria-hidden="true" />
            <span className={styles.text}>
              <span className={styles.label}>{row.label}</span>
              {row.at && (
                <>
                  <span className={styles.atLong}>{formatShort(row.at)}</span>
                  <span className={styles.atShort}>{formatTime(row.at)}</span>
                </>
              )}
            </span>
          </li>
        ))}
      </ol>
    </section>
  )
}
