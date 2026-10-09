import { Link } from 'react-router-dom'
import type { FamilyVisitPage } from '../../../features/schedule/types'
import { serviceLabel, visitDate, visitDayMonth, visitStatusLabels, visitTime, visitWeekday } from '../../../features/schedule/presentation'
import styles from './FamilySchedule.module.css'

/**
 * Displays the returned page of planned visits and its server snapshot time.
 * @author Wang Zhili
 */
export function ScheduleVisitList({ visits, primaryCaregiver = null, onPage, onCaregiver, onRefresh }: {
  visits: FamilyVisitPage
  /** The elder's primary caregiver, named on the rows they are assigned to. */
  primaryCaregiver?: { id: number; name: string } | null
  onPage: (page: number) => void
  onCaregiver: (id: number) => void
  onRefresh: () => void
}) {
  const pages = Math.ceil(visits.totalElements / visits.size)
  const snapshot = visits.items[0]?.asOf
  return (
    <>
      <div className={styles.summary}>
        <div className={styles.summaryText} aria-live="polite">
          <strong>{visits.totalElements} {visits.totalElements === 1 ? 'visit' : 'visits'} this week</strong>
          {visits.items.length > 0 && <span>
            Showing {visits.page * visits.size + 1}–{visits.page * visits.size + visits.items.length} of {visits.totalElements}
          </span>}
        </div>
        <button className={styles.refresh} onClick={onRefresh}>Refresh</button>
      </div>
      {visits.items.length === 0 ? (
        <section className={styles.state}>
          <h2>{visits.page > 0 ? 'No visits on this page' : 'No visits this week'}</h2>
          <p>{visits.page > 0
            ? 'The schedule may have changed. Return to the first page to check it.'
            : 'Planned care visits will appear here. You can also check another week.'}</p>
          {visits.page > 0 && <button onClick={() => onPage(0)}>Back to first page</button>}
        </section>
      ) : (
        <ol className={styles.visits} aria-label="Scheduled visits">
          {visits.items.map((visit) => (
            <li key={visit.id} className={styles.row} data-status={visit.status}>
              <time className={styles.day} dateTime={visit.scheduledStart}>
                <strong>{visitWeekday(visit.scheduledStart)}</strong>{visitDayMonth(visit.scheduledStart)}
              </time>
              <div className={styles.rowMain}>
                <h2>{serviceLabel(visit.serviceType)}</h2>
                <p className={styles.time}>
                  <time dateTime={visit.scheduledStart}>{visitTime(visit.scheduledStart)}</time>
                  {visit.scheduledEnd
                    ? <> – <time dateTime={visit.scheduledEnd}>
                      {visitDate(visit.scheduledStart) !== visitDate(visit.scheduledEnd) && visitDate(visit.scheduledEnd) + ', '}
                      {visitTime(visit.scheduledEnd)}
                    </time></>
                    : <span> · End time to be confirmed</span>}
                </p>
                <p className={styles.sub}>
                  {visit.caregiverId === null ? 'Caregiver awaiting assignment'
                    : visit.caregiverId === primaryCaregiver?.id ? primaryCaregiver.name : 'Caregiver assigned'}
                </p>
              </div>
              <div className={styles.rowSide}>
                <span className={styles.status}>{visitStatusLabels[visit.status] ?? visit.status}</span>
                {visit.caregiverId !== null && <button
                  className={styles.caregiverButton}
                  aria-label={`View caregiver for visit ${visit.id}`}
                  aria-haspopup="dialog"
                  onClick={() => onCaregiver(visit.caregiverId!)}
                >caregiver</button>}
              </div>
              <Link className={styles.progressLink} to={`/family/visits/${visit.id}`}
                aria-label={`View progress for visit ${visit.id}`}>
                <span className={styles.hidden}>View progress</span>
              </Link>
            </li>
          ))}
        </ol>
      )}
      {pages > 1 && visits.items.length > 0 && <nav className={styles.pagination} aria-label="Schedule pages">
        <button onClick={() => onPage(visits.page - 1)} disabled={visits.page === 0}>Previous page</button>
        <span>Page {visits.page + 1} of {pages}</span>
        <button onClick={() => onPage(visits.page + 1)} disabled={visits.page + 1 >= pages}>Next page</button>
      </nav>}
      {snapshot && <p className={styles.snapshot}>
        Schedule checked <time dateTime={snapshot}>{visitDate(snapshot)}, {visitTime(snapshot)}</time>
      </p>}
    </>
  )
}
