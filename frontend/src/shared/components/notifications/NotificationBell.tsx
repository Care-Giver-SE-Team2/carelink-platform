import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { isRead, linkFor, portalOf, validId, when } from '../../../features/notifications/presentation'
import { NOTIFICATIONS_CHANGED_EVENT } from '../../../features/notifications/api'
import type { NotificationItem } from '../../../features/notifications/types'
import { useNotificationsSource } from './notificationsSource'
import type { NotificationsSource } from './notificationsSource'
import styles from './NotificationBell.module.css'

/** The contract's polling pace while a screen stays open, and the longest it backs off to after failures. */
const POLL_MS = 15_000
const MAX_BACKOFF_MS = 120_000
const PAGE_SIZE = 20

/**
 * The in-app inbox for whoever is signed in, in any client's header: a bell with the unread
 * count, and a list that opens under it. Reads are shown only after a successful server receipt.
 * Family incident messages go to their detail screen, which checks access and renders before
 * recording the notification read. Other messages keep their existing destination.
 *
 * `floating` pins it to the top-right corner of the window, for a client with no shared header
 * (the family app, whose pages each draw their own).
 *
 * @author Wang Ziyu
 */
export function NotificationBell({ floating = false }: { floating?: boolean }) {
  const source = useNotificationsSource()
  return source ? <Bell source={source} floating={floating} /> : null
}

