import type { ReactNode } from 'react'
import { Legend, ViewToggle } from '../../../shared/components/ui'
import type { LegendItem } from '../../../shared/components/ui'
import styles from './RosterToolbar.module.css'

export type RosterView = 'day' | 'week'

const VIEWS = [
  { value: 'day' as const, label: 'DAY' },
  { value: 'week' as const, label: 'WEEK' },
]

const LEGEND: LegendItem[] = [
  { label: 'assigned', swatch: 'assigned' },
  { label: 'closed', swatch: 'closed' },
  { label: 'exception', swatch: 'exception' },
]

/**
 * The bar above the roster: whatever the page puts on the left (date navigation; an
 * absence notice and its re-roster action once those exist), the DAY / WEEK toggle and the
 * colour key on the right.
 */
export function RosterToolbar({
  view,
  onViewChange,
  children,
}: {
  view: RosterView
  onViewChange: (view: RosterView) => void
  children?: ReactNode
}) {
  return (
    <div className={styles.toolbar}>
      {children}
      <div className={styles.right}>
        <ViewToggle label="Roster view" options={VIEWS} value={view} onChange={onViewChange} />
        <span className={styles.divider} aria-hidden="true" />
        <Legend items={LEGEND} />
      </div>
    </div>
  )
}
