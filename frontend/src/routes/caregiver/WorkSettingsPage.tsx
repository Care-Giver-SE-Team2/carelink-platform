import { useCallback, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { getAvailability, getWorkSettings, saveAvailability, savePreferences } from '../../features/caregiver/workSettings'
import type { AvailabilityWindow, WeeklyWindow, WorkPreferences, WorkSettingsView } from '../../features/caregiver/workSettings'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import { todayInSingapore } from './format'
import { ApiError } from '../../shared/api/client'
import EnglishDateField from './EnglishDateField'
import SelfServiceError from './SelfServiceError'
import { isAccessFailure, useSelfServiceWrite } from './selfService'
import { useUnsavedCaregiverForm } from './useUnsavedCaregiverForm'
import styles from './Caregiver.module.css'

const weekdays = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
const shortTime = (value: string) => value.slice(0, 5)
export default function WorkSettingsPage({ mode }: { mode: 'availability' | 'preferences' }) {
  const [date, setDate] = useState(todayInSingapore)
  const [draftDate, setDraftDate] = useState(date)
  const [editing, setEditing] = useState<WorkSettingsView | null>(null)
  const [notice, setNotice] = useState('')
  const load = useCallback((signal: AbortSignal) => mode === 'availability' ? getAvailability(date, signal) : getWorkSettings(signal), [mode, date])
  const { result, reload, retainedData } = useCaregiverQuery(`work-settings:${mode}:${date}`, load, !editing)
  const view = result.status === 'success' ? result.data : result.status === 'loading' ? retainedData : undefined
  return <div className={styles.page}>
    <div className={styles.heading}><div><p className={styles.eyebrow}>Caregiver workspace</p><h1>{mode === 'availability' ? 'My availability' : 'Work preferences'}</h1><p className={styles.muted}>{mode === 'availability' ? 'Declare when you can work on a given day.' : 'The services, areas and hours you prefer to work.'}</p></div></div><div className={styles.toolbar}><button className={styles.refresh} disabled={!!editing || result.status === 'loading'} onClick={reload}>Refresh</button></div>
    <div className={styles.readOnly}><p>Availability and preferences are declarations for your manager's reference only. They are not used in automatic rostering. Your manager may assign visits outside these hours, including times you declare unavailable. Follow your published schedule; updating settings never cancels or moves an assigned visit.</p>
    <p>Only approved leave blocks scheduling based on these time declarations. Pending or rejected leave is not permission to miss a visit. Approval and arranging cover are separate manager actions. For an existing shift you cannot attend, use <Link to="/caregiver/absences">My leave</Link> after admission or contact your manager. Published shifts do not require acceptance.</p>
    <p>All dates and times are Singapore time. No declaration means Unspecified, not Unavailable. Same-day windows use [start, end); overnight windows and 24:00 are not supported.</p></div>
    {mode === 'availability' && <form className={styles.dateForm} onSubmit={event => { event.preventDefault(); if (!editing && /^\d{4}-\d{2}-\d{2}$/.test(draftDate)) { setDate(draftDate); setNotice('') } }}>
      <fieldset disabled={!!editing}><EnglishDateField label="Availability date" value={draftDate} onChange={setDraftDate} /><button className={styles.button}>Load date</button></fieldset>
    </form>}
    {result.status === 'loading' && <p role="status">Loading work settings…</p>}
    {result.status === 'error' && <SelfServiceError error={result.error} retry={reload} subject="your work settings" />}
    {notice && <p role="status">{notice}</p>}
    {view && <><section className={styles.card} aria-label={mode === 'availability' ? 'Saved availability' : 'Saved preferences'}>
      <p className={styles.muted}>Settings version {view.settings.version} · Changes allowed from {view.today} to {view.editableUntil}.</p>
      {view.settings.dataIssues.length > 0 && <p role="alert">Some existing settings need review: {view.settings.dataIssues.join(', ')}. They have not been silently cleared. Review all selections before replacing them.</p>}
      {mode === 'availability' ? <>
        <h2>{date}</h2>
        {(view.settings.availability.find(day => day.date === date)?.windows ?? []).length === 0 ? <p>Unspecified — no windows declared.</p> : <ul>{view.settings.availability.find(day => day.date === date)!.windows.map((window, index) => <li key={index}>{window.status === 'UNAVAILABLE' ? 'Declared unavailable (reference only)' : 'Declared available (reference only)'} · {shortTime(window.start)}–{shortTime(window.end)}</li>)}</ul>}
        {view.bookings.length > 0 && <aside><h3>Existing assigned visits — not changed by these settings</h3><ul>{view.bookings.map(booking => <li key={booking.visitId}>Visit {booking.visitId} · {booking.start.replace('T', ' ')}–{booking.end.replace('T', ' ')}</li>)}</ul><p>If a declared unavailable window conflicts, contact your manager; saving does not reassign it.</p></aside>}
        {view.leave.length > 0 && <aside><h3>Leave requests</h3><ul>{view.leave.map(leave => <li key={leave.id}>{leave.startDate}–{leave.endDate} · {leave.status}</li>)}</ul></aside>}
      </> : <>
        <h2>Saved soft preferences</h2>
        <p>Services: {view.settings.preferences.serviceCodes.join(', ') || 'No preference'} · Areas: {view.settings.preferences.sectors.join(', ') || 'No preference'}</p>
        <ul>{view.settings.preferences.weeklyWindows.map((window, index) => <li key={index}>{window.weekday} · {shortTime(window.start)}–{shortTime(window.end)}</li>)}</ul>
        <p>Daily visits: {view.settings.preferences.maxVisitsPerDay ?? 'No preference'} · Daily hours: {view.settings.preferences.maxHoursPerDay ?? 'No preference'}</p>
        {!view.catalog.serviceCatalogConfigured && <p>Service choices await an agreed service-code directory. Other preferences can still be saved.</p>}
      </>}
      {!editing && view.canEdit && (mode === 'preferences' || date >= view.today && date <= view.editableUntil) && <button className={styles.button} onClick={() => { setEditing(view); setNotice('') }}>Edit {mode === 'availability' ? 'availability' : 'preferences'}</button>}
      </section>
      {editing && <SettingsEditor mode={mode} date={date} source={editing} close={() => setEditing(null)} saved={() => { setEditing(null); setNotice('Settings saved for reference only. Your published schedule is unchanged.'); reload() }} />}
    </>}
  </div>
}
function SettingsEditor({ mode, date, source, close, saved }: { mode: 'availability' | 'preferences'; date: string; source: WorkSettingsView; close: () => void; saved: () => void }) {
  const [windows, setWindows] = useState<AvailabilityWindow[]>(() => (source.settings.availability.find(day => day.date === date)?.windows ?? []).map(w => ({ ...w, start: shortTime(w.start), end: shortTime(w.end) })))
  const [preferences, setPreferences] = useState<WorkPreferences>(() => ({ ...source.settings.preferences, weeklyWindows: source.settings.preferences.weeklyWindows.map(w => ({ ...w, start: shortTime(w.start), end: shortTime(w.end) })) }))
  const [key, setKey] = useState(() => crypto.randomUUID())
  const [validation, setValidation] = useState('')
  const [reviewed, setReviewed] = useState(false)
  const mutation = useSelfServiceWrite()
  const uncertain = mutation.error !== null && (!(mutation.error instanceof ApiError) || mutation.error.status >= 500)
  const errorCode = mutation.error instanceof ApiError && mutation.error.body && typeof mutation.error.body === 'object' && 'code' in mutation.error.body ? String(mutation.error.body.code) : ''
  const conflict = errorCode === 'WORK_SETTINGS_VERSION_CONFLICT' || errorCode === 'WORK_SETTINGS_REQUEST_CONFLICT'
  useUnsavedCaregiverForm(true)
  const change = (next: WorkPreferences) => { setPreferences(next); setKey(crypto.randomUUID()) }
  function submit(event: FormEvent) {
    event.preventDefault()
    if (source.settings.dataIssues.length > 0 && !reviewed) { setValidation('Confirm that you reviewed the existing data issues before replacement.'); return }
    const all = mode === 'availability' ? windows : preferences.weeklyWindows
    if (all.some(w => !/^\d{2}:\d{2}$/.test(w.start) || !/^\d{2}:\d{2}$/.test(w.end) || w.start >= w.end || w.end >= '24:00')) { setValidation('Enter valid same-day HH:mm windows with start before end.'); return }
    setValidation('')
    void mutation.send(signal => mode === 'availability' ? saveAvailability(date, windows, source.settings.version, key, signal) : savePreferences(preferences, source.settings.version, key, signal), saved)
  }
  if (isAccessFailure(mutation.error)) return <SelfServiceError error={mutation.error} retry={close} subject="your work settings" />
  return <form className={styles.formPanel} aria-label="Edit work settings" noValidate onSubmit={submit}>
    <fieldset disabled={mutation.pending || uncertain || conflict}>
      {mode === 'availability' ? <>
        {windows.map((window, index) => <div className={styles.dateForm} key={index}>
          <label>Status {index + 1}<select value={window.status} onChange={event => { setWindows(windows.map((w, i) => i === index ? { ...w, status: event.target.value as AvailabilityWindow['status'] } : w)); setKey(crypto.randomUUID()) }}><option value="AVAILABLE">Available (reference only)</option><option value="UNAVAILABLE">Unavailable (reference only)</option></select></label>
          <TimeFields value={window} index={index} change={next => { setWindows(windows.map((w, i) => i === index ? { ...w, ...next } : w)); setKey(crypto.randomUUID()) }} />
          <button type="button" onClick={() => { setWindows(windows.filter((_, i) => i !== index)); setKey(crypto.randomUUID()) }}>Remove window {index + 1}</button>
        </div>)}
        <button type="button" disabled={windows.length >= 8} onClick={() => { setWindows([...windows, { start: '09:00', end: '12:00', status: 'AVAILABLE' }]); setKey(crypto.randomUUID()) }}>Add window</button>
        {windows.length === 0 && <p>Saving no windows clears this day to Unspecified. It does not request leave or make you unavailable all day.</p>}
      </> : <>
        <Choices title="Preferred services" values={source.catalog.serviceCodes} selected={preferences.serviceCodes} change={serviceCodes => change({ ...preferences, serviceCodes })} />
        <Choices title="Preferred areas" values={source.catalog.sectors} selected={preferences.sectors} change={sectors => change({ ...preferences, sectors })} />
        <p>Empty selection means no preference. These choices do not change your manager-assigned area.</p>
        {preferences.weeklyWindows.map((window, index) => <div className={styles.dateForm} key={index}>
          <label>Weekday {index + 1}<select value={window.weekday} onChange={event => change({ ...preferences, weeklyWindows: preferences.weeklyWindows.map((w, i) => i === index ? { ...w, weekday: event.target.value } : w) })}>{weekdays.map(day => <option key={day}>{day}</option>)}</select></label>
          <TimeFields value={window} index={index} change={next => change({ ...preferences, weeklyWindows: preferences.weeklyWindows.map((w, i) => i === index ? { ...w, ...next } : w) })} />
          <button type="button" onClick={() => change({ ...preferences, weeklyWindows: preferences.weeklyWindows.filter((_, i) => i !== index) })}>Remove window {index + 1}</button>
        </div>)}
        <button type="button" disabled={preferences.weeklyWindows.length >= 14} onClick={() => change({ ...preferences, weeklyWindows: [...preferences.weeklyWindows, { weekday: 'MONDAY', start: '09:00', end: '12:00' }] })}>Add preferred window</button>
        <label>Preferred daily visit limit<input type="number" min={1} max={source.catalog.maxVisitsPerDay} value={preferences.maxVisitsPerDay ?? ''} onChange={event => change({ ...preferences, maxVisitsPerDay: event.target.value ? Number(event.target.value) : null })} /></label>
        <label>Preferred daily hours<input type="number" min={0.5} max={source.catalog.maxHoursPerDay} step={0.5} value={preferences.maxHoursPerDay ?? ''} onChange={event => change({ ...preferences, maxHoursPerDay: event.target.value ? Number(event.target.value) : null })} /></label>
        <p>Leave blank for no preference. Personal preferences never relax institution limits.</p>
      </>}
      {source.settings.dataIssues.length > 0 && <label><input type="checkbox" checked={reviewed} onChange={event => setReviewed(event.target.checked)} />I reviewed all existing data issues and confirm replacing these settings.</label>}
    </fieldset>
    {validation && <p role="alert">{validation}</p>}
    {mutation.error !== null && <p role="alert">{uncertain ? 'Save result unknown. Your draft and request key are retained; retry only this unchanged request.' : mutation.error instanceof ApiError ? mutation.error.message : 'Unable to save.'}</p>}
    <button className={styles.button} disabled={mutation.pending || conflict}>{uncertain ? 'Retry unchanged request' : 'Save settings'}</button>
    <button className={styles.button} type="button" disabled={mutation.pending} onClick={() => { if (window.confirm('Close this draft? Check the saved settings before starting a new edit if a save result is unknown.')) close() }}>Close editor</button>
  </form>
}
function TimeFields({ value, index, change }: { value: Pick<WeeklyWindow, 'start' | 'end'>; index: number; change: (value: Pick<WeeklyWindow, 'start' | 'end'>) => void }) {
  return <><label>Start time {index + 1}<input placeholder="HH:mm" inputMode="numeric" value={value.start} onChange={event => change({ ...value, start: event.target.value })} /></label><label>End time {index + 1}<input placeholder="HH:mm" inputMode="numeric" value={value.end} onChange={event => change({ ...value, end: event.target.value })} /></label></>
}
function Choices({ title, values, selected, change }: { title: string; values: string[]; selected: string[]; change: (values: string[]) => void }) {
  return <fieldset><legend>{title}</legend>{[...new Set([...values, ...selected])].map(value => <label key={value}><input type="checkbox" checked={selected.includes(value)} onChange={event => change(event.target.checked ? [...selected, value] : selected.filter(v => v !== value))} />{value}{!values.includes(value) && ' (not in current directory — remove before saving)'}</label>)}{values.length === 0 && <p>No agreed choices available.</p>}</fieldset>
}
