import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { generateReports } from '../../../../features/reports/api'
import {
  audienceLabels,
  audienceNotes,
  metricsLine,
  previousWeek,
  problemDetail,
  reportPeriod,
  reportTime,
  statusLabels,
  todayIso,
} from '../../../../features/reports/presentation'
import type { ReportAudience } from '../../../../features/reports/types'
import { useReportPage } from '../../../../features/reports/useReportQueries'
import { ManagerShell } from '../../components/ManagerShell'
import { ReportFeedback } from './ReportFeedback'
import styles from './Reports.module.css'

const AUDIENCES: ReportAudience[] = ['FAMILY', 'REGULATOR', 'INTERNAL']

/** Ten elder-weeks of three versions each. */
const PAGE_SIZE = 30

const WHOLE_NUMBER = /^\d+$/

function asAudience(value: string | null): ReportAudience | undefined {
  return value && AUDIENCES.includes(value as ReportAudience) ? (value as ReportAudience) : undefined
}

function asElderId(value: string | null): number | undefined {
  return value && WHOLE_NUMBER.test(value) && Number(value) > 0 ? Number(value) : undefined
}

/**
 * UC-MG07 — the filed reports, and the manager's way of generating a period's
 * reports without waiting for Sunday's run.
 *
 * The form offers last week by default. An empty elder means every elder who
 * had a visit in the period, which is what the weekly run does; anything typed
 * there that is not a whole number is refused here rather than sent, because
 * the server would read a blank as "everybody". Generating a period that is
 * already on file hands back the same reports - the list is read again either
 * way, so what it shows is what is on file.
 */
export default function ReportList() {
  const [params, setParams] = useSearchParams()
  const audience = asAudience(params.get('audience'))
  const elderFilter = asElderId(params.get('elderId'))
  const { resource, refresh } = useReportPage({
    page: 0,
    size: PAGE_SIZE,
    elderId: elderFilter,
    audience,
  })

  const [lastWeek] = useState(() => previousWeek(todayIso()))
  const [elderId, setElderId] = useState('')
  const [periodStart, setPeriodStart] = useState(lastWeek.start)
  const [periodEnd, setPeriodEnd] = useState(lastWeek.end)
  const [busy, setBusy] = useState(false)
  const [formProblem, setFormProblem] = useState<string | null>(null)
  const [generateError, setGenerateError] = useState<unknown>(null)
  const [filed, setFiled] = useState<string | null>(null)

  function filterBy(name: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(name, value)
    else next.delete(name)
    setParams(next)
  }

  async function generate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormProblem(null)
    setGenerateError(null)
    setFiled(null)

    const elder = elderId.trim()
    if (elder !== '' && !WHOLE_NUMBER.test(elder)) {
      setFormProblem('Elder # has to be a whole number, or left empty for every elder with a visit.')
      return
    }

    setBusy(true)
    try {
      const reports = await generateReports({
        elderId: elder === '' ? undefined : Number(elder),
        periodStart,
        periodEnd,
      })
      const elders = new Set(reports.map((report) => report.elderId)).size
      setFiled(
        reports.length === 0
          ? `Nobody had a visit between ${reportPeriod(periodStart, periodEnd)}, so there was nothing to report.`
          : `${reports.length} reports on file for ${reportPeriod(periodStart, periodEnd)}, ` +
              `${elders === 1 ? 'one elder' : `${elders} elders`}.`,
      )
      refresh()
    } catch (error) {
      setGenerateError(error)
    } finally {
      setBusy(false)
    }
  }

  return (
    <ManagerShell>
      <div className={styles.page}>
        <div className={styles.pageHead}>
          <h1>Periodic reports</h1>
          <span className={styles.meta}>
            {resource.status === 'success'
              ? `${resource.data.totalElements} on file`
              : 'Loading…'}
          </span>
          <button
            type="button"
            className={styles.textButton}
            onClick={refresh}
            disabled={resource.status === 'loading'}
          >
            Refresh
          </button>
        </div>

        <form className={styles.panel} onSubmit={generate}>
          <h2 className={styles.panelHeading}>Generate a period's reports</h2>
          <div className={styles.fields}>
            <label>
              Elder #
              <input
                inputMode="numeric"
                value={elderId}
                placeholder="every elder"
                onChange={(event) => setElderId(event.target.value)}
              />
            </label>
            <label>
              From
              <input
                type="date"
                value={periodStart}
                onChange={(event) => setPeriodStart(event.target.value)}
              />
            </label>
            <label>
              To
              <input
                type="date"
                value={periodEnd}
                onChange={(event) => setPeriodEnd(event.target.value)}
              />
            </label>
            <button type="submit" disabled={busy}>
              {busy ? 'Generating…' : 'Generate'}
            </button>
          </div>
          <p className={styles.hint}>
            One report for each reader: family, regulator and internal. A period already on
            file is not generated again, and a filed report is never changed - corrections are
            appended to it.
          </p>
        </form>

        {formProblem !== null && (
          <p className={styles.inlineError} role="alert">
            {formProblem}
          </p>
        )}
        {generateError !== null && (
          <p className={styles.inlineError} role="alert">
            {problemDetail(generateError)}
          </p>
        )}
        {filed !== null && (
          <p className={styles.notice} role="status">
            {filed}
          </p>
        )}

        <div className={styles.filters}>
          <label>
            Reader
            <select value={audience ?? ''} onChange={(event) => filterBy('audience', event.target.value)}>
              <option value="">All three</option>
              {AUDIENCES.map((value) => (
                <option key={value} value={value}>
                  {audienceLabels[value]}
                </option>
              ))}
            </select>
          </label>
          <label>
            Elder #
            <input
              inputMode="numeric"
              value={params.get('elderId') ?? ''}
              placeholder="all"
              onChange={(event) => filterBy('elderId', event.target.value.trim())}
            />
          </label>
        </div>

        {resource.status === 'loading' && <p className={styles.note}>Loading the reports…</p>}
        {resource.status === 'error' && <ReportFeedback error={resource.error} onRetry={refresh} />}

        {resource.status === 'success' &&
          (resource.data.items.length === 0 ? (
            <p className={styles.note}>
              No reports are on file here yet. Generate a period above, or wait for Sunday's run.
            </p>
          ) : (
            <div className={styles.tableScroll}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th scope="col">Period</th>
                      <th scope="col">Elder</th>
                      <th scope="col">Reader</th>
                      <th scope="col">Status</th>
                      <th scope="col">Data</th>
                      <th scope="col">The period</th>
                      <th scope="col">Filed</th>
                    </tr>
                  </thead>
                  <tbody>
                    {resource.data.items.map((report) => (
                      <tr key={report.id}>
                        <td>
                          <Link to={'/manager/reports/' + report.id}>
                            {reportPeriod(report.periodStart, report.periodEnd)}
                          </Link>
                        </td>
                        <td className={styles.data}>#{report.elderId}</td>
                        <td>
                          <span className={styles.readerTag} title={audienceNotes[report.audience]}>
                            {audienceLabels[report.audience]}
                          </span>
                        </td>
                        <td>
                          <span className={styles.statusTag}>{statusLabels[report.status]}</span>
                        </td>
                        <td className={styles.data}>
                          {report.dataComplete ? (
                            <span className={styles.complete}>complete</span>
                          ) : (
                            <span className={styles.incompleteMark}>
                              incomplete · {report.missingItems.length} missing
                            </span>
                          )}
                        </td>
                        <td className={styles.data}>{metricsLine(report.metrics)}</td>
                        <td className={styles.data}>{reportTime(report.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
            </div>
          ))}
      </div>
    </ManagerShell>
  )
}
