import { useId, useRef, useState } from 'react'
import { isCalendarDate } from './calendarDate'
import { todayInSingapore } from './format'
import styles from './EnglishDateField.module.css'

const monthLabel = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric', timeZone: 'UTC' })
const dayLabel = new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })

/** Own English labels instead of the browser/OS-localised native date picker. Values stay ISO dates. */
export default function EnglishDateField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  const id = useId()
  const input = useRef<HTMLInputElement>(null)
  const [open, setOpen] = useState(false)
  const [month, setMonth] = useState(() => (isCalendarDate(value) ? value : todayInSingapore()).slice(0, 7))
  const first = new Date(month + '-01T00:00:00Z')
  const days = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0)).getUTCDate()
  function choose(date: string) { onChange(date); setOpen(false); input.current?.focus() }
  function move(offset: number) {
    const next = new Date(first)
    next.setUTCMonth(next.getUTCMonth() + offset)
    setMonth(next.toISOString().slice(0, 7))
  }
  return <div className={styles.field} lang="en">
    <label htmlFor={id}>{label}</label>
    <div className={styles.inputRow}>
      <input ref={input} id={id} type="text" inputMode="numeric" autoComplete="off" placeholder="YYYY-MM-DD" maxLength={10} required
        aria-describedby={id + '-format'} value={value} onChange={event => onChange(event.target.value)} />
      <button type="button" aria-label={'Choose ' + label.toLowerCase()} aria-expanded={open} aria-controls={id + '-calendar'}
        onClick={() => { setMonth((isCalendarDate(value) ? value : todayInSingapore()).slice(0, 7)); setOpen(!open) }}>Calendar</button>
    </div>
    <small id={id + '-format'}>YYYY-MM-DD</small>
    {open && <div id={id + '-calendar'} className={styles.calendar} role="group" aria-label={label + ' calendar'}
      onKeyDown={event => { if (event.key === 'Escape') { event.preventDefault(); setOpen(false); input.current?.focus() } }}>
      <div className={styles.monthHeader}>
        <button type="button" aria-label={label + ': previous month'} disabled={month === '1000-01'} onClick={() => move(-1)}>‹</button>
        <span aria-live="polite">{monthLabel.format(first)}</span>
        <button type="button" aria-label={label + ': next month'} disabled={month === '9999-12'} onClick={() => move(1)}>›</button>
      </div>
      <div className={styles.days}>
        {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map(day => <span key={day} className={styles.weekday}>{day}</span>)}
        {Array.from({ length: first.getUTCDay() }, (_, index) => <span key={'empty-' + index} aria-hidden="true" />)}
        {Array.from({ length: days }, (_, index) => {
          const date = month + '-' + String(index + 1).padStart(2, '0')
          return <button key={date} type="button" aria-label={dayLabel.format(new Date(date + 'T00:00:00Z'))} aria-pressed={value === date}
            className={value === date ? styles.selected : undefined} onClick={() => choose(date)}>{index + 1}</button>
        })}
      </div>
      <div className={styles.actions}><button type="button" onClick={() => choose(todayInSingapore())}>Today</button><button type="button" onClick={() => choose('')}>Clear</button><button type="button" onClick={() => { setOpen(false); input.current?.focus() }}>Close</button></div>
    </div>}
  </div>
}
