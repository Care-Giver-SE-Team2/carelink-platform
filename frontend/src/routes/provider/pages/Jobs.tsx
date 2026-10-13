import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { listJobs } from '../../../features/provider-platform/api'
import { ApiError } from '../../../features/provider-platform/errors'
import { areaShort, formatClock, formatDayTime, formatShort } from '../../../features/provider-platform/format'
import type { JobTab } from '../../../features/provider-platform/types'
import { cx } from '../../../shared/components/ui/cx'
import { SandboxConnect } from '../components/SandboxConnect'
import { Banner, Button, RefreshIcon, StateTag } from '../components/ui'
import { WorkbenchShell } from '../components/WorkbenchShell'
import { useWorkbench } from '../workbenchContext'
import styles from './Jobs.module.css'

const TABS: { tab: JobTab; label: string; short: string }[] = [
  { tab: 'new', label: 'New', short: 'New' },
  { tab: 'accepted', label: 'Accepted', short: 'Accepted' },
  { tab: 'completed', label: 'Completed', short: 'Done' },
  { tab: 'closed', label: 'Declined / Cancelled', short: 'Declined' },
]

const POLL_MS = 30_000

/**
 * Sandbox (PT04): the job API as the provider's server uses it, tried by hand. Get a token with
 * the client credentials, then see every job CareLink dispatched, by state, newest first, and
 * open one to accept or complete it. A real integration does this from its own server, with
 * jobs arriving by webhook; here the list polls every 30 seconds and Refresh asks at once. The
 * elder's area is the district only — the address comes with acceptance. The tab and page live
 * in the URL (?tab=accepted&page=2).
 */
