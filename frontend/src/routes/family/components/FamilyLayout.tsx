import { useEffect, useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { NotificationBell } from '../../../shared/components/notifications/NotificationBell'
import styles from '../intake/FamilyIntake.module.css'
import { useSelectedElder } from './selectedElder'
import { FamilyMenuSheet } from './FamilyMenuSheet'
import { FamilySideRail, NavCount } from './FamilySideRail'
import type { RailGroup, RailItem } from './FamilySideRail'
import { usePendingDecisions } from './usePendingDecisions'
import { useIsDesktop } from './useIsDesktop'
import layout from './FamilyLayout.module.css'

const iconProps = {
  width: 24, height: 24, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor',
  strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true,
} as const

const icons = {
  home: <svg {...iconProps}><path d="M4 11 12 4l8 7v9a1 1 0 0 1-1 1h-4v-6h-6v6H5a1 1 0 0 1-1-1z" /></svg>,
  schedule: <svg {...iconProps}><rect x="4" y="5" width="16" height="15" rx="2" /><path d="M4 10h16M9 3v4M15 3v4" /></svg>,
  carePlan: <svg {...iconProps}><path d="M9 4h6v3H9z" /><path d="M7 5H5v16h14V5h-2" /><path d="m9 13 2 2 4-4" /></svg>,
  reports: <svg {...iconProps}><path d="M7 3h7l4 4v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z" /><path d="M14 3v4h4M9 13h6M9 17h6" /></svg>,
  changes: <svg {...iconProps}><path d="M4 9h13l-3-3M20 15H7l3 3" /></svg>,
  spotChecks: <svg {...iconProps}><path d="M12 3 5 6v5c0 4.5 3 8.3 7 10 4-1.7 7-5.5 7-10V6z" /><path d="m9 12 2 2 4-4" /></svg>,
  requests: <svg {...iconProps}><path d="M5 12h14M12 5v14" /><circle cx="12" cy="12" r="9" /></svg>,
  applications: <svg {...iconProps}><path d="M7 3h10v18H7z" /><path d="M10 8h4M10 12h4M10 16h2" /></svg>,
  review: <svg {...iconProps}><path d="m12 4 2.4 4.9 5.4.8-3.9 3.8.9 5.4-4.8-2.5-4.8 2.5.9-5.4-3.9-3.8 5.4-.8z" /></svg>,
  account: <svg {...iconProps}><circle cx="12" cy="8.5" r="3.8" /><path d="M4.5 20c1.2-3.6 4-5.4 7.5-5.4s6.3 1.8 7.5 5.4" /></svg>,
  menu: <svg {...iconProps}><path d="M4 7h16M4 12h16M4 17h16" /></svg>,
}

/**
 * Every family section, grouped by what the family member is doing: following the care, answering
 * the care team, or managing the service itself. Pages reached from a section (visit progress, a
 * report, an application) keep it active. Account sits apart: the rail pins it to its foot and the
 * phone menu lists it last; it also covers the linked elders and bindings it links to.
 */
function sections(pending: ReturnType<typeof usePendingDecisions>): RailGroup[] {
  return [
    { items: [{ label: 'Home', to: '/family/home', match: ['/family/home'], icon: icons.home }] },
    {
      heading: 'Care',
      items: [
        { label: 'Schedule', to: '/family/schedule', match: ['/family/schedule', '/family/visits'], icon: icons.schedule },
        { label: 'Care plan', to: '/family/care-plan', match: ['/family/care-plan'], icon: icons.carePlan },
        { label: 'Reports', to: '/family/reports/weekly', match: ['/family/reports', '/family/incidents'], icon: icons.reports },
      ],
    },
    {
      heading: 'Needs your answer',
      items: [
        { label: 'Visit changes', to: '/family/changes', match: ['/family/changes'], icon: icons.changes, count: pending.changes.length },
        { label: 'Spot checks', to: '/family/spot-checks', match: ['/family/spot-checks'], icon: icons.spotChecks, count: pending.spotChecks.length },
        { label: 'Extra services', to: '/family/extra-services', match: ['/family/extra-services'], icon: icons.requests, count: pending.requests.length },
      ],
    },
    {
      heading: 'Your service',
      items: [
        { label: 'Care applications', to: '/family/service-applications', match: ['/family/service-applications', '/family/intake'], icon: icons.applications },
        { label: 'Caregiver review', to: '/family/caregiver-reviews', match: ['/family/caregiver-reviews'], icon: icons.review },
      ],
    },
  ]
}

const account: RailItem = {
  label: 'Account', to: '/family/account', match: ['/family/account', '/family/elders', '/family/family-bindings'], icon: icons.account,
}

/** The phone tab bar's most-used sections, by label; the Menu beside them holds every section. */
const phoneTabs = ['Home', 'Schedule', 'Reports']

/**
 * Displays the current page with the family app's navigation: on a phone, a bottom tab bar of the
 * three most-used sections plus a Menu sheet holding every section; from 900px, the side rail with
 * every section grouped and Account pinned to its foot. Sections waiting on the family show a count.
 * @param title Sets the browser page title
 * @author Wang Zhili
 */
export function FamilyLayout({ title = 'My applications' }: { title?: string }) {
  const { pathname, search } = useLocation()
  const desktop = useIsDesktop()
  const { elderId } = useSelectedElder()
  const pending = usePendingDecisions()
  // The menu belongs to the page it was opened on, so moving to another page closes it.
  const [menuOpenOn, setMenuOpenOn] = useState<string | null>(null)
  const menuOpen = menuOpenOn === pathname + search
  const isActive = (item: RailItem) => item.match.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`))
  // Reports keep their elder in the URL, so the section opens the elder currently followed.
  const groups = sections(pending).map((group) => ({
    ...group,
    items: group.items.map((item) => item.label === 'Reports' && elderId !== null
      ? { ...item, to: `${item.to}?elderId=${elderId}` } : item),
  }))
  const all = groups.flatMap((group) => group.items)
  const tabs = all.filter((item) => phoneTabs.includes(item.label))
  // On a section only the Menu lists, the Menu button shows as the current tab.
  const inMenuOnly = !tabs.some(isActive)
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
      {desktop && <FamilySideRail groups={groups} account={account} isActive={isActive} />}
      <main id="family-content" className={styles.main}>
        <Outlet />
      </main>
      {!desktop && <nav className={layout.tabBar} aria-label="Family pages">
        {tabs.map((tab) => (
          <Link key={tab.label} to={tab.to} aria-current={isActive(tab) ? 'page' : undefined}>
            {tab.icon}
            <span className={layout.tabLabel}>{tab.label}</span>
          </Link>
        ))}
        <button type="button" aria-haspopup="dialog" aria-expanded={menuOpen} data-current={inMenuOnly || undefined}
          aria-label={pending.total ? `Menu, ${pending.total} waiting` : undefined} onClick={() => setMenuOpenOn(pathname + search)}>
          <span className={layout.menuIcon}>{icons.menu}<NavCount count={pending.total} /></span>
          <span className={layout.tabLabel}>Menu</span>
        </button>
      </nav>}
      {!desktop && menuOpen && <FamilyMenuSheet groups={groups} account={account} isActive={isActive}
        onClose={() => setMenuOpenOn(null)} />}
    </div>
  )
}
