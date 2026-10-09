import type { VisitBlockState } from '../../../shared/components/ui'
import type { CaregiverOption } from '../../../shared/api/profile'
import type { VisitResponse } from '../../../shared/api/visit'
import type { ElderRow } from './elders'

/**
 * Roster tab data — the Day timeline and Week grid, shaped from the same endpoints as the
 * Today board: GET /api/visits/roster per day, named via GET /api/elders and
 * GET /api/caregivers. Visits come from published care plans (UC-MG03). It shows each
 * caregiver's schedule as it stands: a visit with no caregiver is nobody's schedule and is
 * left out, and re-rostering for an absence happens on the Absences screen. Approved leave
 * (GET /api/absences) only marks the days a caregiver is away.
 *
 * Dates are ISO "yyyy-MM-dd" strings in Singapore time, the same wall clock the visits use.
 */

export const DAY_START_HOUR = 8
export const DAY_END_HOUR = 20
export const CAP_HOURS_PER_DAY = 8
export const CAP_HOURS_PER_WEEK = 40
/** Caregiver rows per page. */
export const ROSTER_PAGE_SIZE = 10
/** Minutes a visit is counted for when it has no scheduled end. */
const DEFAULT_MINUTES = 60

export type RosterBlock = {
  id: string
  elderShort: string
  label: string
  state: VisitBlockState
  /** Hour the visit starts in, e.g. 9 for 09:30. */
  hour: number
  /** Minutes past midnight the visit starts at, e.g. 570 for 09:30. */
  startMinute: number
  minutes: number
  title: string
}

/** One caregiver's row. */
export type RosterRow = {
  id: string
  name: string
  subLine: string
}

/** Approved leave, as the roster needs it: who is away, which days, and which absence to open. */
export type RosterAbsence = { id: number; caregiverId: number; startDate: string; endDate: string }

/** `leave` is the caregiver's approved absence covering the day, if any. */
export type TimelineRow = RosterRow & { blocks: RosterBlock[]; leave: RosterAbsence | null }

export type DayTimeline = { rows: TimelineRow[]; startHour: number; endHour: number }

export type WeekDay = { date: string; label: string; isToday: boolean; isPast: boolean }

/** One caregiver's day; `leave` is their approved absence covering it, if any. */
export type DayLoad = { visits: number; hours: number; exceptions: number; leave: RosterAbsence | null }

export type WeekRow = RosterRow & { days: DayLoad[]; totalHours: number }

// ---------------------------------------------------------------------------------------
// Dates

const DAY_NAMES = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

function toUtc(date: string): Date {
  return new Date(`${date}T00:00:00Z`)
}

function fromUtc(day: Date): string {
  return day.toISOString().slice(0, 10)
}

/** Today's date in Singapore, whatever the browser's zone. */
export function singaporeToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Singapore' }).format(now)
}

export function addDays(date: string, days: number): string {
  const day = toUtc(date)
  day.setUTCDate(day.getUTCDate() + days)
  return fromUtc(day)
}

/** The Monday of the week the date falls in. */
export function mondayOf(date: string): string {
  const weekday = toUtc(date).getUTCDay()
  return addDays(date, weekday === 0 ? -6 : 1 - weekday)
}

/** ISO-8601 week number. */
export function isoWeek(date: string): number {
  const thursday = toUtc(addDays(mondayOf(date), 3))
  const firstOfYear = Date.UTC(thursday.getUTCFullYear(), 0, 1)
  return Math.floor((thursday.getTime() - firstOfYear) / 86_400_000 / 7) + 1
}

function dayAndMonth(date: string): string {
  const day = toUtc(date)
  return `${day.getUTCDate()} ${MONTH_NAMES[day.getUTCMonth()]}`
}

/** "Thu 28 Aug" */
export function dayContext(date: string): string {
  return `${DAY_NAMES[toUtc(date).getUTCDay()]} ${dayAndMonth(date)}`
}

