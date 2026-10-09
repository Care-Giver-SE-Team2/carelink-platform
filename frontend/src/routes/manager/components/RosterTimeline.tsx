import type { CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import { Tag, VisitBlock } from '../../../shared/components/ui'
import type { DayTimeline, TimelineRow } from '../data/roster'
import styles from './RosterTimeline.module.css'

const cx = (...names: (string | false | undefined)[]) => names.filter(Boolean).join(' ')

const LABEL_COLUMN = 1
/** Quarter-hour columns, so back-to-back visits on a half hour sit side by side. */
const SLOTS_PER_HOUR = 4
const SLOT_MINUTES = 60 / SLOTS_PER_HOUR

/**
 * One day as a caregiver × hour grid, so cover can be judged by sight. A visit is placed by
 * the quarter hour it starts in and spans the quarter hours it runs, so a 10:30 visit starts
 * halfway across 10 and only visits that really overlap fall onto a second line.
 * A caregiver on approved leave has their row hatched and an "On leave" tag that opens the
 * absence, where any re-rostering is done.
 */
export function RosterTimeline({ timeline }: { timeline: DayTimeline }) {
  const { rows, startHour, endHour } = timeline
  const hours = Array.from({ length: endHour - startHour }, (_, i) => startHour + i)
  const slots = hours.length * SLOTS_PER_HOUR
  const grid = { '--slots': slots } as CSSProperties

  return (
    <div className={styles.timeline} role="table" aria-label="Visits by caregiver and hour">
      <div className={styles.row} style={grid} role="row">
        <div role="columnheader" className={styles.corner}>
          <span className={styles.visuallyHidden}>Caregiver</span>
        </div>
        {hours.map((hour) => (
          <div key={hour} role="columnheader" className={styles.hour} style={{ gridColumn: `span ${SLOTS_PER_HOUR}` }}>
            {String(hour).padStart(2, '0')}
          </div>
        ))}
      </div>
      {rows.map((row) => (
        <TimelineRowView key={row.id} row={row} startHour={startHour} slots={slots} grid={grid} />
      ))}
    </div>
  )
}

/** One caregiver's row: who it is, any leave, and the day's visits. */
function TimelineRowView({
  row,
  startHour,
  slots,
  grid,
}: {
  row: TimelineRow
  startHour: number
  slots: number
  grid: CSSProperties
}) {
  return (
    <div className={cx(styles.row, styles.body, row.leave !== null && styles.onLeave)} style={grid} role="row">
      <div role="rowheader" className={styles.label}>
        <div className={styles.name}>{row.name}</div>
        <div className={styles.sub}>{row.subLine}</div>
        {row.leave && (
          <Link to={`/manager/absences/${row.leave.id}`} className={styles.leave} title="On approved leave. Open the absence.">
            <Tag tone="muted" compact>
              On leave
            </Tag>
          </Link>
        )}
      </div>
      {row.blocks.map((block) => {
        const offset = Math.floor((block.startMinute - startHour * 60) / SLOT_MINUTES)
        const column = Math.min(Math.max(0, offset), slots - 1) + LABEL_COLUMN + 1
        const span = Math.min(Math.max(1, Math.round(block.minutes / SLOT_MINUTES)), slots + LABEL_COLUMN + 1 - column)
        return (
          <VisitBlock
            key={block.id}
            elderShort={block.elderShort}
            label={block.label}
            state={block.state}
            startCol={column}
            span={span}
            slotsPerHour={SLOTS_PER_HOUR}
            title={block.title}
          />
        )
      })}
    </div>
  )
}
