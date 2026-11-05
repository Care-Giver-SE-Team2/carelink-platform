import { describe, expect, it } from 'vitest'
import {
  dateOfBirthLabel,
  isScheduleDate,
  serviceLabel,
  shiftDays,
  singaporeToday,
  visitDate,
  visitStatusLabels,
  visitTime,
  weekLabel,
  weekStart,
} from './presentation'

describe('Singapore schedule dates', () => {
  it.each([
    ['2026-09-20T15:59:59Z', '2026-09-20', '2026-09-14'],
    ['2026-09-20T16:00:00Z', '2026-09-21', '2026-09-21'],
    ['2026-12-31T16:00:00Z', '2027-01-01', '2026-12-28'],
  ])('selects the Singapore date and week for %s', (instant, today, monday) => {
    expect(singaporeToday(new Date(instant))).toBe(today)
    expect(weekStart(today)).toBe(monday)
  })

  it.each([
    ['2026-09-21', '2026-09-21'],
    ['2026-09-27', '2026-09-21'],
    ['2026-09-28', '2026-09-28'],
    ['2028-02-29', '2028-02-28'],
  ])('finds Monday for %s', (date, monday) => {
    expect(weekStart(date)).toBe(monday)
  })

  it.each([
    ['2026-09-28', 6, '2026-10-04'],
    ['2026-12-28', 7, '2027-01-04'],
    ['2027-01-04', -7, '2026-12-28'],
    ['2028-02-28', 1, '2028-02-29'],
    ['2028-02-29', 1, '2028-03-01'],
    ['2026-03-08', 1, '2026-03-09'],
  ])('shifts %s by %i days across calendar boundaries', (date, days, expected) => {
    expect(shiftDays(date, days)).toBe(expected)
  })

  it.each([
    ['9999-12-31', 1],
    ['0000-01-01', -1],
  ])('rejects a shift from %s by %i days beyond a four-digit year', (date, days) => {
    expect(() => shiftDays(date, days)).toThrow(RangeError)
  })

  it.each(['1000-01-06', '1000-01-12', '2028-02-29', '9999-12-20', '9999-12-26'])(
    'accepts %s as a date in a complete supported schedule week', (date) => {
      expect(isScheduleDate(date)).toBe(true)
    },
  )

  it.each(['0999-12-31', '1000-01-01', '1000-01-05', '9999-12-27', '9999-12-31',
    '2026-02-30', '2026-13-01', '2026-9-21', '+010000-01-01', '', 'invalid'])(
    'rejects malformed dates and incomplete or unsupported schedule weeks: %s', (date) => {
      expect(isScheduleDate(date)).toBe(false)
    },
  )

  it.each(['2026-02-30', '2026-13-01', '2026-9-21', 'invalid'])('rejects invalid calendar dates: %s', (date) => {
    expect(() => weekStart(date)).toThrow(RangeError)
  })

  it.each([
    ['2026-09-21', '21 Sept – 27 Sept 2026'],
    ['2026-09-28', '28 Sept – 4 Oct 2026'],
    ['2026-12-28', '28 Dec 2026 – 3 Jan 2027'],
  ])('labels the complete week starting %s', (date, expected) => {
    expect(weekLabel(date)).toBe(expected)
  })

  it('formats UTC and offset timestamps as the same Singapore visit', () => {
    const utc = '2026-09-21T16:30:00Z'
    const singapore = '2026-09-22T00:30:00+08:00'

    expect(visitDate(utc)).toBe('Tue, 22 Sept 2026')
    expect(visitDate(singapore)).toBe(visitDate(utc))
    expect(visitTime(utc)).toBe('00:30')
    expect(visitTime(singapore)).toBe(visitTime(utc))
  })

  it('uses midnight rather than 24:00 at the Singapore day boundary', () => {
    expect(visitTime('2026-09-21T16:00:00Z')).toBe('00:00')
  })

  it('uses a placeholder for an invalid timestamp', () => {
    expect(visitDate('invalid')).toBe('Not available')
    expect(visitTime('')).toBe('Not available')
  })
})

describe('family visit labels', () => {
  it.each([
    ['BATHING', 'Bathing assistance'],
    ['VITALS', 'Vital signs monitoring'],
    ['  VITALS  ', 'Vital signs monitoring'],
    ['MEAL_PREPARATION', 'Meal preparation'],
    ['MOBILITY-SUPPORT', 'Mobility support'],
    ['Home exercise', 'Home exercise'],
    ['', 'Care visit'],
    ['   ', 'Care visit'],
    [null, 'Care visit'],
  ])('gives service %s a readable label', (raw, expected) => {
    expect(serviceLabel(raw)).toBe(expected)
  })

  it('distinguishes all public visit states', () => {
    expect(visitStatusLabels).toEqual({
      SCHEDULED: 'Scheduled',
      ARRIVED: 'Arrived',
      IN_PROGRESS: 'In progress',
      COMPLETED: 'Completed',
      VERIFIED: 'Verified',
      AUTO_CLOSED: 'Automatically closed',
      EXCEPTION: 'Exception',
      CANCELLED: 'Cancelled',
    })
  })
})

describe('dateOfBirthLabel', () => {
  it('spells out a recorded date and leaves anything else readable', () => {
    expect(dateOfBirthLabel('1950-07-30')).toBe('30 Jul 1950')
    expect(dateOfBirthLabel('1948-01-05')).toBe('5 Jan 1948')
    expect(dateOfBirthLabel(null)).toBeNull()
    expect(dateOfBirthLabel('unknown')).toBe('unknown')
  })
})
