import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Tag } from './Badge'
import styles from './AppHeader.module.css'

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const pad = (value: number) => String(value).padStart(2, '0')

/**
 * "Thu 28 Aug 2026 · 09:41". Built by hand rather than with Intl, whose en-GB output
 * varies by ICU version ("Sept") and inserts a comma after the weekday.
 */
function formatClock(date: Date): string {
  const day = `${WEEKDAYS[date.getDay()]} ${date.getDate()} ${MONTHS[date.getMonth()]} ${date.getFullYear()}`
  return `${day} · ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** The current time, re-read on each minute boundary so the displayed minute never lags. */
function useMinuteClock(): Date {
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    let interval: ReturnType<typeof setInterval> | undefined
    const timeout = setTimeout(
      () => {
        setNow(new Date())
        interval = setInterval(() => setNow(new Date()), 60_000)
      },
      60_000 - (Date.now() % 60_000),
    )
    return () => {
      clearTimeout(timeout)
      clearInterval(interval)
    }
  }, [])

  return now
}

function LiveClock() {
  return <>Today · {formatClock(useMinuteClock())}</>
}

export type AppHeaderUserInfo = { name: string; roleLabel: string }

/** Signed-in user's name and role badge ("CARE MGR"), for pages that compose their own `trailing`. */
export function AppHeaderUser({ name, roleLabel }: AppHeaderUserInfo) {
  return (
    <span className={styles.user}>
      <span className={styles.userName}>{name}</span>
      <Tag compact>{roleLabel}</Tag>
    </span>
  )
}

/**
 * Top bar for every role's client; only the user's `roleLabel` differs between them.
 * `contextLine` defaults to a live "Today · <date> · <time>" clock; a page can replace it
 * with a breadcrumb. `trailing` replaces the user block with page-specific status (which
 * can include an AppHeaderUser of its own). `notifications` (the bell) stays whatever `trailing` is.
 */
export function AppHeader({
  product = 'CareLink',
  contextLine,
  user,
  trailing,
  notifications,
  onLogout,
}: {
  product?: string
  contextLine?: ReactNode
  /** Undefined while the session is still loading. */
  user?: AppHeaderUserInfo
  trailing?: ReactNode
  notifications?: ReactNode
  onLogout: () => void
}) {
  return (
    <header className={styles.header}>
      <div className={styles.left}>
        <span className={styles.product}>{product}</span>
        <span className={styles.context}>{contextLine ?? <LiveClock />}</span>
      </div>
      <div className={styles.right}>
        {notifications}
        {trailing ?? (user && <AppHeaderUser {...user} />)}
        <button type="button" className={styles.logout} onClick={onLogout}>
          Log out
        </button>
      </div>
    </header>
  )
}
