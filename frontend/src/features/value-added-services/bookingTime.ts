/**
 * When an extra service can be booked. The server checks the same rules in
 * `ValueAddedServiceRequest.requireBookableTime`; keep the two in step.
 */

/** The server asks for at least this much notice. */
export const MIN_NOTICE_HOURS = 2
/** And takes bookings no further ahead than this. */
export const MAX_ADVANCE_DAYS = 90
/** Visits start no earlier than DAY_START and finish by DAY_END, in hours of the day. */
const DAY_START = 8
const DAY_END = 20
/** Start times fall on the hour or the half hour. */
export const STEP_MINUTES = 30

/** The server's codes for a time it will not book; a form shows them under its date and time. */
export const BOOKING_TIME_CODES = new Set([
  'VALUE_ADDED_SERVICE_TOO_SOON',
  'VALUE_ADDED_SERVICE_TOO_FAR',
  'VALUE_ADDED_SERVICE_OFF_STEP',
  'VALUE_ADDED_SERVICE_OUTSIDE_HOURS',
])

const pad = (n: number) => String(n).padStart(2, '0')

/** "2026-10-10T12:30", the datetime-local value for a moment on this device. */
function inputValue(moment: Date): string {
  return `${moment.getFullYear()}-${pad(moment.getMonth() + 1)}-${pad(moment.getDate())}T${pad(moment.getHours())}:${pad(moment.getMinutes())}`
}

/** "8 am", "7:30 pm". */
function clock(minutesOfDay: number): string {
  const hours = Math.floor(minutesOfDay / 60)
  const minutes = minutesOfDay % 60
  const twelve = hours % 12 === 0 ? 12 : hours % 12
  return `${twelve}${minutes ? `:${pad(minutes)}` : ''} ${hours < 12 ? 'am' : 'pm'}`
}

/**
 * The picker's `min` and `max`. `min` is rounded up to the next half hour, because a browser
 * counts `step` from `min` and would otherwise offer 10:13, 10:43…
 */
export function bookingWindow(now = new Date()): { min: string; max: string } {
  const earliest = new Date(now.getTime() + MIN_NOTICE_HOURS * 60 * 60 * 1000)
  earliest.setSeconds(0, 0)
  const over = earliest.getMinutes() % STEP_MINUTES
  if (over) earliest.setMinutes(earliest.getMinutes() + STEP_MINUTES - over)
  const latest = new Date(now.getTime() + MAX_ADVANCE_DAYS * 24 * 60 * 60 * 1000)
  return { min: inputValue(earliest), max: inputValue(latest) }
}

/** The rules in one line, for the hint under the picker. */
export function bookingHint(durationMinutes: number): string {
  return `Start between ${clock(DAY_START * 60)} and ${clock(DAY_END * 60 - durationMinutes)}, on the hour or half hour, `
    + `at least ${MIN_NOTICE_HOURS} hours ahead and within ${MAX_ADVANCE_DAYS} days.`
}

/** Why a datetime-local value cannot be booked for a service this long, or undefined when it can. */
export function bookingTimeError(value: string, durationMinutes: number, now = new Date()): string | undefined {
  if (!value) return 'Choose a date and time.'
  const start = new Date(value)
  if (Number.isNaN(start.getTime())) return 'Choose a date and time.'
  if (start.getTime() < now.getTime() + MIN_NOTICE_HOURS * 60 * 60 * 1000)
    return `Choose a time at least ${MIN_NOTICE_HOURS} hours from now.`
  if (start.getTime() > now.getTime() + MAX_ADVANCE_DAYS * 24 * 60 * 60 * 1000)
    return `Choose a time within the next ${MAX_ADVANCE_DAYS} days.`
  if (start.getMinutes() % STEP_MINUTES !== 0) return 'Choose a time on the hour or half hour.'
  const startMinute = start.getHours() * 60 + start.getMinutes()
  if (startMinute < DAY_START * 60 || startMinute + durationMinutes > DAY_END * 60)
    return `Choose a start between ${clock(DAY_START * 60)} and ${clock(DAY_END * 60 - durationMinutes)}, so the visit ends by ${clock(DAY_END * 60)}.`
  return undefined
}
