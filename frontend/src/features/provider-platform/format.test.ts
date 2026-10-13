import { describe, expect, it } from 'vitest'
import {
  areaLong,
  areaShort,
  formatDayTime,
  formatLong,
  formatSchedule,
  formatShort,
  fromSgtInput,
  minutesLeft,
  toSgtInput,
} from './format'

it('names the district a job is in', () => {
  expect(areaShort('16')).toBe('D16 · Bedok')
  expect(areaLong('19')).toBe('District 19 · Hougang')
})

describe('times, in Singapore time', () => {
  const at = '2026-10-16T02:00:00Z'

  it('formats the scheduled start and its span', () => {
    expect(formatDayTime(at)).toBe('Fri 16 Oct, 10:00')
    expect(formatSchedule(at, 45)).toBe('Fri 16 Oct 2026, 10:00–10:45')
  })

  it('formats received and reviewed times', () => {
    expect(formatShort('2026-10-12T01:41:00Z')).toBe('12 Oct 09:41')
    expect(formatLong('2026-10-12T01:20:00Z')).toBe('12 Oct 2026, 09:20')
  })

  it('round-trips the datetime inputs through Singapore wall time', () => {
    expect(toSgtInput(at)).toBe('2026-10-16T10:00')
    expect(fromSgtInput('2026-10-16T10:00')).toBe('2026-10-16T02:00:00.000Z')
    expect(fromSgtInput('')).toBeNull()
  })

  it('counts whole minutes left, never below zero', () => {
    expect(minutesLeft(10 * 60_000 + 1, 0)).toBe(11)
    expect(minutesLeft(0, 5)).toBe(0)
  })
})
