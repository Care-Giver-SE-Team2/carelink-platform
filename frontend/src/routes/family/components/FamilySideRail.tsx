import type { ReactNode } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useFamilyElders, useFamilyMe } from '../../../features/family-account/useFamilyAccount'
import { ageOn, singaporeToday } from '../../../features/schedule/presentation'
import { useSelectedElder } from './selectedElder'
import screen from './FamilyScreen.module.css'
import styles from './FamilySideRail.module.css'

/** One section; `count` is how many things in it wait on the family's answer. */
export type RailItem = { label: string; to: string; match: string[]; icon: ReactNode; count?: number }
export type RailGroup = { heading?: string; items: RailItem[] }

/** How many things wait on the family, as a pill; nothing when none do. Screen readers hear "3 waiting". */
export function NavCount({ count }: { count?: number }) {
  if (!count) return null
  return <span className={styles.count}><span aria-hidden="true">{count}</span><span className={screen.visuallyHidden}>, {count} waiting</span></span>
}

function initials(name: string) {
  return name.trim().split(/\s+/).slice(0, 2).map((part) => Array.from(part)[0]?.toUpperCase()).join('')
}

/**
 * Desktop navigation (≥ 900px): wordmark, the elder being followed, every section under its group's
 * heading, and the signed-in family member's Account pinned to the bottom.
 */
export function FamilySideRail({ groups, account, isActive }: {
  groups: RailGroup[]
  account: RailItem
  isActive: (item: RailItem) => boolean
}) {
  const me = useFamilyMe()
  const elders = useFamilyElders()
  const { elderId, setElderId } = useSelectedElder()
  const { pathname } = useLocation()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const list = elders.data ?? []
  const elder = list.find((item) => item.id === elderId) ?? list[0]
  const age = elder ? ageOn(elder.dateOfBirth, singaporeToday()) : null
  const name = me.data?.displayName.trim() || me.data?.username || ''

  function follow(id: number) {
    setElderId(id)
    // Report pages keep their elder in the URL, so switching there reloads that page for the new elder.
    if (pathname.startsWith('/family/reports')) {
      const next = new URLSearchParams()
      next.set('elderId', String(id))
      const week = params.get('weekStart')
      if (week && pathname === '/family/reports/weekly') next.set('weekStart', week)
      navigate(`${pathname === '/family/reports/weekly' ? pathname : '/family/reports'}?${next}`)
    }
  }

  return <aside className={styles.rail}>
    <Link className={styles.wordmark} to="/family/home">CareLink</Link>
    {elder && <div className={styles.elder}>
      <div>
        <p className={styles.label}>Following</p>
        <p className={styles.elderName}>{elder.fullName}</p>
        <p className={styles.elderMeta}>{[age === null ? null : `Age ${age}`, elder.sector].filter(Boolean).join(' · ') || 'Linked elder'}</p>
      </div>
      {list.length > 1 && <>
        <span className={styles.chevron} aria-hidden="true">⌄</span>
        <select className={styles.picker} aria-label="Following" value={elder.id}
          onChange={(event) => follow(Number(event.target.value))}>
          {list.map((item) => <option key={item.id} value={item.id}>{item.fullName}</option>)}
        </select>
      </>}
    </div>}
    <nav className={styles.nav} aria-label="Family pages">
      {groups.map((group, index) => <div key={group.heading ?? index} className={styles.group}>
        {group.heading && <p className={styles.heading}>{group.heading}</p>}
        {group.items.map((item) => <Link key={item.to} to={item.to} aria-current={isActive(item) ? 'page' : undefined}>
          {item.icon}<span className={styles.itemLabel}>{item.label}</span><NavCount count={item.count} />
        </Link>)}
      </div>)}
    </nav>
    <Link className={styles.account} to={account.to} aria-current={isActive(account) ? 'page' : undefined}>
      <span className={styles.avatar} aria-hidden="true">{name ? initials(name) : ''}</span>
      <span>
        <span className={styles.accountName}>{name || 'Your account'}</span>
        <span className={styles.accountLabel}>Account</span>
      </span>
    </Link>
  </aside>
}
