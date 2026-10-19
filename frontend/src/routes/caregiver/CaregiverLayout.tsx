import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import type { KeyboardEvent, ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { signOut } from '../../features/auth/api'
import { ApiError } from '../../shared/api/client'
import { NotificationBell } from '../../shared/components/notifications/NotificationBell'
import styles from './Caregiver.module.css'
import layout from './CaregiverLayout.module.css'

/** `tab` is the shorter label the phone tab bar shows, where four columns share the width. */
type NavItem = { label: string; tab?: string; to: string; match: string[]; exact?: boolean; icon: ReactNode }
type NavGroup = { heading?: string; items: NavItem[] }

const iconProps = {
  width: 24, height: 24, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor',
  strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true,
} as const

const icons = {
  schedule: <svg {...iconProps}><rect x="4" y="5" width="16" height="15" rx="2" /><path d="M4 10h16M9 3v4M15 3v4" /></svg>,
  history: <svg {...iconProps}><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></svg>,
  reports: <svg {...iconProps}><path d="M7 3h7l4 4v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z" /><path d="M14 3v4h4M12 11v4M12 18h.01" /></svg>,
  alerts: <svg {...iconProps}><path d="M12 4 3 19h18z" /><path d="M12 10v4M12 17h.01" /></svg>,
  spotChecks: <svg {...iconProps}><path d="M12 3 5 6v5c0 4.5 3 8.3 7 10 4-1.7 7-5.5 7-10V6z" /><path d="m9 12 2 2 4-4" /></svg>,
  leave: <svg {...iconProps}><rect x="4" y="5" width="16" height="15" rx="2" /><path d="M4 10h16M9 3v4M15 3v4M10 13l4 4M14 13l-4 4" /></svg>,
  availability: <svg {...iconProps}><rect x="4" y="5" width="16" height="15" rx="2" /><path d="M4 10h16M9 3v4M15 3v4M9 15l2 2 4-4" /></svg>,
  preferences: <svg {...iconProps}><path d="M4 7h10M18 7h2M4 17h4M12 17h8" /><circle cx="16" cy="7" r="2" /><circle cx="10" cy="17" r="2" /></svg>,
  application: <svg {...iconProps}><path d="M7 3h10v18H7z" /><path d="M10 8h4M10 12h4M10 16h2" /></svg>,
  credentials: <svg {...iconProps}><circle cx="12" cy="9" r="5" /><path d="m9 13.5-1 7.5 4-2 4 2-1-7.5" /></svg>,
  menu: <svg {...iconProps}><path d="M4 7h16M4 12h16M4 17h16" /></svg>,
}

/**
 * Every caregiver section, grouped by what the caregiver is doing: working visits, arranging their
 * hours, or keeping their record current. Visit work is hidden until the caregiver is admitted.
 */
function sections(workAllowed: boolean, scheduleSearch: string): NavGroup[] {
  const groups: NavGroup[] = [
    { items: workAllowed ? [{ label: 'My schedule', tab: 'Schedule', to: '/caregiver' + scheduleSearch, match: ['/caregiver/visits'], exact: true, icon: icons.schedule }] : [] },
    {
      heading: 'Visits',
      items: workAllowed ? [
        { label: 'My history', to: '/caregiver/history', match: ['/caregiver/history'], icon: icons.history },
        { label: 'My reports', to: '/caregiver/incidents', match: ['/caregiver/incidents'], icon: icons.reports },
        { label: 'System alerts', to: '/caregiver/system-alerts', match: ['/caregiver/system-alerts'], icon: icons.alerts },
        { label: 'Spot checks', to: '/caregiver/spot-checks', match: ['/caregiver/spot-checks'], icon: icons.spotChecks },
      ] : [],
    },
    {
      heading: 'Your hours',
      items: [
        ...(workAllowed ? [{ label: 'My leave', tab: 'Leave', to: '/caregiver/absences', match: ['/caregiver/absences'], icon: icons.leave }] : []),
        { label: 'My availability', tab: 'Availability', to: '/caregiver/availability', match: ['/caregiver/availability'], icon: icons.availability },
        { label: 'Work preferences', to: '/caregiver/work-preferences', match: ['/caregiver/work-preferences'], icon: icons.preferences },
      ],
    },
    {
      heading: 'Your record',
      items: [
        { label: 'My application', tab: 'Application', to: '/caregiver/application', match: ['/caregiver/application'], icon: icons.application },
        { label: 'My credentials', tab: 'Credentials', to: '/caregiver/credentials', match: ['/caregiver/credentials'], icon: icons.credentials },
      ],
    },
  ]
  return groups.filter((group) => group.items.length > 0)
}

/** The phone tab bar shows the first three of these that the caregiver can open; Menu holds the rest. */
const phoneTabs = ['My schedule', 'My leave', 'My credentials', 'My application', 'My availability']

const desktopQuery = '(min-width: 900px)'
function subscribe(onChange: () => void) {
  if (typeof window.matchMedia !== 'function') return () => {}
  const query = window.matchMedia(desktopQuery)
  query.addEventListener('change', onChange)
  return () => query.removeEventListener('change', onChange)
}
function useIsDesktop() {
  return useSyncExternalStore(subscribe, () => typeof window.matchMedia === 'function' && window.matchMedia(desktopQuery).matches)
}

function initials(name: string) {
  return name.trim().split(/\s+/).slice(0, 2).map((part) => Array.from(part)[0]?.toUpperCase()).join('')
}

/**
 * The caregiver app's frame, matching the family app: on a phone, a bottom tab bar of the most-used
 * sections plus a Menu sheet holding every section and Sign out; from 900px, a side rail with every
 * section grouped and the caregiver's name and Sign out pinned to its foot.
 */
export default function CaregiverLayout({ workAllowed, name, children }: { workAllowed: boolean; name: string; children: ReactNode }) {
  const { pathname, search } = useLocation()
  const navigate = useNavigate()
  const desktop = useIsDesktop()
  // The schedule link returns to the date range last viewed on the schedule or a work pack.
  const [scheduleSearch, setScheduleSearch] = useState('')
  if (pathname === '/caregiver' || pathname.startsWith('/caregiver/visits/')) {
    const source = new URLSearchParams(search)
    const dates = new URLSearchParams()
    for (const key of ['dateFrom', 'dateTo']) if (source.has(key)) dates.set(key, source.get(key)!)
    const nextSearch = dates.size ? '?' + dates.toString() : ''
    if (nextSearch !== scheduleSearch) setScheduleSearch(nextSearch)
  }
  // The menu belongs to the page it was opened on, so moving to another page closes it.
  const [menuOpenOn, setMenuOpenOn] = useState<string | null>(null)
  const menuOpen = menuOpenOn === pathname + search
  const [signingOut, setSigningOut] = useState(false)
  const [signOutError, setSignOutError] = useState('')

  const groups = sections(workAllowed, scheduleSearch)
  const all = groups.flatMap((group) => group.items)
  const isActive = (item: NavItem) => (item.exact && pathname === '/caregiver')
    || item.match.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`))
  const tabs = phoneTabs.flatMap((label) => all.filter((item) => item.label === label)).slice(0, 3)
  const inMenuOnly = !tabs.some(isActive)

  useEffect(() => {
    window.scrollTo?.({ top: 0, left: 0, behavior: 'instant' })
  }, [pathname])

  async function handleSignOut() {
    if (signingOut) return
    setSigningOut(true)
    setSignOutError('')
    try {
      await signOut()
      navigate('/', { replace: true })
    } catch (error) {
      // An already-expired session means the caregiver is signed out anyway.
      if (error instanceof ApiError && error.status === 401) {
        navigate('/', { replace: true })
        return
      }
      setSignOutError('Unable to sign out. Please try again.')
    } finally {
      setSigningOut(false)
    }
  }

  const account = <div className={layout.account}>
    <span className={layout.avatar} aria-hidden="true">{initials(name)}</span>
    <span className={layout.accountText}>
      <span className={layout.accountName}>{name || 'Your account'}</span>
      <span className={layout.accountLabel}>Caregiver</span>
    </span>
    <button type="button" className={layout.signOut} onClick={handleSignOut} disabled={signingOut}>
      {signingOut ? 'Signing out…' : 'Sign out'}
    </button>
    {signOutError && <p role="alert" className={layout.signOutError}>{signOutError}</p>}
  </div>

  return <div className={`${styles.shell} ${layout.shell}`} data-desktop={desktop || undefined}>
    <a className={layout.skip} href="#caregiver-content">Skip to content</a>
    <NotificationBell floating />
    {desktop && <aside className={layout.rail}>
      <Link className={layout.wordmark} to={workAllowed ? '/caregiver' + scheduleSearch : '/caregiver/application'}>CareLink</Link>
      <nav className={layout.nav} aria-label="Caregiver navigation">
        {groups.map((group, index) => <div key={group.heading ?? index} className={layout.group}>
          {group.heading && <p className={layout.heading}>{group.heading}</p>}
          {group.items.map((item) => <Link key={item.label} to={item.to} aria-current={isActive(item) ? 'page' : undefined}>
            {item.icon}<span>{item.label}</span>
          </Link>)}
        </div>)}
      </nav>
      {account}
    </aside>}
    <main id="caregiver-content" className={layout.main}>{children}</main>
    {!desktop && <nav className={layout.tabBar} aria-label="Caregiver navigation">
      {tabs.map((tab) => <Link key={tab.label} to={tab.to} aria-current={isActive(tab) ? 'page' : undefined}>
        {tab.icon}<span className={layout.tabLabel}>{tab.tab ?? tab.label}</span>
      </Link>)}
      <button type="button" aria-haspopup="dialog" aria-expanded={menuOpen} data-current={inMenuOnly || undefined}
        onClick={() => setMenuOpenOn(pathname + search)}>
        {icons.menu}<span className={layout.tabLabel}>Menu</span>
      </button>
    </nav>}
    {!desktop && menuOpen && <MenuSheet groups={groups} isActive={isActive} account={account} onClose={() => setMenuOpenOn(null)} />}
  </div>
}

const FOCUSABLE = 'a[href], button:not([disabled])'

/**
 * Phone navigation's full list: a sheet rising from the bottom with every section under its group's
 * heading and the account with Sign out last. Focus stays in the sheet; Esc, the close button, the
 * backdrop or choosing a section closes it and focus returns to Menu.
 */
function MenuSheet({ groups, isActive, account, onClose }: {
  groups: NavGroup[]
  isActive: (item: NavItem) => boolean
  account: ReactNode
  onClose: () => void
}) {
  const sheet = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const current = sheet.current?.querySelector<HTMLElement>('[aria-current="page"]')
    ;(current ?? sheet.current?.querySelector<HTMLElement>(FOCUSABLE))?.focus()
    return () => {
      document.body.style.overflow = overflow
      opener?.focus?.()
    }
  }, [])

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.stopPropagation()
      onClose()
      return
    }
    if (event.key !== 'Tab') return
    const items = Array.from(sheet.current?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])
    const first = items[0]
    const last = items[items.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last?.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first?.focus()
    }
  }

  return <div className={layout.backdrop} onClick={(event) => event.target === event.currentTarget && onClose()}>
    <div ref={sheet} className={layout.sheet} role="dialog" aria-modal="true" aria-label="Menu" onKeyDown={onKeyDown}>
      <div className={layout.sheetHead}>
        <p className={layout.sheetTitle}>Menu</p>
        <button type="button" className={layout.close} aria-label="Close menu" onClick={onClose}>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
            strokeLinecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
        </button>
      </div>
      <nav aria-label="All caregiver pages">
        {groups.map((group, index) => <section key={group.heading ?? index} className={layout.sheetGroup}>
          {group.heading && <h2 className={layout.heading}>{group.heading}</h2>}
          {group.items.map((item) => <Link key={item.label} className={layout.row} to={item.to}
            aria-current={isActive(item) ? 'page' : undefined} onClick={onClose}>
            {item.icon}<span>{item.label}</span>
          </Link>)}
        </section>)}
      </nav>
      <section className={layout.sheetGroup}>{account}</section>
    </div>
  </div>
}
