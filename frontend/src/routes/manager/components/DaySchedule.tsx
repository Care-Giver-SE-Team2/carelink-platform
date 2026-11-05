import { DayToggle, Select } from '../../../shared/components/ui'
import type { SelectItem } from '../../../shared/components/ui'
import { HALF_HOUR_START_TIMES, VISIT_DURATIONS, durationLabel, isOnHalfHour, parseEditorTime, withinVisitingHours } from '../lib/planTree'
import { WEEKDAYS } from './weekdays'
import type { DayKey, DayScheduleEntry, DayScheduleValue } from './weekdays'
import styles from './DaySchedule.module.css'

/**
 * Seven weekday rows — toggle, start time, minutes — for authoring a sub-plan's or task's
 * schedule. Start times are picked from the half hours and lengths from 30-minute blocks up to
 * 4 hours that end by midnight; a value that breaks those rules (saved before them, or a length
 * left too long by a later start) shows until the manager picks a new one.
 * An inactive day disables both inputs. Manager-only: a read-only view of a
 * schedule for other roles belongs in the shared tier as its own component.
 */
export function DaySchedule({
  value,
  onChange,
  disabled = false,
}: {
  value: DayScheduleValue
  onChange: (next: DayScheduleValue) => void
  disabled?: boolean
}) {
  function update(key: DayKey, patch: Partial<DayScheduleEntry>) {
    onChange({ ...value, [key]: { ...value[key], ...patch } })
  }

  return (
    <div className={styles.schedule}>
      {WEEKDAYS.map((d) => {
        const entry = value[d.key]
        const inactive = disabled || !entry.active
        return (
          <div key={d.key} className={styles.row}>
            <DayToggle
              day={d.letter}
              label={d.full}
              active={entry.active}
              disabled={disabled}
              onToggle={() => update(d.key, { active: !entry.active })}
            />
            <Select
              aria-label={`${d.full} start time`}
              className={styles.time}
              options={startTimeOptions(entry.startTime)}
              value={inactive ? '' : entry.startTime}
              placeholder="—"
              disabled={inactive}
              onChange={(startTime) => update(d.key, { startTime })}
            />
            <Select
              aria-label={`${d.full} minutes`}
              className={styles.duration}
              options={durationOptions(entry.minutes, entry.startTime)}
              value={inactive || entry.minutes === null ? '' : String(entry.minutes)}
              placeholder="—"
              disabled={inactive}
              onChange={(minutes) => update(d.key, { minutes: Number(minutes) })}
            />
          </div>
        )
      })}
    </div>
  )
}

const HALF_HOURS: SelectItem[] = HALF_HOUR_START_TIMES.map((time) => ({ value: time, label: time }))

/** The half hours in visiting hours, led by the day's current time when it is a time but not one of them. */
function startTimeOptions(current: string): SelectItem[] {
  if (HALF_HOUR_START_TIMES.includes(current) || parseEditorTime(current) === null) return HALF_HOURS
  const why = isOnHalfHour(current) ? 'outside 7 AM – 11 PM' : 'pick :00 or :30'
  return [{ value: current, label: `${current} – ${why}` }, ...HALF_HOURS]
}

/**
 * The 30-minute blocks that end by 11:00 PM from this start, led by the day's current length when
 * it is set but not one of them, labelled with why.
 */
function durationOptions(current: number | null, startTime: string): SelectItem[] {
  const fitting = VISIT_DURATIONS.filter((minutes) => withinVisitingHours(startTime, minutes))
  const options = (fitting.length > 0 ? fitting : VISIT_DURATIONS).map((minutes) => ({
    value: String(minutes),
    label: durationLabel(minutes),
  }))
  if (current === null || current <= 0 || options.some((o) => o.value === String(current))) return options
  const why = VISIT_DURATIONS.includes(current)
    ? `${durationLabel(current)} – runs past 11 PM`
    : `${current} min – pick 30 min steps`
  return [{ value: String(current), label: why }, ...options]
}
