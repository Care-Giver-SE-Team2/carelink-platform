import { useEffect, useRef } from 'react'
import type { KeyboardEvent } from 'react'
import { Link } from 'react-router-dom'
import { NavCount } from './FamilySideRail'
import type { RailGroup, RailItem } from './FamilySideRail'
import styles from './FamilyMenuSheet.module.css'

const FOCUSABLE = 'a[href], button:not([disabled])'

/**
 * Phone navigation's full list: a sheet rising from the bottom with every section under its group's
 * heading, waiting counts included, and Account last. Focus moves into the sheet and stays there;
 * Esc, the close button, the backdrop or choosing a section closes it and focus returns to Menu.
 */
export function FamilyMenuSheet({ groups, account, isActive, onClose }: {
  groups: RailGroup[]
  account: RailItem
  isActive: (item: RailItem) => boolean
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

  const row = (item: RailItem) => <Link key={item.to} className={styles.row} to={item.to}
    aria-current={isActive(item) ? 'page' : undefined} onClick={onClose}>
    {item.icon}<span className={styles.rowLabel}>{item.label}</span><NavCount count={item.count} />
  </Link>

  return <div className={styles.backdrop} onClick={(event) => event.target === event.currentTarget && onClose()}>
    <div ref={sheet} className={styles.sheet} role="dialog" aria-modal="true" aria-label="Menu" onKeyDown={onKeyDown}>
      <div className={styles.head}>
        <p className={styles.title}>Menu</p>
        <button type="button" className={styles.close} aria-label="Close menu" onClick={onClose}>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
            strokeLinecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
        </button>
      </div>
      <nav aria-label="All family pages">
        {groups.map((group, index) => <section key={group.heading ?? index} className={styles.group}>
          {group.heading && <h2 className={styles.heading}>{group.heading}</h2>}
          {group.items.map(row)}
        </section>)}
        <section className={styles.group}>{row(account)}</section>
      </nav>
    </div>
  </div>
}