/** "Week 35 · 25–31 Aug", or "Week 40 · 28 Sep–4 Oct" across a month end. */
export function weekContext(date: string): string {
  const monday = mondayOf(date)
  const sunday = addDays(monday, 6)
  const sameMonth = monday.slice(5, 7) === sunday.slice(5, 7)
  const range = sameMonth
    ? `${toUtc(monday).getUTCDate()}–${dayAndMonth(sunday)}`
    : `${dayAndMonth(monday)}–${dayAndMonth(sunday)}`
  return `Week ${isoWeek(date)} · ${range}`
}

/** Monday to Sunday of the date's week, today marked and the days before it flagged past. */
export function weekDays(date: string, today: string): WeekDay[] {
  const monday = mondayOf(date)
  return Array.from({ length: 7 }, (_, i) => {
    const day = addDays(monday, i)
    return {
      date: day,
      label: `${DAY_NAMES[toUtc(day).getUTCDay()]} ${toUtc(day).getUTCDate()}`,
      isToday: day === today,
      isPast: day < today,
    }
  })
}

// ---------------------------------------------------------------------------------------
// Names

/** "Tan Hock Seng" → "Tan H.S."; a one-word name is left whole. */
export function elderShort(fullName: string): string {
  const [family, ...given] = fullName.trim().split(/\s+/)
  if (given.length === 0) return family
  return `${family} ${given.map((part) => `${part[0].toUpperCase()}.`).join('')}`
}

/** "S45 · Malay, English" — whichever parts are known. */
function caregiverSubLine(caregiver: CaregiverOption): string {
  const languages = (caregiver.dialects ?? '')
    .split(',')
    .map((part) => part.trim())
    .filter(Boolean)
    .join(', ')
  return [caregiver.sector, languages].filter(Boolean).join(' · ')
}

// ---------------------------------------------------------------------------------------
// Leave

/** The absences that keep somebody away on `date`, both ends of an absence included. */
export function absencesOn(absences: RosterAbsence[], date: string): RosterAbsence[] {
  return absences.filter((absence) => absence.startDate <= date && date <= absence.endDate)
}

function leaveOf(absences: RosterAbsence[], rowId: string): RosterAbsence | null {
  return absences.find((absence) => String(absence.caregiverId) === rowId) ?? null
}

// ---------------------------------------------------------------------------------------
// Visits

/** The visits somebody has: the roster is caregivers' schedules, so unassigned ones are left out. */
function assigned(visits: VisitResponse[]): VisitResponse[] {
  return visits.filter((visit) => visit.caregiverId != null)
}

function minutesOf(visit: VisitResponse): number {
  if (!visit.scheduledEnd) return DEFAULT_MINUTES
  const minutes = (Date.parse(`${visit.scheduledEnd}Z`) - Date.parse(`${visit.scheduledStart}Z`)) / 60_000
  return minutes > 0 ? minutes : DEFAULT_MINUTES
}

function blockState(visit: VisitResponse): VisitBlockState {
  switch (visit.status) {
    case 'COMPLETED':
    case 'VERIFIED':
    case 'AUTO_CLOSED':
      return 'closed'
    case 'EXCEPTION':
      return 'exception'
    default:
      return 'assigned'
  }
}

/**
 * Who gets a row: every caregiver who can take visits, plus anyone else (onboarding,
 * inactive) who still has one in the range. Caregivers with an exception in the range come
 * first, so they land on page 1; then by name.
 */
function rosterRows(visits: VisitResponse[], caregivers: CaregiverOption[]): RosterRow[] {
  const withVisits = new Set(visits.map((visit) => visit.caregiverId as number))
  const withExceptions = new Set(
    visits.filter((visit) => visit.status === 'EXCEPTION').map((visit) => visit.caregiverId),
  )
  const rows: RosterRow[] = caregivers
    .filter((caregiver) => caregiver.assignable || withVisits.has(caregiver.id))
    .sort(
      (a, b) =>
        Number(withExceptions.has(b.id)) - Number(withExceptions.has(a.id)) || a.fullName.localeCompare(b.fullName),
    )
    .map((caregiver) => ({ id: String(caregiver.id), name: caregiver.fullName, subLine: caregiverSubLine(caregiver) }))
  const known = new Set(rows.map((row) => row.id))
  for (const id of withVisits) {
    if (!known.has(String(id))) {
      rows.push({ id: String(id), name: `Caregiver #${id}`, subLine: '' })
      known.add(String(id))
    }
  }
  return rows
}

