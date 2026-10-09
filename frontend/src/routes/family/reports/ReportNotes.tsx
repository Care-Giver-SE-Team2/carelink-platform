import type { FamilyReportDetail } from '../../../features/reports/types'
import { amendmentKindLabels, familyReportTime } from '../../../features/reports/presentation'
import styles from './FamilyReports.module.css'

/** Read the filed overview, never today's assignment: historical reports keep their original context. */
export function ReportCareContext({ sections }: { sections: FamilyReportDetail['sections'] }) {
  const overview = sections.find((section) => section.key === 'overview' || section.title === 'Overview')
  const lines = overview?.body.split('\n').map((line) => line.trim()) ?? []
  const caregiver = lines.find((line) => line.startsWith('Main caregiver: '))?.slice('Main caregiver: '.length).replace(/\.$/, '')
  const plan = lines.find((line) => line.startsWith('Care plan version ') || line === 'No care plan in force.')
    ?.replace(/\.$/, '').replace(/^Care plan version /, 'Care plan v').replace(/ h a week$/, ' h/week')
  return <p className={styles.careContext} aria-label="Care context at report generation">
    <span>Main caregiver: {caregiver || 'Not recorded'}</span>
    <span aria-hidden="true"> · </span>
    <span>{plan ?? 'Care plan: Not recorded'}</span>
  </p>
}

/** The same completeness notice accompanies both the report and weekly summary.
 * @author Wang Zhili
 */
export function ReportCompleteness({ report }: { report: Pick<FamilyReportDetail, 'dataComplete' | 'missingItems'> }) {
  return <div className={styles.completeness} data-complete={report.dataComplete}>
    <p>{report.dataComplete ? 'Records complete' : 'Some care records are missing'}</p>
    {report.missingItems.length > 0 && <details>
      <summary>View {report.missingItems.length} missing {report.missingItems.length === 1 ? 'record' : 'records'}</summary>
      <ul>{report.missingItems.map((item, index) => <li key={index}>{item}</li>)}</ul>
    </details>}
  </div>
}

/** Corrections remain separate from the original report or summary text.
 * @author Wang Zhili
 */
export function ReportCorrections({ amendments }: { amendments: FamilyReportDetail['amendments'] }) {
  return <section className={styles.card} aria-labelledby="report-corrections">
    <h2 id="report-corrections">Corrections and follow-ups</h2>
    {amendments.length === 0 && <p className={styles.body}>No corrections or follow-ups have been added.</p>}
    {amendments.map((amendment) => <div className={styles.correction} key={amendment.id}>
      <span className={styles.amendmentKind}>{amendmentKindLabels[amendment.kind ?? 'CORRECTION']}</span>
      <time className={styles.timestamp} dateTime={amendment.createdAt}>{familyReportTime(amendment.createdAt)}</time>
      <p className={styles.body}>{amendment.note}</p>
    </div>)}
  </section>
}