function Bell({ source, floating }: { source: NotificationsSource; floating: boolean }) {
  const navigate = useNavigate()
  const portal = portalOf(useLocation().pathname)
  const [unread, setUnread] = useState(0)
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<NotificationItem[] | null>(null)
  const [failed, setFailed] = useState(false)
  const [readFailure, setReadFailure] = useState<NotificationItem | 'all' | 'invalid' | null>(null)
  const [reading, setReading] = useState(false)
  const [revision, setRevision] = useState(0)
  const writing = useRef<AbortController | null>(null)
  const root = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    let delay = POLL_MS
    const poll = () => {
      source.unreadCount(controller.signal).then(
        (count) => {
          if (controller.signal.aborted) return
          setUnread(count)
          delay = POLL_MS
        },
        () => {
          delay = Math.min(delay * 2, MAX_BACKOFF_MS)
        },
      ).finally(() => {
        if (!controller.signal.aborted) timer = setTimeout(poll, delay)
      })
    }
    poll()
    return () => {
      controller.abort()
      clearTimeout(timer)
    }
  }, [source, revision])

  const load = useCallback(
    async (signal?: AbortSignal) => {
      try {
        const inbox = await source.inbox(0, PAGE_SIZE, signal)
        if (signal?.aborted) return
        setItems(inbox.items)
        setFailed(false)
        const count = await source.unreadCount(signal)
        if (!signal?.aborted) setUnread(count)
      } catch (error) {
        if (!signal?.aborted && !(error instanceof DOMException && error.name === 'AbortError')) {
          setFailed(true)
        }
      }
    },
    [source],
  )

  /** The list request in flight, cancelled when the list closes or a newer one starts. */
  const pending = useRef<AbortController | null>(null)

  const fetchList = useCallback(() => {
    pending.current?.abort()
    const controller = new AbortController()
    pending.current = controller
    void load(controller.signal)
  }, [load])

  useEffect(() => () => { pending.current?.abort(); writing.current?.abort() }, [source])

  useEffect(() => {
    const refresh = () => { setRevision((value) => value + 1); if (open) fetchList() }
    window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, refresh)
    return () => window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, refresh)
  }, [open, fetchList])

  useEffect(() => {
    if (!open) return undefined
    const close = () => {
      pending.current?.abort()
      setOpen(false)
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close()
    }
    const onPointer = (event: MouseEvent) => {
      if (root.current && !root.current.contains(event.target as Node)) close()
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('mousedown', onPointer)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('mousedown', onPointer)
    }
  }, [open])

  async function choose(item: NotificationItem) {
    if (writing.current) return
    const link = linkFor(item, portal)
    if (portal === 'family' && item.resourceType === 'INCIDENT') {
      // The destination checks access and renders before making its independent read/view writes.
      if (!link || !validId(item.id)) { setReadFailure('invalid'); return }
      pending.current?.abort()
      setOpen(false)
      navigate(link, { state: { familyIncidentNotification: { id: item.id, incidentId: item.resourceId } } })
      return
    }
    if (!isRead(item)) {
      const controller = new AbortController()
      writing.current = controller
      pending.current?.abort()
      setReading(true)
      setReadFailure(null)
      try {
        const receipt = await source.markRead(item.id, controller.signal)
        if (controller.signal.aborted) return
        if (receipt.id !== item.id || receipt.status !== 'READ') throw new Error('Read was not confirmed')
        setItems((current) => current?.map((each) => each.id === item.id ? receipt : each) ?? null)
        setUnread((count) => Math.max(0, count - 1))
        setRevision((value) => value + 1)
      } catch {
        if (!controller.signal.aborted) setReadFailure(item)
        return
      } finally {
        if (writing.current === controller) { writing.current = null; setReading(false) }
      }
      if (controller.signal.aborted) return
    }
    if (link) {
      pending.current?.abort()
      setOpen(false)
      navigate(link)
    }
  }

  function toggle() {
    if (open) {
      pending.current?.abort()
      setOpen(false)
      return
    }
    setFailed(false)
    setReadFailure(null)
    setOpen(true)
    fetchList()
  }

  function retry() {
    setFailed(false)
    fetchList()
  }

  async function readAll() {
    if (writing.current) return
    const controller = new AbortController()
    writing.current = controller
    pending.current?.abort()
    setReading(true)
    setReadFailure(null)
    try {
      await source.markAllRead(controller.signal)
      if (controller.signal.aborted) return
      setItems((current) => current?.map((each) => ({ ...each, status: 'READ' as const })) ?? null)
      setUnread(0)
      setRevision((value) => value + 1)
    } catch {
      if (!controller.signal.aborted) setReadFailure('all')
    } finally {
      if (writing.current === controller) { writing.current = null; setReading(false) }
    }
  }

  return (
    <div ref={root} className={floating ? `${styles.root} ${styles.floating}` : styles.root}>
      <button
        type="button"
        className={styles.bell}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
        onClick={toggle}
      >
        <BellIcon />
        {unread > 0 && (
          <span className={styles.badge} aria-hidden="true">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>
      {open && (
        <div className={styles.panel} role="dialog" aria-label="Notifications">
          <div className={styles.panelHead}>
            <span className={styles.panelTitle}>Notifications</span>
            <button type="button" className={styles.textButton} disabled={unread === 0 || reading} onClick={() => void readAll()}>
              Mark all as read
            </button>
          </div>
          {readFailure && <p className={styles.note} role="alert">
            {readFailure === 'invalid' ? 'This incident notification has an invalid link.' : 'Your notifications could not be marked as read.'}{' '}
            {readFailure !== 'invalid' && <button type="button" className={styles.textButton} disabled={reading}
              onClick={() => { if (readFailure === 'all') void readAll(); else void choose(readFailure) }}>Try again</button>}
          </p>}
          <PanelBody items={items} failed={failed} disabled={reading} onRetry={retry} onChoose={(item) => void choose(item)} />
        </div>
      )}
    </div>
  )
}

function PanelBody({
  items,
  failed,
  disabled,
  onRetry,
  onChoose,
}: {
  items: NotificationItem[] | null
  failed: boolean
  disabled: boolean
  onRetry: () => void
  onChoose: (item: NotificationItem) => void
}) {
  if (failed) {
    return (
      <p className={styles.note} role="alert">
        Your notifications could not be loaded.{' '}
        <button type="button" className={styles.textButton} onClick={onRetry}>
          Try again
        </button>
      </p>
    )
  }
  if (items === null) {
    return <p className={styles.note}>Loading…</p>
  }
  if (items.length === 0) {
    return <p className={styles.note}>Nothing yet. Messages about visits, changes and checks appear here.</p>
  }
  return (
    <ul className={styles.list}>
      {items.map((item) => (
        <li key={item.id}>
          <button
            type="button"
            className={isRead(item) ? styles.item : `${styles.item} ${styles.unread}`}
            disabled={disabled}
            onClick={() => onChoose(item)}
          >
            <span className={styles.itemTitle}>
              {!isRead(item) && <span className={styles.srOnly}>Unread: </span>}
              {item.title}
            </span>
            {item.body && <span className={styles.itemBody}>{item.body}</span>}
            <span className={styles.itemTime}>{when(item.createdAt)}</span>
          </button>
        </li>
      ))}
    </ul>
  )
}

function BellIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path
        d="M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15L6 16z"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <path d="M10 20.5a2 2 0 0 0 4 0" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  )
}
