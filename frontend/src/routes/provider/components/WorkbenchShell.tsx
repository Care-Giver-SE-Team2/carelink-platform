import { useState } from 'react'
import type { MouseEvent, ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { cx } from '../../../shared/components/ui/cx'
import { getIntegrationHealth } from '../../../features/provider-platform/api'
import type { IntegrationHealth } from '../../../features/provider-platform/types'
import { useWorkbench } from '../workbenchContext'
import { Button, Dialog, LinkButton, LockIcon } from './ui'
import styles from './WorkbenchShell.module.css'

const NAV = [
  { label: 'Apply', to: '/provider/apply', open: true },
  { label: 'Status', to: '/provider/status', open: true },
  { label: 'API credentials', to: '/provider/credentials', open: false },
  { label: 'Webhooks', to: '/provider/webhooks', open: false },
  { label: 'Sandbox', to: '/provider/sandbox', open: false },
  { label: 'Docs', to: '/provider/docs', open: true },
]

/**
 * Frame for every developer-portal page: the top bar with Back to the CareLink home page and,
 * once signed in, the provider, how its webhooks are doing and Sign out; and one nav that is a left rail on a desktop and a wrapping row above the page on a
 * phone. API credentials, Webhooks and Sandbox stay locked until the application is approved.
 * While a page has set a leave guard, following a nav link asks first. `dense` is the tighter
 * top padding of the job detail page.
 */
export function WorkbenchShell({ children, dense = false }: { children: ReactNode; dense?: boolean }) {
  const { approved, application, portal, signOut, leaveGuard, setLeaveGuard } = useWorkbench()
  const navigate = useNavigate()
  const [leavingTo, setLeavingTo] = useState<string | null>(null)

  const health = useQuery({
    queryKey: ['provider', 'health', portal?.token],
    queryFn: () => getIntegrationHealth(portal!),
    enabled: portal !== null,
    refetchInterval: 30_000,
    retry: false,
  })

  function guard(e: MouseEvent<HTMLAnchorElement>, to: string) {
    if (!leaveGuard) return
    e.preventDefault()
    setLeavingTo(to)
  }

  const items = NAV.map((item) => ({ ...item, locked: !item.open && !approved }))

  const nav = items.map((item) =>
    item.locked ? (
      <span key={item.to} className={cx(styles.item, styles.locked)} title="Available after approval" aria-disabled="true">
        <span>{item.label}</span>
        <span className={styles.lock}>
          <LockIcon />
        </span>
      </span>
    ) : (
      <NavLink
        key={item.to}
        to={item.to}
        onClick={(e) => guard(e, item.to)}
        className={({ isActive }) => cx(styles.item, isActive && styles.active)}
      >
        {item.label}
      </NavLink>
    ),
  )

  return (
    <div className={styles.shell}>
      <header className={styles.bar}>
        <div className={styles.brand}>
          <Link className={styles.back} to="/" onClick={(e) => guard(e, '/')} aria-label="Back to the CareLink home page">
            <span aria-hidden="true">←</span>
            <span className={styles.backLabel}>Back</span>
          </Link>
          <span className={styles.backDivider} aria-hidden="true" />
          <span className={styles.wordmark}>CareLink</span>
          <span className={styles.product}>
            <span className={styles.productLong}>Partner Portal</span>
            <span className={styles.productShort}>Portal</span>
          </span>
        </div>
        {application && (
          <div className={styles.status}>
            <span className={styles.org}>
              {application.organisationName}
              <span className={styles.providerTag}>PROVIDER</span>
              <span className={styles.divider} aria-hidden="true" />
            </span>
            {portal && health.data && (
              <>
                <HealthIndicator health={health.data} />
                <span className={styles.divider} aria-hidden="true" />
              </>
            )}
            <LinkButton
              className={styles.signOut}
              onClick={() => {
                signOut()
                navigate('/provider/status')
              }}
            >
              Sign out
            </LinkButton>
          </div>
        )}
      </header>

      <div className={styles.body}>
        <nav className={styles.rail} aria-label="Partner Portal">
          {nav}
          {!approved && <p className={styles.railNote}>API credentials, Webhooks and Sandbox unlock once CareLink approves your application.</p>}
        </nav>
        <main className={cx(styles.main, dense && styles.dense)}>{children}</main>
      </div>

      {leavingTo && (
        <Dialog
          title="Leave this page?"
          onClose={() => setLeavingTo(null)}
          footer={
            <>
              <Button onClick={() => setLeavingTo(null)}>Stay</Button>
              <Button
                variant="danger"
                onClick={() => {
                  const to = leavingTo
                  setLeavingTo(null)
                  setLeaveGuard(null)
                  navigate(to)
                }}
              >
                Leave anyway
              </Button>
            </>
          }
        >
          <p className={styles.dialogText}>{leaveGuard}</p>
        </Dialog>
      )}
    </div>
  )
}

/** Suspended, then failed deliveries, then ones still retrying, else all well. */
function HealthIndicator({ health }: { health: IntegrationHealth }) {
  const [tone, long, short] = health.suspended
    ? [styles.failed, 'SUSPENDED', 'SUSPENDED']
    : health.failed > 0
      ? [styles.failed, `${health.failed} WEBHOOK${health.failed === 1 ? '' : 'S'} FAILED`, `${health.failed} FAILED`]
      : health.retrying > 0
        ? [styles.warning, `${health.retrying} WEBHOOK${health.retrying === 1 ? '' : 'S'} RETRYING`, `${health.retrying} RETRYING`]
        : [styles.connected, 'WEBHOOKS OK', 'WEBHOOKS OK']
  return (
    <span className={cx(styles.indicator, tone)} title="Webhook deliveries in the last 7 days">
      <span className={styles.dot} aria-hidden="true" />
      {long === short ? (
        long
      ) : (
        <>
          <span className={styles.errorLong}>{long}</span>
          <span className={styles.errorShort}>{short}</span>
        </>
      )}
    </span>
  )
}
