import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useFamilySchedule } from '../../../features/schedule/useFamilySchedule'
import type { ScheduleSelection } from '../../../features/schedule/useFamilySchedule'
import type { FamilyVisitPage } from '../../../features/schedule/types'
import { isScheduleDate, scheduleDateBounds, shiftDays, singaporeToday, weekLabel, weekStart } from '../../../features/schedule/presentation'
import { ScheduleFeedback } from './ScheduleFeedback'
import { ScheduleVisitList } from './ScheduleVisitList'
import { CaregiverDetails } from './CaregiverDetails'
import styles from './FamilySchedule.module.css'
import { useSelectedElder } from '../components/selectedElder'
import { useIsDesktop } from '../components/useIsDesktop'

/**
 * Lets family members choose an elder and browse their Singapore weekly schedule.
 * @author Wang Zhili
 */
export function FamilySchedulePage() {
  const desktop = useIsDesktop()
  const followed = useSelectedElder()
  const [selection, setSelection] = useState<Pick<ScheduleSelection, 'elderId' | 'page'> & { date: string }>(() => ({
    elderId: followed.elderId, date: singaporeToday(), page: 0,
  }))
  // An elder chosen elsewhere (the desktop rail) switches this schedule too, back to its first page.
  const [seenFollowed, setSeenFollowed] = useState(followed.elderId)
  if (followed.elderId !== seenFollowed) {
    setSeenFollowed(followed.elderId)
    if (followed.elderId !== null) setSelection((value) => ({ ...value, elderId: followed.elderId, page: 0 }))
  }
  const week = weekStart(selection.date)
  const { resource, refresh, invalidateAccess } = useFamilySchedule({ ...selection, week })
  const [caregiver, setCaregiver] = useState<{ visits: FamilyVisitPage; id: number } | null>(null)
  const selectedElderId = resource.status === 'success'
    ? resource.data.selectedElderId : selection.elderId
  const selectedElder = resource.status === 'success'
    ? resource.data.elders.find((elder) => elder.id === selectedElderId) : undefined
  const changeDate = (date: string) => {
    if (isScheduleDate(date)) setSelection({ elderId: selectedElderId, date, page: 0 })
  }
  const resetAccess = () => {
    setSelection((value) => ({ ...value, elderId: null, page: 0 }))
    refresh()
  }
  const reload = () => {
    setSelection((value) => ({ ...value, elderId: selectedElderId }))
    refresh()
  }

  const header = <header className={styles.hero}>
    <p className={styles.eyebrow}>{desktop ? ['Schedule', selectedElder?.fullName].filter(Boolean).join(' · ') : selectedElder?.fullName ?? "Your family's care"}</p>
    <h1>Weekly schedule</h1>
    <p className={styles.meta}>{desktop ? weekLabel(week) : 'Planned visits, a week at a time'}</p>
  </header>
  const weekPanel = <>
      <section className={styles.weekPanel} aria-label="Choose a week">
        <button
          className={styles.stepper}
          aria-label="Previous week"
          disabled={week <= scheduleDateBounds.min}
          onClick={() => changeDate(shiftDays(selection.date, -7))}
        >‹</button>
        <div className={styles.weekCenter}>
          <h2>{weekLabel(week)}</h2>
          <label htmlFor="schedule-date" className={styles.hidden}>Choose a date</label>
          <input
            id="schedule-date"
            type="date"
            min={scheduleDateBounds.min}
            max={scheduleDateBounds.max}
            value={selection.date}
            aria-describedby="schedule-date-help"
            onChange={(event) => {
              const { value, validity } = event.currentTarget
              if (value && validity.valid && /^\d{4}-\d{2}-\d{2}$/.test(value)) changeDate(value)
            }}
          />
          <p id="schedule-date-help" className={styles.hidden}>Choose any date to view its whole week.</p>
        </div>
        <button
          className={styles.stepper}
          aria-label="Next week"
          disabled={week >= weekStart(scheduleDateBounds.max)}
          onClick={() => changeDate(shiftDays(selection.date, 7))}
        >›</button>
        <div className={styles.weekFoot}>
          <p>Times in Singapore (SGT)</p>
          <button onClick={() => changeDate(singaporeToday())}>This week</button>
        </div>
      </section>
  </>
  const status = <>
      {resource.status === 'loading' && <div className={styles.loading} role="status">
        <span className={styles.spinner} aria-hidden="true" />Loading your schedule…
      </div>}
      {resource.status === 'error' && <ScheduleFeedback error={resource.error} onRetry={reload} onResetAccess={resetAccess} />}
  </>
  // Phone only: on desktop the elder is chosen in the side rail. Refresh sits on the visit count row.
  const toolbar = resource.status === 'success' && resource.data.elders.length > 0 && !desktop && <>
        <div className={styles.toolbar}>
          <div className={styles.elderPicker}>
            <label htmlFor="schedule-elder">Care for</label>
            <select id="schedule-elder" value={selectedElderId ?? ''} onChange={(event) => {
              const elderId = Number(event.target.value)
              setSeenFollowed(elderId)
              followed.setElderId(elderId)
              setSelection({ ...selection, elderId, page: 0 })
            }}>
              {resource.data.elders.map((elder) => <option key={elder.id} value={elder.id}>{elder.fullName}</option>)}
            </select>
          </div>
        </div>
  </>
  const content = resource.status === 'success' && <>
        {resource.data.elders.length === 0 && <div className={styles.refreshBar}>
          <button className={styles.refresh} onClick={reload}>Refresh</button>
        </div>}
        {resource.data.elders.length === 0 && <section className={styles.state}>
          <h2>No linked elders yet</h2>
          <p>Your available elders will appear here once a family binding is active. Contact your care team if you need help with access.</p>
          <Link to="/family/service-applications">View my applications</Link>
        </section>}
        {resource.data.visits && <ScheduleVisitList
          visits={resource.data.visits}
          primaryCaregiver={selectedElder?.primaryCaregiverId != null && selectedElder.primaryCaregiverName
            ? { id: selectedElder.primaryCaregiverId, name: selectedElder.primaryCaregiverName } : null}
          onPage={(page) => setSelection({ ...selection, elderId: selectedElderId, page })}
          onCaregiver={(id) => setCaregiver({ visits: resource.data.visits!, id })}
          onRefresh={reload}
        />}
        {caregiver && caregiver.visits === resource.data.visits && <CaregiverDetails
          key={caregiver.id}
          caregiverId={caregiver.id}
          onClose={() => setCaregiver(null)}
          onAccessError={invalidateAccess}
        />}
  </>

  if (desktop) {
    return <div className={styles.schedule}>
      {header}
      <div className={styles.split}>
        <div className={styles.splitMain}>{status}{content}</div>
        <div className={styles.splitSide}>{weekPanel}{toolbar}</div>
      </div>
    </div>
  }
  return (
    <div className={styles.schedule}>
      {header}
      {weekPanel}
      {status}
      {toolbar}
      {content}
    </div>
  )
}
