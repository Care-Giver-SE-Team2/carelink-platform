import { Link, useLocation, useParams } from 'react-router-dom'
import { useServiceApplication } from '../../../features/service-applications/queries'
import { intakeDate } from '../../../features/intake/presentation'
import { careNeedLabel, useCareActivities } from '../../../features/careplan/careActivities'
import { planDate } from '../../../features/careplan/familyCarePlan'
import type { ServiceApplication, ServiceApplicationNeed } from '../../../features/service-applications/api'
import { IntakeLoading } from '../intake/IntakeLayout'
import { ElderSnapshot } from './ElderSnapshot'
import { OutcomeBadge } from './OutcomeBadge'
import styles from '../intake/FamilyIntake.module.css'

const outcomeText: Record<ServiceApplication['outcome'], string> = {
  SUBMITTED: 'Your application has been received and is waiting for review. The care team adds the services to your elder\'s care plan, or tells you why not.',
  PLANNED: 'Everything you asked for is in your elder\'s care plan.',
  DECLINED: 'The care team could not add these services to the care plan.',
}

/** "In care plan v2 from 20 Oct 2026", or that it isn't planned yet. */
function needStatus(need: ServiceApplicationNeed | undefined): string {
  if (!need?.plannedVersion) return 'Not in the care plan yet'
  return `In care plan v${need.plannedVersion}` + (need.plannedFrom ? ` from ${planDate(need.plannedFrom)}` : '')
}
export function ServiceApplicationDetailPage() {
  const { id = '' } = useParams()
  const { search, state } = useLocation()
  const query = useServiceApplication(id)
  const activities = useCareActivities()
  const item = query.data
  return <div className={styles.page}>
    <div className={styles.detailNav}>
      <Link to={'/family/service-applications' + search}>← Back to service applications</Link>
      <button onClick={() => void query.refetch()} disabled={query.isFetching || !/^[1-9]\d*$/.test(id)}>Refresh</button>
    </div>
    {Number.isSafeInteger(state?.submittedApplicationId) && String(state.submittedApplicationId) === id &&
      <section role="status" className={styles.progress}><h2>Application submitted</h2><p>Your service application #{id} has been received.</p></section>}
    {!/^[1-9]\d*$/.test(id) ? <p role="alert">Invalid application reference.</p> : query.isError ?
      <p role="alert" className={styles.state}>Unable to view this service application. Check your current binding or try Refresh.</p> : query.isPending ? <IntakeLoading /> : item && <>
        <header className={styles.detailHeading}><p className={styles.eyebrow}>SERVICE APPLICATION #{item.id}</p><h1>{item.elderSnapshot.fullName}</h1></header>
        <section className={styles.progress} aria-label="Application status"><OutcomeBadge outcome={item.outcome} />
          <p>{outcomeText[item.outcome]}</p>
          {item.outcome === 'DECLINED' && item.declineReason && <p>Reason: {item.declineReason}</p>}
          {item.needs.some((need) => need.plannedVersion) && <p><Link to="/family/care-plan">See the care plan</Link></p>}
          <p>Submitted <time dateTime={item.createdAt}>{intakeDate(item.createdAt)}</time> (Singapore time)</p>
        </section>
        <section className={styles.detailSection}><h2>Elder details at submission</h2>
          <p>These saved details stay unchanged when the elder's profile is updated.</p>
          <ElderSnapshot details={item.elderSnapshot} />
        </section>
        <section className={styles.detailSection}><h2>Requested care services</h2>
          <ul className={styles.careNeeds}>{item.careNeeds.map((need) => <li key={need}>{careNeedLabel(activities.data, need)}
            {' — '}{needStatus(item.needs.find((progress) => progress.need === need))}</li>)}</ul>
          <h3 className={styles.fieldTitle}>Notes for this application</h3><p className={styles.notes}>{item.notes || 'Not provided'}</p>
        </section>
      </>}
  </div>
}
