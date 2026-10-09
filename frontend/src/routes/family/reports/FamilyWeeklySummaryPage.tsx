import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useFamilyWeeklySummary } from '../../../features/reports/useFamilyWeeklySummary'
import { generatedByLabels, reportPeriod, statusLabels } from '../../../features/reports/presentation'
import { isScheduleDate, scheduleDateBounds, shiftDays, singaporeToday, weekStart } from '../../../features/schedule/presentation'
import { ReportCareContext, ReportCompleteness, ReportCorrections } from './ReportNotes'
import { ReportListFeedback } from './ReportListFeedback'
import styles from './FamilyReports.module.css'
import { useSelectedElder } from '../components/selectedElder'
import { useIsDesktop } from '../components/useIsDesktop'
import { WeekVisits } from './WeekVisits'
import { FamilyReportContent } from './FamilyReportContent'

/** Reads an exact Singapore calendar week alongside its report's care notes.
 * @author Wang Zhili
 */
export function FamilyWeeklySummaryPage() {
  const [params, setParams] = useSearchParams()
  const [defaultWeek] = useState(() => shiftDays(weekStart(singaporeToday()), -7))
  const requestedWeek = params.get('weekStart') ?? ''
  const week = isScheduleDate(requestedWeek) ? weekStart(requestedWeek) : defaultWeek
  const candidateElder = Number(params.get('elderId'))
  const elderId = Number.isSafeInteger(candidateElder) && candidateElder > 0 ? candidateElder : null
  const candidatePage = Number(params.get('page'))
  const page = Number.isInteger(candidatePage) && candidatePage > 0 && candidatePage <= 2147483647 ? candidatePage : 0
  const { resource, refresh } = useFamilyWeeklySummary({ elderId, week })
  const desktop = useIsDesktop()
  const { setElderId } = useSelectedElder()
  const data = resource.status === 'success' ? resource.data : null
  const weekly = data?.weekly
  const selectedElderId = data?.selectedElderId ?? elderId
  const listParams = new URLSearchParams()
  if (selectedElderId !== null) listParams.set('elderId', String(selectedElderId))
  if (page > 0) listParams.set('page', String(page))
  const detailParams = new URLSearchParams(listParams)
  detailParams.set('weekStart', week)
  const selectWeek = (date: string, nextElder = selectedElderId, nextPage = page) => {
    if (!isScheduleDate(date)) return
    const next = new URLSearchParams()
    if (nextElder !== null) next.set('elderId', String(nextElder))
    if (nextPage > 0) next.set('page', String(nextPage))
    next.set('weekStart', weekStart(date))
    setParams(next)
  }
  const reload = () => {
    selectWeek(week)
    refresh()
  }
  const resetAccess = () => {
    selectWeek(week, null, 0)
    refresh()
  }

  const elderName = data?.elders.find((elder) => elder.id === data.selectedElderId)?.fullName
  const allReports = `/family/reports${listParams.size ? `?${listParams}` : ''}`
  const fullReport = weekly && `/family/reports/${weekly.summary.reportId}?${detailParams}`
  const changeElder = (id: number) => {
    setElderId(id)
    selectWeek(week, id, 0)
  }

  const header = <header className={styles.hero}>
    <div>
      <p className={styles.eyebrow}>{desktop ? ['Reports', elderName].filter(Boolean).join(' · ') : elderName ?? "Your family's care"}</p>
      <h1>Weekly summary</h1>
      <p className={styles.intro}>{reportPeriod(week, shiftDays(week, 6))}</p>
      {weekly && <ReportCareContext sections={weekly.detail.sections} />}
    </div>
    {desktop
      ? fullReport && <div className={styles.headerActions}>
        <Link className={styles.headerAction} to={fullReport}>Read full report</Link>
      </div>
      : <Link className={styles.readLink} to={allReports}>All reports</Link>}
  </header>

  const weekPanel = <section className={styles.weekPanel} aria-label="Choose a week">
    <button className={styles.stepper} aria-label="Previous week" disabled={week <= scheduleDateBounds.min}
      onClick={() => selectWeek(shiftDays(week, -7))}>‹</button>
    <div className={styles.weekCenter}>
      <h2>{reportPeriod(week, shiftDays(week, 6))}</h2>
      <label htmlFor="summary-date" className={styles.hidden}>Choose a date</label>
      <input id="summary-date" type="date" min={scheduleDateBounds.min} max={scheduleDateBounds.max}
        value={week} aria-describedby="summary-date-help"
        onChange={(event) => { if (event.currentTarget.validity.valid) selectWeek(event.currentTarget.value) }} />
      <p id="summary-date-help" className={styles.hidden}>Choose any date to read its Monday–Sunday week.</p>
    </div>
    <button className={styles.stepper} aria-label="Next week" disabled={week >= weekStart(scheduleDateBounds.max)}
      onClick={() => selectWeek(shiftDays(week, 7))}>›</button>
    <div className={styles.weekFoot}>
      <p>Weeks follow Singapore time.</p>
      <button onClick={() => selectWeek(singaporeToday())}>This week</button>
    </div>
  </section>

  const status = <>
    {resource.status === 'loading' && <p className={styles.loading} role="status">Loading your weekly summary…</p>}
    {resource.status === 'error' && <ReportListFeedback error={resource.error} onRetry={reload} onResetAccess={resetAccess} />}
  </>

  // Phone only: on desktop the elder is chosen in the side rail. Refresh sits with the summary.
  const toolbar = data && data.elders.length > 0 && !desktop && <div className={styles.toolbar}>
    <div className={styles.elderPicker}>
      <label htmlFor="summary-elder">Care for</label>
      <select id="summary-elder" value={data.selectedElderId ?? ''} onChange={(event) => changeElder(Number(event.target.value))}>
        {data.elders.map((elder) => <option key={elder.id} value={elder.id}>{elder.fullName}</option>)}
      </select>
    </div>
  </div>
  const refreshButton = <button className={styles.refresh} onClick={reload}>Refresh</button>

  const content = data && <>
    {/* With no summary to sit beside, Refresh gets its own row above the message card. */}
    {(data.elders.length === 0 || !weekly) && <div className={styles.refreshBar}>{refreshButton}</div>}
    {data.elders.length === 0 ? <section className={styles.state}>
      <h2>No linked elders yet</h2>
      <p>Your care reports will be available once a family binding is active.</p>
      <Link to="/family/service-applications">View my applications</Link>
    </section> : !weekly && <section className={styles.state}>
      <h2>No report for this week</h2>
      <p>No published or archived report is available for the selected week. Choose another week or check again later.</p>
    </section>}
    {weekly && <article aria-label="Weekly care summary" className={styles.summaryArticle}>
      <div className={styles.tagRow}>
        <span className={styles.modelTag}>{generatedByLabels[weekly.summary.generatedBy]}</span>
        <span>from the week's records</span>
        {refreshButton}
      </div>
      <section className={styles.reportMeta}>
        <ReportCompleteness report={weekly.detail} />
        <div className={styles.cardFoot}>
          <span className={styles.reference}>REPORT #{weekly.summary.reportId}</span>
          <span className={styles.badge} data-status={weekly.detail.status}>{statusLabels[weekly.detail.status]}</span>
        </div>
      </section>
      <FamilyReportContent sections={weekly.detail.sections} />
      <p className={styles.disclaimer}>{weekly.summary.disclaimer}</p>
      <ReportCorrections amendments={weekly.detail.amendments} />
      {!desktop && fullReport && <div className={styles.actions}>
        <Link className={styles.secondary} to={fullReport}>Read full report</Link>
      </div>}
      <p className={styles.timestamp}>Dates and times are shown in Singapore time.</p>
    </article>}
  </>

  if (desktop) {
    return <div className={styles.weekly}>
      {header}
      <div className={styles.split}>
        <div className={styles.splitMain}>{status}{content}</div>
        <div className={styles.splitSide}>
          {/* Moving between reports lives in this column: every report here, another week below. */}
          <Link className={styles.sideLink} to={allReports}>All reports <span aria-hidden="true">→</span></Link>
          {weekPanel}
          {toolbar}
          {data?.selectedElderId != null && <WeekVisits elderId={data.selectedElderId} week={week} />}
        </div>
      </div>
    </div>
  }
  return <div className={styles.weekly}>
    {header}
    {weekPanel}
    {status}
    {toolbar}
    {content}
  </div>
}