function toBlock(visit: VisitResponse, elderNames: Map<string, string>): RosterBlock {
  const elderName = elderNames.get(String(visit.elderId)) ?? `Elder #${visit.elderId}`
  const time = visit.scheduledStart.slice(11, 16)
  const service = visit.serviceType ?? 'visit'
  const minutes = minutesOf(visit)
  return {
    id: String(visit.id),
    elderShort: elderShort(elderName),
    label: visit.status === 'EXCEPTION' ? 'exception' : service,
    state: blockState(visit),
    hour: Number(visit.scheduledStart.slice(11, 13)),
    startMinute: Number(visit.scheduledStart.slice(11, 13)) * 60 + Number(visit.scheduledStart.slice(14, 16)),
    minutes,
    title: `${time} · ${elderName} · ${service} · ${minutes} min`,
  }
}

/**
 * One day as caregiver rows of time-placed blocks, widened past 08–20 if a visit falls
 * outside. `absences` are the ones covering this day (see absencesOn).
 */
export function toDayTimeline(
  visits: VisitResponse[],
  caregivers: CaregiverOption[],
  elders: ElderRow[],
  absences: RosterAbsence[] = [],
): DayTimeline {
  const elderNames = new Map(elders.map((elder) => [elder.id, elder.name]))
  const mine = assigned(visits)
  const blocks = mine.map((visit) => ({ rowId: String(visit.caregiverId), block: toBlock(visit, elderNames) }))

  const startHour = Math.min(DAY_START_HOUR, ...blocks.map(({ block }) => block.hour))
  const endHour = Math.max(
    DAY_END_HOUR,
    ...blocks.map(({ block }) => Math.ceil((block.startMinute + block.minutes) / 60)),
  )
  const rows = rosterRows(mine, caregivers).map((row) => ({
    ...row,
    blocks: blocks.filter(({ rowId }) => rowId === row.id).map(({ block }) => block),
    leave: leaveOf(absences, row.id),
  }))
  return { rows, startHour, endHour }
}

/**
 * One week as caregiver rows of per-day load: visits, hours, exceptions and leave.
 * `absencesByDay` lines up with `visitsByDay`: the absences covering each day.
 */
export function toWeek(
  visitsByDay: VisitResponse[][],
  caregivers: CaregiverOption[],
  absencesByDay: RosterAbsence[][] = [],
): WeekRow[] {
  const byDay = visitsByDay.map(assigned)
  return rosterRows(byDay.flat(), caregivers).map((row) => {
    const days = byDay.map((visits, i) => {
      const mine = visits.filter((visit) => String(visit.caregiverId) === row.id)
      return {
        visits: mine.length,
        hours: mine.reduce((sum, visit) => sum + minutesOf(visit), 0) / 60,
        exceptions: mine.filter((visit) => visit.status === 'EXCEPTION').length,
        leave: leaveOf(absencesByDay[i] ?? [], row.id),
      }
    })
    return { ...row, days, totalHours: days.reduce((sum, day) => sum + day.hours, 0) }
  })
}

/** The caregiver rows whose name contains `query`, ignoring case and surrounding spaces. */
export function matchingName<T extends RosterRow>(rows: T[], query: string): T[] {
  const needle = query.trim().toLowerCase()
  return needle ? rows.filter((row) => row.name.toLowerCase().includes(needle)) : rows
}

/** One page of caregiver rows; `page` is clamped to the pages that exist. */
export function pageOf<T extends RosterRow>(
  rows: T[],
  page: number,
  pageSize: number = ROSTER_PAGE_SIZE,
): { rows: T[]; page: number; total: number } {
  const lastPage = Math.max(1, Math.ceil(rows.length / pageSize))
  const current = Math.min(Math.max(1, page), lastPage)
  const start = (current - 1) * pageSize
  return { rows: rows.slice(start, start + pageSize), page: current, total: rows.length }
}
