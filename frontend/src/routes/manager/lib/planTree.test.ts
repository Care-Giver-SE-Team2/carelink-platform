import { describe, expect, it } from 'vitest'
import {
  dayScheduleFromVisits,
  isScheduleComplete,
  parseEditorTime,
  scheduleTags,
  timeRange,
  visitsFromDaySchedule,
  weeklyHours,
} from './planTree'
import type { DayVisit, TaskNode } from '../data/carePlans'

const everyDay = (startTime: string, minutes: number): DayVisit[] =>
  ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((day) => ({ day, startTime, minutes }))

describe('timeRange', () => {
  it('works the end out from start + minutes, naming the meridiem once when both ends share it', () => {
    expect(timeRange('08:00', 30)).toBe('8:00–8:30 AM')
    expect(timeRange('19:00', 5)).toBe('7:00–7:05 PM')
    expect(timeRange('12:00', 15)).toBe('12:00–12:15 PM')
  })

  it('names both meridiems when the visit crosses noon or midnight', () => {
    expect(timeRange('11:45', 30)).toBe('11:45 AM–12:15 PM')
    expect(timeRange('23:50', 20)).toBe('11:50 PM–12:10 AM')
  })
})

describe('scheduleTags', () => {
  it('gives one tag per day, each with its own start and minutes', () => {
    expect(
      scheduleTags([
        { day: 'Mon', startTime: '08:00', minutes: 30 },
        { day: 'Wed', startTime: '08:00', minutes: 45 },
        { day: 'Fri', startTime: '08:00', minutes: 60 },
      ]),
    ).toEqual(['Mon 8:00–8:30 AM · 30 m', 'Wed 8:00–8:45 AM · 45 m', 'Fri 8:00–9:00 AM · 60 m'])
  })

  it('collapses to a single Daily tag only when all seven days match', () => {
    expect(scheduleTags(everyDay('09:00', 15))).toEqual(['Daily 9:00–9:15 AM · 15 m'])

    const oneDayLater = everyDay('09:00', 15).map((v) => (v.day === 'Sat' ? { ...v, startTime: '10:00' } : v))
    expect(scheduleTags(oneDayLater)).toHaveLength(7)
    expect(scheduleTags(everyDay('09:00', 15).slice(0, 6))).toHaveLength(6)
  })
})

describe('weeklyHours', () => {
  it('sums a task’s per-day minutes', () => {
    const walk: TaskNode = {
      id: 't',
      type: 'task',
      activityCode: 'COMPANIONSHIP_WALK',
      name: 'Companionship walk',
      evidence: 'CHECKLIST',
      visits: [
        { day: 'Mon', startTime: '16:30', minutes: 20 },
        { day: 'Tue', startTime: '10:00', minutes: 20 },
        { day: 'Sat', startTime: '10:00', minutes: 30 },
      ],
    }
    expect(weeklyHours(walk)).toBeCloseTo(70 / 60)
  })
})

describe('parseEditorTime', () => {
  it('reads 12-hour and 24-hour input into "HH:mm"', () => {
    expect(parseEditorTime('8:00 AM')).toBe('08:00')
    expect(parseEditorTime('4:30pm')).toBe('16:30')
    expect(parseEditorTime('12:00 AM')).toBe('00:00')
    expect(parseEditorTime('12:15 PM')).toBe('12:15')
    expect(parseEditorTime('8 PM')).toBe('20:00')
    expect(parseEditorTime('16:30')).toBe('16:30')
  })

  it('rejects what isn’t a time', () => {
    expect(parseEditorTime('')).toBeNull()
    expect(parseEditorTime('13:00 PM')).toBeNull()
    expect(parseEditorTime('8:75 AM')).toBeNull()
    expect(parseEditorTime('noon')).toBeNull()
  })
})

describe('the day-schedule editor round trip', () => {
  it('pre-fills each day’s own start time and gives it back unchanged', () => {
    const visits: DayVisit[] = [
      { day: 'Mon', startTime: '08:00', minutes: 30 },
      { day: 'Thu', startTime: '16:30', minutes: 45 },
    ]
    const schedule = dayScheduleFromVisits(visits)
    expect(schedule.mon).toEqual({ active: true, startTime: '8:00 AM', minutes: 30 })
    expect(schedule.thu).toEqual({ active: true, startTime: '4:30 PM', minutes: 45 })
    expect(visitsFromDaySchedule(schedule)).toEqual(visits)
  })

  it('is incomplete while a day that’s on has no valid start time', () => {
    const schedule = dayScheduleFromVisits([{ day: 'Mon', startTime: '08:00', minutes: 30 }])
    expect(isScheduleComplete(schedule)).toBe(true)
    schedule.mon = { ...schedule.mon, startTime: 'soon' }
    expect(isScheduleComplete(schedule)).toBe(false)
  })
})
