import { describe, expect, it } from 'vitest'
import { bookingHint, bookingTimeError, bookingWindow } from './bookingTime'

const now = new Date(2026, 9, 7, 17, 13)

describe('booking an extra service', () => {
  it('starts the picker on the next half hour after the notice, and ends it 90 days out', () => {
    expect(bookingWindow(now)).toEqual({ min: '2026-10-07T19:30', max: '2027-01-05T17:13' })
  })

  it('accepts a half-hour start that finishes by 8 pm', () => {
    expect(bookingTimeError('2026-10-08T08:00', 60, now)).toBeUndefined()
    expect(bookingTimeError('2026-10-08T19:00', 60, now)).toBeUndefined()
    expect(bookingTimeError('2026-10-08T17:00', 180, now)).toBeUndefined()
  })

  it.each([
    ['', 60, 'Choose a date and time.'],
    ['2026-10-07T19:00', 60, 'Choose a time at least 2 hours from now.'],
    ['2027-01-06T10:00', 60, 'Choose a time within the next 90 days.'],
    ['2026-10-08T10:15', 60, 'Choose a time on the hour or half hour.'],
    ['2026-10-08T07:30', 60, 'Choose a start between 8 am and 7 pm, so the visit ends by 8 pm.'],
    ['2026-10-08T17:30', 180, 'Choose a start between 8 am and 5 pm, so the visit ends by 8 pm.'],
  ])('refuses %j for a %i-minute service', (value, minutes, message) => {
    expect(bookingTimeError(value, minutes, now)).toBe(message)
  })

  it('says the rules for the chosen service in one line', () => {
    expect(bookingHint(90)).toBe('Start between 8 am and 6:30 pm, on the hour or half hour, at least 2 hours ahead and within 90 days.')
  })
})
