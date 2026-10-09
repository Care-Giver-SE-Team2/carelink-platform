import type { FamilyVisitStatus } from './types'

const singapore = 'Asia/Singapore'

// Complete Monday-to-Sunday weeks within the API's 1000-01-01 to 9999-12-30 range.
export const scheduleDateBounds = { min: '1000-01-06', max: '9999-12-26' } as const

export const visitStatusLabels: Record<FamilyVisitStatus, string> = {
  SCHEDULED: 'Scheduled',
  ARRIVED: 'Arrived',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  VERIFIED: 'Verified',
  AUTO_CLOSED: 'Automatically closed',
  EXCEPTION: 'Exception',
  CANCELLED: 'Cancelled',
}

/**
 * Gets today's calendar date in Singapore, regardless of the browser's timezone.
 * @param now Current instant
 * @return Date in YYYY-MM-DD format
 * @author Wang Zhili
 */
export function singaporeToday(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-SG', {
    timeZone: singapore,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now)
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value
  return `${part('year')}-${part('month')}-${part('day')}`
}

/**
 * Checks that a real calendar date belongs to a complete supported schedule week.
 * @param value Date entered in YYYY-MM-DD format
 * @return Whether the date and its whole week can be queried
 * @author Wang Zhili
 */
export function isScheduleDate(value: string): boolean {
  if (value < scheduleDateBounds.min || value > scheduleDateBounds.max) return false
  try {
    calendarDate(value)
    return true
  } catch {
    return false
  }
}

/**
 * Finds the Monday containing a calendar date.
 * @param date Date in YYYY-MM-DD format
 * @return Monday in YYYY-MM-DD format
 * @author Wang Zhili
 */
export function weekStart(date: string): string {
  const weekday = calendarDate(date).getUTCDay()
  return shiftDays(date, -((weekday + 6) % 7))
}

/**
 * Moves a calendar date without applying browser timezone or daylight saving rules.
 * @param date Date in YYYY-MM-DD format
 * @param days Number of calendar days to move
 * @return Shifted date in YYYY-MM-DD format
 * @throws RangeError If the shifted date cannot use a four-digit year
 * @author Wang Zhili
 */
export function shiftDays(date: string, days: number): string {
  const result = calendarDate(date)
  result.setUTCDate(result.getUTCDate() + days)
  if (result.getUTCFullYear() < 0 || result.getUTCFullYear() > 9999) {
    throw new RangeError('Shifted date must have a four-digit year')
  }
  return result.toISOString().slice(0, 10)
}

/**
 * Labels a Monday-to-Sunday calendar range, including both years when needed.
 * @param monday Week's first date in YYYY-MM-DD format
 * @return Readable weekly date range
 * @author Wang Zhili
 */
export function weekLabel(monday: string): string {
  const start = calendarDate(monday)
  const end = calendarDate(shiftDays(monday, 6))
  const startLabel = new Intl.DateTimeFormat('en-SG', {
    day: 'numeric',
    month: 'short',
    year: start.getUTCFullYear() === end.getUTCFullYear() ? undefined : 'numeric',
    timeZone: 'UTC',
  }).format(start)
  const endLabel = new Intl.DateTimeFormat('en-SG', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(end)
  return `${startLabel} – ${endLabel}`
}

/**
 * Formats a visit's calendar day in Singapore.
 * @param timestamp API timestamp with a UTC offset
 * @return Readable date or an unavailable placeholder
 * @author Wang Zhili
 */
export function visitDate(timestamp: string): string {
  return formatTimestamp(timestamp, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })
}

/**
 * Whole years between a date of birth and a calendar date.
 * @param dateOfBirth Date in YYYY-MM-DD format, or null when not recorded
 * @param today Date in YYYY-MM-DD format, usually today in Singapore
 * @return Age in years, or null when it cannot be worked out
 */
export function ageOn(dateOfBirth: string | null, today: string): number | null {
  if (!dateOfBirth) return null
  const [by, bm, bd] = dateOfBirth.split('-').map(Number)
  const [ty, tm, td] = today.split('-').map(Number)
  const age = ty - by - (tm < bm || (tm === bm && td < bd) ? 1 : 0)
  return Number.isFinite(age) && age >= 0 ? age : null
}

/**
 * Short weekday of a visit in Singapore, for compact day columns.
 * @param timestamp API timestamp with a UTC offset
 * @return Weekday such as Mon, or an unavailable placeholder
 */
export function visitWeekday(timestamp: string): string {
  return formatTimestamp(timestamp, { weekday: 'short' })
}

/**
 * Day and month of a visit in Singapore, for compact day columns.
 * @param timestamp API timestamp with a UTC offset
 * @return Date such as 5 Oct, or an unavailable placeholder
 */
export function visitDayMonth(timestamp: string): string {
  return formatTimestamp(timestamp, { day: 'numeric', month: 'short' })
}

/**
 * Formats a visit time in Singapore using the 24-hour clock.
 * @param timestamp API timestamp with a UTC offset
 * @return Hour and minute or an unavailable placeholder
 * @author Wang Zhili
 */
export function visitTime(timestamp: string): string {
  return formatTimestamp(timestamp, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
}

/**
 * Labels known services and makes other service names readable.
 * @param raw Service type returned by the API
 * @return A service label, including a fallback when no type was recorded
 * @author Wang Zhili
 */
export function serviceLabel(raw: string | null): string {
  const service = raw?.trim()
  if (!service) return 'Care visit'
  if (service === 'BATHING') return 'Bathing assistance'
  if (service === 'VITALS') return 'Vital signs monitoring'
  const readable = service.replace(/[_-]+/g, ' ').replace(/\s+/g, ' ').toLowerCase()
  return readable.charAt(0).toUpperCase() + readable.slice(1)
}

function calendarDate(value: string): Date {
  const date = new Date(`${value}T00:00:00Z`)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== value) {
    throw new RangeError('Expected a valid calendar date in YYYY-MM-DD format')
  }
  return date
}

function formatTimestamp(timestamp: string, options: Intl.DateTimeFormatOptions): string {
  const date = new Date(timestamp)
  return Number.isNaN(date.getTime())
    ? 'Not available'
    : new Intl.DateTimeFormat('en-SG', { ...options, timeZone: singapore }).format(date)
}
