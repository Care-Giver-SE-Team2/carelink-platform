import { serviceLabel, visitStatusLabels, visitWeekday } from '../../../features/schedule/presentation'
import { useWeekVisits } from '../../../features/schedule/useWeekVisits'
import styles from './FamilyReports.module.css'

/** ISO-8601 week number of a Monday in YYYY-MM-DD format. */
function isoWeek(monday: string) {
  const date = new Date(`${monday}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + 3)
  const january4 = new Date(Date.UTC(date.getUTCFullYear(), 0, 4))
  return 1 + Math.round(((date.getTime() - january4.getTime()) / 86400000 - 3 + (january4.getUTCDay() + 6) % 7) / 7)
}

/** Desktop only: the visits of the summarised week, beside the summary. Hidden if they cannot be read. */
export function WeekVisits({ elderId, week }: { elderId: number; week: string }) {
  const visits = useWeekVisits(elderId, week)
  if (visits.isError || (visits.data && visits.data.items.length === 0)) return null
  return <section aria-labelledby="week-visits">
    <h2 id="week-visits" className={styles.sectionLabel}>Visits · week {isoWeek(week)}</h2>
    {visits.isPending ? <p className={styles.loading} role="status">Loading visits…</p>
      : <ol className={styles.visitRows} aria-label="Visits this week">
        {visits.data.items.map((visit) => <li key={visit.id} data-status={visit.status}>
          <span className={styles.visitDay}>{visitWeekday(visit.scheduledStart)}</span>
          <span>{serviceLabel(visit.serviceType)}</span>
          <span className={styles.visitStatus}>{visitStatusLabels[visit.status]}</span>
        </li>)}
      </ol>}
  </section>
}
