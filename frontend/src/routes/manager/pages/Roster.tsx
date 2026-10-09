import { useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAbsences } from '../../../features/absences/useAbsenceQueries'
import { Button, Pagination, RowTitle, SearchField } from '../../../shared/components/ui'
import { ManagerShell } from '../components/ManagerShell'
import { RosterTimeline } from '../components/RosterTimeline'
import { RosterToolbar } from '../components/RosterToolbar'
import type { RosterView } from '../components/RosterToolbar'
import { WeekGrid } from '../components/WeekGrid'
import {
  CAP_HOURS_PER_DAY,
  ROSTER_PAGE_SIZE,
  absencesOn,
  addDays,
  dayContext,
  isoWeek,
  matchingName,
  pageOf,
  singaporeToday,
  toDayTimeline,
  toWeek,
  weekContext,
  weekDays,
} from '../data/roster'
import type { RosterAbsence } from '../data/roster'
import { useCaregivers } from '../lib/useCaregivers'
import { useElders } from '../lib/useElders'
import { useDayVisits, useWeekVisits } from '../lib/useRoster'
import styles from './Roster.module.css'

/**
 * Roster — MG03: the visits published care plans have put on the calendar, by caregiver. Day
 * shows one day hour by hour; Week shows each caregiver's daily load against the caps, and a
 * cell opens that day. It is each caregiver's schedule as it stands: visits nobody has are
 * left out, and re-rostering for an absence is done on the Absences screen. A caregiver on
 * approved leave is marked on the days they are away, linking to the absence. Caregivers are
 * shown a page at a time, and Day and Week share the page. A search narrows the rows to the
 * caregivers whose name matches, in both views. The view, date, page and search live in the
 * URL (?view=week&date=2026-10-05&page=2&q=ong), so a reload or a shared link lands on the
 * same roster. The header keeps the live clock, as on the other screens; the day or week being
 * looked at is named in the toolbar, beside the controls that change it.
 */
export default function Roster() {
  const [params, setParams] = useSearchParams()
  const today = singaporeToday()
  const view: RosterView = params.get('view') === 'week' ? 'week' : 'day'
  const date = /^\d{4}-\d{2}-\d{2}$/.test(params.get('date') ?? '') ? (params.get('date') as string) : today
  const page = Math.max(1, Number(params.get('page')) || 1)
  const query = params.get('q') ?? ''
  const go = (next: { view?: RosterView; date?: string; page?: number; query?: string }, replace = false) => {
    const q = next.query ?? query
    setParams(
      { view: next.view ?? view, date: next.date ?? date, page: String(next.page ?? page), ...(q ? { q } : {}) },
      { replace },
    )
  }
  const step = view === 'week' ? 7 : 1
  const unit = view === 'week' ? 'week' : 'day'

  return (
    <ManagerShell>
      <RosterToolbar view={view} onViewChange={(next) => go({ view: next })}>
        <div className={styles.dateNav}>
          <Button aria-label={`Previous ${unit}`} onClick={() => go({ date: addDays(date, -step) })}>
            ‹
          </Button>
          <Button onClick={() => go({ date: today })} disabled={date === today}>
            Today
          </Button>
          <Button aria-label={`Next ${unit}`} onClick={() => go({ date: addDays(date, step) })}>
            ›
          </Button>
          <span className={styles.dateLabel} aria-live="polite">
            <RowTitle>{view === 'week' ? weekContext(date) : dayContext(date)}</RowTitle>
          </span>
        </div>
        <SearchField
          className={styles.search}
          placeholder="Search caregivers by name"
          value={query}
          // A new search starts from the first page; typing replaces the history entry
          // rather than adding one per keystroke.
          onChange={(next) => go({ query: next, page: 1 }, true)}
        />
      </RosterToolbar>
      {view === 'week' ? (
        <WeekView
          date={date}
          today={today}
          page={page}
          query={query}
          onPageChange={(next) => go({ page: next })}
          onOpenDay={(day) => go({ view: 'day', date: day })}
        />
      ) : (
        <DayView date={date} page={page} query={query} onPageChange={(next) => go({ page: next })} />
      )}
    </ManagerShell>
  )
}

type Paging = { page: number; query: string; onPageChange: (page: number) => void }

const NO_LEAVE: RosterAbsence[] = []

/**
 * Approved leave, for marking who is away. The roster is still drawn without it: if the
 * absences cannot be read, nobody is marked rather than the whole roster failing.
 */
function useApprovedLeave(): RosterAbsence[] {
  const { data } = useAbsences('APPROVED')
  return data ?? NO_LEAVE
}

function DayView({ date, page, query, onPageChange }: { date: string } & Paging) {
  const visits = useDayVisits(date)
  const caregivers = useCaregivers()
  const elders = useElders()
  const leave = useApprovedLeave()
  const timeline = useMemo(
    () =>
      visits.data && caregivers.data && elders.data
        ? toDayTimeline(visits.data, caregivers.data, elders.data, absencesOn(leave, date))
        : null,
    [visits.data, caregivers.data, elders.data, leave, date],
  )

  if (visits.isError || caregivers.isError || elders.isError) {
    return <p className={styles.status}>Could not load the roster for this day.</p>
  }
  if (!timeline) return <p className={styles.status}>Loading the roster…</p>
  if (timeline.rows.length === 0) return <p className={styles.status}>No caregivers or visits on this day.</p>
  const matching = matchingName(timeline.rows, query)
  if (matching.length === 0 && query.trim()) return <NoMatch query={query} />
  const shown = pageOf(matching, page)
  return (
    <>
      <RosterTimeline timeline={{ ...timeline, rows: shown.rows }} />
      <div className={styles.dayPagination}>
        <Pagination page={shown.page} pageSize={ROSTER_PAGE_SIZE} total={shown.total} noun="caregivers" onPageChange={onPageChange} />
      </div>
    </>
  )
}

function WeekView({
  date,
  today,
  page,
  query,
  onPageChange,
  onOpenDay,
}: { date: string; today: string; onOpenDay: (date: string) => void } & Paging) {
  const days = useMemo(() => weekDays(date, today), [date, today])
  const visits = useWeekVisits(days.map((day) => day.date))
  const caregivers = useCaregivers()
  const leave = useApprovedLeave()
  const rows = useMemo(
    () =>
      visits.data && caregivers.data
        ? toWeek(visits.data, caregivers.data, days.map((day) => absencesOn(leave, day.date)))
        : null,
    [visits.data, caregivers.data, leave, days],
  )

  if (visits.isError || caregivers.isError) return <p className={styles.status}>Could not load the roster for this week.</p>
  if (!rows) return <p className={styles.status}>Loading the roster…</p>
  const matching = matchingName(rows, query)
  if (matching.length === 0 && query.trim()) return <NoMatch query={query} />
  const shown = pageOf(matching, page)
  return (
    <WeekGrid
      days={days}
      rows={shown.rows}
      onOpenDay={onOpenDay}
      pagination={
        <Pagination page={shown.page} pageSize={ROSTER_PAGE_SIZE} total={shown.total} noun="caregivers" onPageChange={onPageChange} />
      }
      footer={`Week ${isoWeek(date)} · bars show hours against the ${CAP_HOURS_PER_DAY} h daily cap (green = completed days, black = scheduled, red = over cap) · hatched = on approved leave · click a cell to open that day in Day view`}
    />
  )
}

function NoMatch({ query }: { query: string }) {
  return <p className={styles.status}>No caregivers match “{query.trim()}”.</p>
}
