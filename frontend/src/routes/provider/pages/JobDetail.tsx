import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { acceptJob, completeJob, declineJob, getJob, newIdempotencyKey } from '../../../features/provider-platform/api'
import { ApiError, isTransient } from '../../../features/provider-platform/errors'
import { areaLong, formatDayTime, formatSchedule, formatShort, formatTime } from '../../../features/provider-platform/format'
import type { DeclineReason, Job } from '../../../features/provider-platform/types'
import { cx } from '../../../shared/components/ui/cx'
import { CompleteDialog, DeclineDialog } from '../components/JobDialogs'
import { JobTimeline } from '../components/JobTimeline'
import { Banner, Button, Facts, LockIcon, SectionLabel, StateTag } from '../components/ui'
import { WorkbenchShell } from '../components/WorkbenchShell'
import { useOnline } from '../useOnline'
import { useWorkbench } from '../workbenchContext'
import styles from './JobDetail.module.css'

type Op = 'accept' | 'decline' | 'complete'

type Attempt =
  | { op: 'accept'; key: string }
  | { op: 'decline'; key: string; body: { reason: DeclineReason; note: string | null } }
  | { op: 'complete'; key: string; body: { actualStart: string; actualEnd: string; note: string } }

type Failure = { kind: 'cancelled'; message: string } | { kind: 'network'; requestId: string } | { kind: 'expired' } | { kind: 'other'; message: string }

const NOT_SAVED: Record<Op, string> = {
  accept: "Your acceptance wasn't saved.",
  decline: "Your decline wasn't saved.",
  complete: "Your completion wasn't saved.",
}

/** The time of the job's latest step, in the words the sub line uses. */
function latestLine(job: Job): string {
  const last = job.timeline[job.timeline.length - 1]
  const word = { dispatched: 'received', accepted: 'accepted', completed: 'completed', declined: 'declined', cancelled: 'cancelled' }[last.step]
  return `${job.id} · ${word} ${formatShort(last.at)}`
}

/**
 * One job (PT04). New: Accept or Decline, the address withheld. Accepted: the address, and
 * Mark complete. Completed, declined and cancelled are read-only. Every action sends an
 * Idempotency-Key; Retry after a dropped request re-sends the same key, so CareLink applies it
 * once. A 409 means CareLink cancelled the job first; a 401 that the token ran out.
 */
