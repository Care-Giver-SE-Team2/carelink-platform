export const WEEKDAYS = [
  { key: 'mon', letter: 'M', short: 'Mon', full: 'Monday' },
  { key: 'tue', letter: 'T', short: 'Tue', full: 'Tuesday' },
  { key: 'wed', letter: 'W', short: 'Wed', full: 'Wednesday' },
  { key: 'thu', letter: 'T', short: 'Thu', full: 'Thursday' },
  { key: 'fri', letter: 'F', short: 'Fri', full: 'Friday' },
  { key: 'sat', letter: 'S', short: 'Sat', full: 'Saturday' },
  { key: 'sun', letter: 'S', short: 'Sun', full: 'Sunday' },
] as const

export type DayKey = (typeof WEEKDAYS)[number]['key']

/** One weekday's slot. Start time and duration are set per day, independently of the others. */
export type DayScheduleEntry = { active: boolean; startTime: string; minutes: number | null }

export type DayScheduleValue = Record<DayKey, DayScheduleEntry>

/** Every day off, pre-filled with the values a day gets when switched on. */
export function emptyDaySchedule(defaults: { startTime: string; minutes: number } = { startTime: '8:00 AM', minutes: 30 }): DayScheduleValue {
  return Object.fromEntries(
    WEEKDAYS.map((d) => [d.key, { active: false, startTime: defaults.startTime, minutes: defaults.minutes }]),
  ) as DayScheduleValue
}
