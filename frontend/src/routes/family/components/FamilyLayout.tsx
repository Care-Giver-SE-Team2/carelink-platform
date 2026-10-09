import { useEffect } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { NotificationBell } from '../../../shared/components/notifications/NotificationBell'
import styles from '../intake/FamilyIntake.module.css'
import { useSelectedElder } from './selectedElder'
import { FamilySideRail } from './FamilySideRail'
import type { RailItem } from './FamilySideRail'
import { useIsDesktop } from './useIsDesktop'
import layout from './FamilyLayout.module.css'

const iconProps = {
  width: 24, height: 24, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor',
  strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true,
} as const

/**
 * Global tabs, Account last. Pages reached from a tab (visit progress, a report, an application) keep
 * it active. The phone tab bar has room for five, so `phone: false` sections appear only on the rail.
 */
const tabs: (RailItem & { phone?: false })[] = [
  {
    label: 'Home', to: '/family/home', match: ['/family/home'],
    icon: <svg {...iconProps}><path d="M4 11 12 4l8 7v9a1 1 0 0 1-1 1h-4v-6h-6v6H5a1 1 0 0 1-1-1z" /></svg>,
  },
  {
    label: 'Schedule', to: '/family/schedule', match: ['/family/schedule', '/family/visits'],
    icon: <svg {...iconProps}><rect x="4" y="5" width="16" height="15" rx="2" /><path d="M4 10h16M9 3v4M15 3v4" /></svg>,
  },
  {
    label: 'Visit changes', to: '/family/changes', match: ['/family/changes'], phone: false,
    icon: <svg {...iconProps}><path d="M4 9h13l-3-3M20 15H7l3 3" /></svg>,
  },
  {
    label: 'Spot checks', to: '/family/spot-checks', match: ['/family/spot-checks'], phone: false,
    icon: <svg {...iconProps}><path d="M12 3 5 6v5c0 4.5 3 8.3 7 10 4-1.7 7-5.5 7-10V6z" /><path d="m9 12 2 2 4-4" /></svg>,
  },
  {
    label: 'Reports', to: '/family/reports/weekly', match: ['/family/reports'],
    icon: <svg {...iconProps}><path d="M7 3h7l4 4v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z" /><path d="M14 3v4h4M9 13h6M9 17h6" /></svg>,
  },
  {
    label: 'Services', to: '/family/service-applications', match: ['/family/service-applications', '/family/intake'],
    icon: <svg {...iconProps}><circle cx="12" cy="12" r="8.5" /><path d="M12 8v8M8 12h8" /></svg>,
  },
  {
    label: 'Extra services', to: '/family/extra-services', match: ['/family/extra-services'], phone: false,
    icon: <svg {...iconProps}><path d="M5 12h14M12 5v14" /><circle cx="12" cy="12" r="9" /></svg>,
  },
  {
    label: 'Caregiver reviews', to: '/family/caregiver-reviews', match: ['/family/caregiver-reviews'], phone: false,
    icon: <svg {...iconProps}><path d="M7 4h10v16H7z" /><path d="M9 9h6M9 13h6M9 17h4" /></svg>,
  },
  {
    label: 'My elders', to: '/family/elders', match: ['/family/elders'], phone: false,
    icon: <svg {...iconProps}><circle cx="12" cy="8" r="4" /><path d="M4 21c0-5 3-8 8-8s8 3 8 8" /></svg>,
  },
  {
    label: 'Family bindings', to: '/family/family-bindings', match: ['/family/family-bindings'], phone: false,
    icon: <svg {...iconProps}><circle cx="8" cy="9" r="3" /><circle cx="17" cy="9" r="3" /><path d="M2 20c0-4 3-6 6-6s6 2 6 6M12 20c0-3 2-5 5-5s5 2 5 5" /></svg>,
  },
  {
    label: 'Account', to: '/family/account', match: ['/family/account'],
    icon: <svg {...iconProps}><circle cx="12" cy="8.5" r="3.8" /><path d="M4.5 20c1.2-3.6 4-5.4 7.5-5.4s6.3 1.8 7.5 5.4" /></svg>,
  },
]

/**
 * Displays the current page with the family app's navigation: the bottom tab bar on a phone, the
 * side rail (Account pinned to its foot) from 900px.
 * @param title Sets the browser page title
 * @author Wang Zhili
 */
export function FamilyLayout({ title = 'My applications' }: { title?: string }) {
  const { pathname, search } = useLocation()
  const desktop = useIsDesktop()
  const { elderId } = useSelectedElder()
  const isActive = (tab: RailItem) => tab.match.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`))
  // Reports keep their elder in the URL, so the tab opens the elder currently followed.
  const links = tabs.map((tab) => tab.label === 'Reports' && elderId !== null
    ? { ...tab, to: `${tab.to}?elderId=${elderId}` } : tab)
  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
  }, [pathname, search])
  useEffect(() => {
    const previous = document.title
    document.title = `${title} · CareLink`
    return () => {
      document.title = previous
    }
  }, [title])

  return (
    <div className={`${styles.portal} ${layout.shell}`} data-desktop={desktop || undefined}>
      <a className={styles.skip} href="#family-content">
        Skip to content
      </a>
      <NotificationBell floating />
      {desktop && <FamilySideRail items={links.slice(0, -1)} account={links[links.length - 1]} isActive={isActive} />}
      <main id="family-content" className={styles.main}>
        <Outlet />
      </main>
      {!desktop && <nav className={layout.tabBar} aria-label="Family pages">
        {links.filter((tab) => tab.phone !== false).map((tab) => (
          <Link key={tab.label} to={tab.to} aria-current={isActive(tab) ? 'page' : undefined}>
            {tab.icon}
            {tab.label}
          </Link>
        ))}
      </nav>}
    </div>
  )
}