export default function JobDetail() {
  const { jobId = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const online = useOnline()
  const { token, expireToken, expired } = useWorkbench()
  const [dialog, setDialog] = useState<'decline' | 'complete' | null>(null)
  const [pending, setPending] = useState<Op | null>(null)
  const [attempt, setAttempt] = useState<Attempt | null>(null)
  const [failure, setFailure] = useState<Failure | null>(null)

  const job = useQuery({
    queryKey: ['provider', 'job', jobId],
    queryFn: () => getJob(token!.accessToken, jobId),
    enabled: token !== null,
    retry: false,
  })

  const loadUnauthorised = job.error instanceof ApiError && job.error.status === 401
  useEffect(() => {
    if (loadUnauthorised) expireToken()
  }, [loadUnauthorised, expireToken])

  async function run(next: Attempt) {
    if (!token) {
      setFailure({ kind: 'expired' })
      return
    }
    setAttempt(next)
    setPending(next.op)
    setFailure(null)
    try {
      const updated =
        next.op === 'accept'
          ? await acceptJob(token.accessToken, jobId, next.key)
          : next.op === 'decline'
            ? await declineJob(token.accessToken, jobId, next.body, next.key)
            : await completeJob(token.accessToken, jobId, next.body, next.key)
      queryClient.setQueryData(['provider', 'job', jobId], updated)
      void queryClient.invalidateQueries({ queryKey: ['provider', 'jobs'] })
      void queryClient.invalidateQueries({ queryKey: ['provider', 'deliveries'] })
      setDialog(null)
      setAttempt(null)
    } catch (err) {
      setDialog(null)
      if (err instanceof ApiError && err.status === 409) {
        setFailure({ kind: 'cancelled', message: err.message })
        setAttempt(null)
        await job.refetch()
      } else if (err instanceof ApiError && err.status === 401) {
        setFailure({ kind: 'expired' })
        expireToken()
      } else if (isTransient(err)) {
        setFailure({ kind: 'network', requestId: err instanceof ApiError ? err.requestId : '' })
      } else {
        setFailure({ kind: 'other', message: err instanceof Error ? err.message : 'CareLink refused the request.' })
        setAttempt(null)
      }
    } finally {
      setPending(null)
    }
  }

  const data = job.data
  const actionsLocked = pending !== null || !online

  return (
    <WorkbenchShell dense>
      <div className={styles.page}>
        <nav className={styles.crumbs} aria-label="Breadcrumb">
          <Link to="/provider/sandbox" className={styles.crumbLink}>
            <span className={styles.crumbBack}>← </span>Sandbox
          </Link>
          <span className={styles.crumbSep}> / </span>
          <span className={styles.crumbId}>{jobId}</span>
        </nav>

        {failure?.kind === 'expired' || (!token && (expired || !data)) ? (
          <Banner
            tone="warning"
            title={expired || failure?.kind === 'expired' ? 'Access token expired' : 'Not connected'}
            role="alert"
            action={
              <Button size="small" onClick={() => navigate('/provider/sandbox')}>
                Reconnect
              </Button>
            }
          >
            Get a new token in the Sandbox, then retry the action.
          </Banner>
        ) : null}

        {failure?.kind === 'cancelled' && (
          <Banner tone="danger" title="Job was cancelled by CareLink" role="alert" action={<Link to="/provider/sandbox">Back to jobs</Link>}>
            {data?.cancelReason ?? failure.message}
          </Banner>
        )}
        {failure?.kind === 'network' && attempt && (
          <Banner
            tone="danger"
            title="Couldn't reach CareLink"
            role="alert"
            action={
              <Button size="small" disabled={!online} onClick={() => void run(attempt)}>
                Retry
              </Button>
            }
          >
            {NOT_SAVED[attempt.op]} Check your connection and try again.{' '}
            {failure.requestId && <span className={styles.requestId}>{failure.requestId}</span>}
          </Banner>
        )}
        {failure?.kind === 'other' && (
          <Banner tone="danger" title="CareLink refused the request" role="alert">
            {failure.message}
          </Banner>
        )}

        {job.isError && !loadUnauthorised ? (
          <Banner
            tone="danger"
            title={job.error instanceof ApiError && job.error.status === 404 ? 'No such job' : "Couldn't reach CareLink"}
            role="alert"
            action={<Link to="/provider/sandbox">Back to jobs</Link>}
          >
            {job.error.message}
          </Banner>
        ) : !data ? (
          token && <p className={styles.loading}>Loading the job…</p>
        ) : (
          <>
            <header className={styles.head}>
              <div className={styles.heading}>
                <div className={styles.titleRow}>
                  <h1 className={styles.title}>
                    {data.service} · {formatDayTime(data.scheduledStart)}
                  </h1>
                  <StateTag kind={data.status} />
                </div>
                <span className={styles.sub}>{latestLine(data)}</span>
              </div>
              {data.status === 'new' ? (
                <div className={styles.actions}>
                  <Button size="large" className={styles.secondaryAction} disabled={actionsLocked} onClick={() => setDialog('decline')}>
                    {pending === 'decline' ? 'Declining…' : 'Decline'}
                  </Button>
                  <Button
                    size="large"
                    variant="primary"
                    className={styles.primaryAction}
                    disabled={!online || (pending !== null && pending !== 'accept')}
                    busy={pending === 'accept' && 'Accepting…'}
                    onClick={() => void run({ op: 'accept', key: newIdempotencyKey() })}
                  >
                    Accept job
                  </Button>
                </div>
              ) : data.status === 'accepted' ? (
                <div className={cx(styles.actions, styles.single)}>
                  <Button
                    size="large"
                    variant="primary"
                    className={styles.primaryAction}
                    disabled={!online}
                    busy={pending === 'complete' && 'Saving…'}
                    onClick={() => setDialog('complete')}
                  >
                    Mark complete
                  </Button>
                </div>
              ) : (
                <span className={styles.readOnly}>Read-only</span>
              )}
            </header>
            {!online && (data.status === 'new' || data.status === 'accepted') && (
              <p className={styles.offline} role="status">
                You're offline. Actions come back when your connection does.
              </p>
            )}

            <div className={styles.body}>
              <div className={styles.main}>
                {data.completion && (
                  <section className={styles.block} aria-labelledby="job-completion-heading">
                    <SectionLabel id="job-completion-heading">Completion</SectionLabel>
                    <Facts
                      className={styles.facts}
                      rows={[
                        { label: 'Actual time', value: `${formatTime(data.completion.actualStart)} – ${formatTime(data.completion.actualEnd)}`, mono: true },
                        { label: 'Note', value: data.completion.note },
                      ]}
                    />
                  </section>
                )}

                <Facts
                  className={styles.facts}
                  rows={
                    data.status === 'cancelled' || data.status === 'declined'
                      ? [
                          { label: 'Service', value: data.service },
                          { label: 'Scheduled', value: formatSchedule(data.scheduledStart, data.durationMin) },
                          { label: "Elder's area", value: areaLong(data.district) },
                        ]
                      : [
                          { label: 'Service', value: data.service },
                          { label: 'Scheduled', value: formatSchedule(data.scheduledStart, data.durationMin) },
                          { label: 'Elder', value: data.elder },
                          { label: 'Language', value: data.language },
                          { label: 'Booked by', value: data.bookedBy },
                          ...(data.status === 'completed' && data.address
                            ? [{ label: 'Location', value: `${data.address.line1}, ${data.address.line2.split(' · ')[0]}` }]
                            : []),
                        ]
                  }
                />

                {(data.status === 'new' || data.status === 'accepted') && (
                  <>
                    <section className={styles.block} aria-labelledby="job-instructions-heading">
                      <SectionLabel id="job-instructions-heading">Special instructions</SectionLabel>
                      <p className={styles.instructions}>{data.instructions}</p>
                    </section>
                    <section className={styles.block} aria-labelledby="job-location-heading">
                      <SectionLabel id="job-location-heading">Location</SectionLabel>
                      {data.address ? (
                        <div className={styles.address}>
                          <div className={styles.addressText}>
                            <span className={styles.addressLine}>{data.address.line1}</span>
                            <span className={styles.addressSub}>{data.address.line2}</span>
                          </div>
                          <a
                            href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${data.address.line1}, ${data.address.line2.split(' · ')[0]}`)}`}
                            target="_blank"
                            rel="noreferrer"
                          >
                            Open in maps →
                          </a>
                        </div>
                      ) : (
                        <div className={styles.withheld}>
                          <LockIcon color="var(--text-muted)" />
                          <span>
                            {areaLong(data.district)}. <span className={styles.muted}>Full address is revealed after you accept.</span>
                          </span>
                        </div>
                      )}
                    </section>
                  </>
                )}
              </div>
              <JobTimeline job={data} />
            </div>

            {dialog === 'decline' && (
              <DeclineDialog
                job={data}
                busy={pending === 'decline'}
                onCancel={() => setDialog(null)}
                onDecline={(body) => void run({ op: 'decline', key: newIdempotencyKey(), body })}
              />
            )}
            {dialog === 'complete' && (
              <CompleteDialog
                job={data}
                busy={pending === 'complete'}
                onCancel={() => setDialog(null)}
                onComplete={(body) => void run({ op: 'complete', key: newIdempotencyKey(), body })}
              />
            )}
          </>
        )}
      </div>
    </WorkbenchShell>
  )
}