export default function Jobs() {
  const { token, expireToken, application } = useWorkbench()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const tab = (TABS.find((t) => t.tab === params.get('tab'))?.tab ?? 'new') as JobTab
  const page = Math.max(1, Number(params.get('page')) || 1)
  const [refreshing, setRefreshing] = useState(false)

  const jobs = useQuery({
    queryKey: ['provider', 'jobs', token?.accessToken, tab, page],
    queryFn: () => listJobs(token!.accessToken, tab, page),
    enabled: token !== null,
    refetchInterval: POLL_MS,
    retry: false,
  })

  const unauthorised = jobs.error instanceof ApiError && jobs.error.status === 401
  useEffect(() => {
    if (unauthorised) expireToken()
  }, [unauthorised, expireToken])

  async function refresh() {
    setRefreshing(true)
    try {
      await jobs.refetch()
    } finally {
      setRefreshing(false)
    }
  }

  const go = (next: { tab?: JobTab; page?: number }) =>
    setParams({ tab: next.tab ?? tab, page: String(next.page ?? 1) }, { replace: true })

  const data = jobs.data
  const first = data && data.total > 0 ? (data.page - 1) * data.pageSize + 1 : 0
  const last = data ? Math.min(data.page * data.pageSize, data.total) : 0
  const everything = data ? Object.values(data.counts).reduce((a, b) => a + b, 0) : 0

  return (
    <WorkbenchShell>
      <div className={styles.page}>
        <div className={styles.head}>
          <div className={styles.intro}>
            <h1 className={styles.title}>Sandbox</h1>
            <p className={styles.sub}>
              Try the job API the way your server will. In production your server makes these calls, with jobs arriving by webhook:
              never a browser.
            </p>
          </div>
          {token && (
            <div className={styles.refresh}>
              {jobs.dataUpdatedAt > 0 && <span className={styles.refreshed}>Last refreshed {formatClock(jobs.dataUpdatedAt)}</span>}
              <Button size="small" onClick={() => void refresh()} busy={refreshing && 'Refreshing…'}>
                <RefreshIcon />
                Refresh
              </Button>
            </div>
          )}
        </div>

        <SandboxConnect />

        {!token ? null : (
          <>
            <div className={styles.tabs} role="tablist" aria-label="Job states">
              {TABS.map((t) => (
                <button
                  key={t.tab}
                  type="button"
                  role="tab"
                  aria-selected={t.tab === tab}
                  className={cx(styles.tab, t.tab === tab && styles.tabActive)}
                  onClick={() => go({ tab: t.tab })}
                >
                  <span className={styles.tabLong}>{t.label}</span>
                  <span className={styles.tabShort}>{t.short}</span>
                  {data && (data.counts[t.tab] > 0 || t.tab !== 'new') && (
                    <span className={cx(styles.count, t.tab === 'new' && styles.countNew)}>{data.counts[t.tab]}</span>
                  )}
                </button>
              ))}
            </div>

            <div role="tabpanel" aria-label={TABS.find((t) => t.tab === tab)!.label}>
              {jobs.isError && !unauthorised ? (
                <Banner
                  tone="danger"
                  title="Couldn't reach CareLink"
                  role="alert"
                  action={
                    <Button size="small" onClick={() => void refresh()}>
                      Retry
                    </Button>
                  }
                >
                  The job list didn't load.{' '}
                  {jobs.error instanceof ApiError && <span className={styles.requestId}>{jobs.error.requestId}</span>}
                </Banner>
              ) : refreshing || jobs.isPending ? (
                <div className={styles.skeleton} aria-label="Loading jobs" role="status">
                  {[0, 1, 2].map((row) => (
                    <div key={row} className={styles.skeletonRow}>
                      {[0, 1, 2, 3, 4, 5].map((cell) => (
                        <span key={cell} />
                      ))}
                    </div>
                  ))}
                </div>
              ) : data && data.items.length === 0 ? (
                <div className={styles.empty}>
                  <div className={styles.emptyTitle}>{everything === 0 ? 'No jobs yet.' : tab === 'new' ? 'No new jobs.' : 'Nothing here yet.'}</div>
                  {tab === 'new' && (
                    <>
                      <div className={styles.emptyBody}>You'll be notified by webhook when one is dispatched.</div>
                      {application?.webhookUrl && <div className={styles.listening}>Listening at {application.webhookUrl}</div>}
                    </>
                  )}
                </div>
              ) : (
                data && (
                  <>
                    <table className={styles.table}>
                      <thead>
                        <tr>
                          <th scope="col">Job ID</th>
                          <th scope="col">Service</th>
                          <th scope="col">Elder's area</th>
                          <th scope="col">Scheduled</th>
                          <th scope="col">Status</th>
                          <th scope="col">Received</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.items.map((job) => (
                          <tr key={job.id} onClick={() => navigate(`/provider/sandbox/jobs/${job.id}`)}>
                            <td className={styles.id}>
                              <Link to={`/provider/sandbox/jobs/${job.id}`} onClick={(e) => e.stopPropagation()}>
                                {job.id}
                              </Link>
                            </td>
                            <td className={styles.service}>{job.service}</td>
                            <td className={styles.area}>{areaShort(job.district)}</td>
                            <td className={styles.when}>{formatDayTime(job.scheduledStart)}</td>
                            <td className={styles.status}>
                              <StateTag kind={job.status} />
                            </td>
                            <td className={styles.received}>
                              <span className={styles.receivedLabel}>Received </span>
                              {formatShort(job.receivedAt)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    {data.total > data.pageSize && (
                      <div className={styles.pagination}>
                        <span className={styles.showing}>
                          <span className={styles.showingWord}>Showing </span>
                          {first}–{last} of {data.total}
                        </span>
                        <div className={styles.pages}>
                          <Button size="small" disabled={data.page <= 1} onClick={() => go({ page: data.page - 1 })}>
                            ← Previous
                          </Button>
                          <Button size="small" disabled={last >= data.total} onClick={() => go({ page: data.page + 1 })}>
                            Next →
                          </Button>
                        </div>
                      </div>
                    )}
                  </>
                )
              )}
            </div>
          </>
        )}
      </div>
    </WorkbenchShell>
  )
}
