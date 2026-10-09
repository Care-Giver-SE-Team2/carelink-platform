import { Link, useLocation, useParams } from 'react-router-dom'
import { intakeDate, statusDescriptions } from '../../../features/intake/presentation'
import type { IntakeApplication } from '../../../features/intake/types'
import { useIntakeApplication } from '../../../features/intake/useIntakeQueries'
import { IntakeIcon, IntakeLoading, StatusBadge } from './IntakeLayout'
import { IntakeFeedback } from './IntakeFeedback'
import styles from './FamilyIntake.module.css'

const mobilityLabels = {
  INDEPENDENT: 'Moves independently',
  ASSISTIVE_CANE: 'Uses a walking aid',
  WHEELCHAIR_BEDBOUND: 'Uses a wheelchair / stays in bed',
}
const careLabels: Record<string, string> = {
  BATHING: 'Bathing assistance',
  VITALS: 'Vital signs monitoring',
}

/**
 * Displays an authorised application and the care team's recorded review.
 * @author Wang Zhili
 */
export function IntakeDetailPage() {
  const { id = '' } = useParams()
  const { search, state } = useLocation()
  const { resource, refresh } = useIntakeApplication(id)
  return (
    <div className={styles.page}>
      <div className={styles.detailNav}>
        <Link className={styles.backLink} to={'/family/intake' + search}>
          <IntakeIcon name="back" />
          Back to applications
        </Link>
        <button
          className={styles.textButton}
          onClick={refresh}
          disabled={resource.status === 'loading'}
          aria-label="Refresh application"
        >
          <IntakeIcon name="refresh" />
        </button>
      </div>
      {Number.isSafeInteger(state?.submittedApplicationId) &&
        String(state.submittedApplicationId) === id && (
          <section className={styles.progress} role="status">
            <h2>Application submitted</h2>
            <p>Your application #{state.submittedApplicationId} has been received.</p>
          </section>
        )}
      {resource.status === 'loading' && <IntakeLoading />}
      {resource.status === 'error' && <IntakeFeedback error={resource.error} onRetry={refresh} />}
      {resource.status === 'success' && <ApplicationDetails application={resource.data} />}
    </div>
  )
}

function ApplicationDetails({ application: item }: { application: IntakeApplication }) {
  const pending = item.status === 'SUBMITTED' || item.status === 'UNDER_REVIEW'
  return (
    <>
      <div className={styles.detailHeading}>
        <p className={styles.eyebrow}>APPLICATION #{item.id}</p>
        <h1>{item.targetElderName}</h1>
        <p className={styles.subtitle}>Your care application, at a glance.</p>
      </div>
      <section className={styles.progress} aria-label="Application status">
        <StatusBadge status={item.status} />
        <p>{statusDescriptions[item.status]}</p>
        <div className={styles.submittedAt}>
          Submitted <time dateTime={item.createdAt}>{intakeDate(item.createdAt)}</time>
        </div>
      </section>
      <section className={styles.detailSection}>
        <h2>Elder information</h2>
        <dl className={styles.fields}>
          <div>
            <dt>Full name</dt>
            <dd>{item.targetElderName}</dd>
          </div>
          <div>
            <dt>Age</dt>
            <dd>
              {item.targetElderAge === null ? 'Not provided' : item.targetElderAge + ' years'}
            </dd>
          </div>
          <div className={styles.fullWidth}>
            <dt>Address</dt>
            <dd>{item.targetAddress}</dd>
          </div>
          <div>
            <dt>Postal code</dt>
            <dd>{item.postalCode}</dd>
          </div>
          <div>
            <dt>Preferred dialects</dt>
            <dd>{item.preferredDialects || 'Not provided'}</dd>
          </div>
          <div className={styles.fullWidth}>
            <dt>Mobility</dt>
            <dd>{mobilityLabels[item.mobilityLevel] ?? item.mobilityLevel}</dd>
          </div>
        </dl>
      </section>
      <section className={styles.detailSection}>
        <h2>Care needs</h2>
        {item.careNeeds.length ? (
          <ul className={styles.careNeeds}>
            {item.careNeeds.map((need) => (
              <li key={need}>{Object.hasOwn(careLabels, need) ? careLabels[need] : need}</li>
            ))}
          </ul>
        ) : (
          <p className={styles.muted}>No care needs recorded.</p>
        )}
        <h3 className={styles.fieldTitle}>Medical notes</h3>
        <p className={styles.notes}>{item.medicalNotes || 'Not provided'}</p>
      </section>
      <section className={styles.detailSection}>
        <h2>Review details</h2>
        <dl className={styles.fields}>
          <div className={styles.fullWidth}>
            <dt>Review date</dt>
            <dd>
              {item.reviewedAt ? (
                <time dateTime={item.reviewedAt}>{intakeDate(item.reviewedAt)}</time>
              ) : pending ? (
                'Awaiting review'
              ) : (
                'Not available'
              )}
            </dd>
          </div>
          <div className={styles.fullWidth}>
            <dt>Review notes</dt>
            <dd>
              {item.reviewRemarks ||
                (pending
                  ? 'The care team has not added any review notes yet.'
                  : 'No review notes recorded.')}
            </dd>
          </div>
          {item.elderId !== null && (
            <div className={styles.fullWidth}>
              <dt>Elder profile reference</dt>
              <dd>#{item.elderId}</dd>
            </div>
          )}
        </dl>
      </section>
      <p className={styles.footnote}>Application times are shown in Singapore time.</p>
    </>
  )
}
