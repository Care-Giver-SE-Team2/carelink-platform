import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useFamilyReport } from '../../../features/reports/useFamilyReport'
import { familyReportTime, generatedByLabels, reportPeriod, statusLabels } from '../../../features/reports/presentation'
import { ReportDetailFeedback } from './ReportDetailFeedback'
import { ReportCareContext, ReportCompleteness, ReportCorrections } from './ReportNotes'
import { FamilyReportContent } from './FamilyReportContent'
import { isScheduleDate, weekStart } from '../../../features/schedule/presentation'
import styles from './FamilyReports.module.css'

/** Reads the filed family report without rewriting its sections or corrections.
 * @author Wang Zhili
 */
export function FamilyReportDetailPage() {
  const { id = '' } = useParams()
  const [params] = useSearchParams()
  const backParams = new URLSearchParams()
  const elderId = Number(params.get('elderId'))
  const page = Number(params.get('page'))
  if (Number.isSafeInteger(elderId) && elderId > 0) backParams.set('elderId', String(elderId))
  if (Number.isInteger(page) && page > 0 && page <= 2147483647) backParams.set('page', String(page))
  const summaryWeek = params.get('weekStart') ?? ''
  const fromWeekly = isScheduleDate(summaryWeek)
  if (fromWeekly) backParams.set('weekStart', weekStart(summaryWeek))
  const { resource, refresh } = useFamilyReport(id)
  const report = resource.status === 'success' ? resource.data : null

  return <div className={`${styles.reports} ${styles.reportDetail}`}>
    <div className={styles.detailNav}>
      <Link className={styles.backLink} to={`/family/reports${fromWeekly ? '/weekly' : ''}${backParams.size ? `?${backParams}` : ''}`}>
        {fromWeekly ? 'Back to weekly summary' : 'Back to reports'}
      </Link>
      {report && <button onClick={refresh}>Refresh</button>}
    </div>
    {resource.status === 'loading' && <p className={styles.loading} role="status">Loading your report…</p>}
    {resource.status === 'error' && <ReportDetailFeedback error={resource.error} onRetry={refresh} />}
    {report && <article aria-labelledby="report-period">
      <header className={`${styles.hero} ${styles.detailHero}`}>
        <div className={styles.cardTop}>
          <p className={styles.reference}>REPORT #{report.id}</p>
          <span className={styles.badge} data-status={report.status}>{statusLabels[report.status]}</span>
        </div>
        <p className={styles.eyebrow}>Weekly care report</p>
        <h1 id="report-period">{reportPeriod(report.periodStart, report.periodEnd)}</h1>
        <ReportCareContext sections={report.sections} />
        <p>Elder profile #{report.elderId}</p>
        <p className={styles.source}>{generatedByLabels[report.generatedBy]}</p>
        {report.createdAt && <p className={styles.timestamp}>Created <time dateTime={report.createdAt}>{familyReportTime(report.createdAt)}</time></p>}
        {report.archivedAt && <p className={styles.timestamp}>Archived on <time dateTime={report.archivedAt}>{familyReportTime(report.archivedAt)}</time></p>}
        <ReportCompleteness report={report} />
      </header>
      <div className={styles.cards}>
        <FamilyReportContent sections={report.sections} />
        <ReportCorrections amendments={report.amendments} />
      </div>
      <p className={styles.disclaimer}>{report.disclaimer}</p>
      <p className={styles.timestamp}>Times are shown in Singapore time.</p>
    </article>}
  </div>
}
