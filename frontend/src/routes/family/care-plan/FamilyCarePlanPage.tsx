import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useCareActivities } from '../../../features/careplan/careActivities'
import { groupedTasks, planDate, slotLines, useFamilyCarePlan, weeklyTime } from '../../../features/careplan/familyCarePlan'
import type { FamilyPlanVersion } from '../../../features/careplan/familyCarePlan'
import { useFamilyElderProfiles } from '../../../features/family-elders/queries'
import { useSelectedElder } from '../components/selectedElder'
import styles from '../intake/FamilyIntake.module.css'
import plan from './FamilyCarePlan.module.css'

/**
 * The final details of the elder's care plan as the care team published it: what is done, on which
 * days and for how long. Shows the version in force and, when one has been published to start
 * later, that one too. Read-only; any linked elder can be chosen, read-only access is enough.
 */
export function FamilyCarePlanPage() {
  const profiles = useFamilyElderProfiles()
  const { elderId, setElderId } = useSelectedElder()
  const elders = profiles.data ?? []
  const selected = elderId === null ? elders[0] : elders.find((elder) => elder.id === elderId)
  const selectedId = selected?.id
  useEffect(() => {
    if (elderId === null && selectedId !== undefined) setElderId(selectedId)
  }, [elderId, selectedId, setElderId])
  const carePlan = useFamilyCarePlan(selectedId)

  return <div className={styles.page}>
    <header className={styles.detailHeading}>
      <p className={styles.eyebrow}>CARE PLAN</p>
      <h1>{selected?.fullName ?? 'Care plan'}</h1>
      <p className={styles.subtitle}>What the care team does for your elder each week.</p>
    </header>
    {profiles.isError ? <section className={styles.state}><p role="alert">Unable to load your elders.</p>
      <button onClick={() => void profiles.refetch()}>Try again</button></section>
      : profiles.isPending ? <p role="status">Loading your elders…</p>
      : elders.length === 0 ? <section className={styles.state}><h2>No linked elders</h2>
        <p>Once an elder accepts your binding, their care plan appears here.</p>
        <Link to="/family/family-bindings">Review binding requests</Link></section>
      : <>
        {elders.length > 1 && <div className={plan.picker}><label htmlFor="care-plan-elder">Elder</label>
          <select id="care-plan-elder" value={selectedId ?? ''} onChange={(event) => setElderId(Number(event.target.value))}>
            {elders.map((elder) => <option key={elder.id} value={elder.id}>{elder.fullName}</option>)}
          </select></div>}
        {carePlan.isError ? <section className={styles.state}><p role="alert">Unable to show this care plan. Check your binding or try again.</p>
          <button onClick={() => void carePlan.refetch()}>Try again</button></section>
          : carePlan.isPending ? <p role="status">Loading the care plan…</p>
          : !carePlan.data.current && !carePlan.data.upcoming ? <section className={styles.state}>
            <h2>No care plan yet</h2>
            <p>The care team is preparing one. You'll get a notification when it's ready.</p>
            <Link to="/family/service-applications">See your service applications</Link></section>
          : <>
            {carePlan.data.current && <PlanVersion version={carePlan.data.current} heading="In place now" />}
            {carePlan.data.upcoming && <PlanVersion version={carePlan.data.upcoming} heading="Coming up" />}
          </>}
      </>}
  </div>
}

function PlanVersion({ version, heading }: { version: FamilyPlanVersion; heading: string }) {
  const { data: catalog } = useCareActivities()
  const dates = version.effectiveUntil
    ? `${planDate(version.effectiveFrom)} to ${planDate(version.effectiveUntil)}`
    : `from ${planDate(version.effectiveFrom)}`
  return <section className={styles.detailSection} aria-label={`${heading}: version ${version.version}`}>
    <h2>{heading}</h2>
    <div className={plan.version}>
      <p>Version {version.version}, {dates} · {weeklyTime(version)}</p>
      {groupedTasks(version).map(({ group, tasks }) => <div key={group}>
        <h3 className={styles.fieldTitle}>{group}</h3>
        <ul className={plan.group}>{tasks.map((task) => {
          // The activity the family chose, by its catalog name; the care team's own name for it when they renamed it.
          const activity = catalog?.find((a) => a.code === task.activityCode)?.label ?? task.name
          return <li key={task.name} className={plan.task}>
            <span className={plan.taskName}>{activity}</span>
            {activity !== task.name && <span className={plan.when}>{task.name}</span>}
            {slotLines(task.slots).map((line) => <span key={line} className={plan.when}>{line}</span>)}
          </li>
        })}</ul>
      </div>)}
    </div>
  </section>
}
