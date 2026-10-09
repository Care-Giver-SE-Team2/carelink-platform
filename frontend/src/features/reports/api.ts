import { initialiseCsrf } from '../auth/api'
import { api } from '../../shared/api/client'
import type {
  FamilyReportDetail,
  FamilyWeeklySummary,
  GenerateReportsRequest,
  Report,
  ReportAmendment,
  ReportAmendmentKind,
  ReportDetail,
  ReportListQuery,
  ReportPage,
} from './types'

/**
 * One function per endpoint of the report module, and nothing else: no state,
 * no error handling, no formatting. Every write initialises CSRF first, as the
 * incident screens do.
 *
 * There is no function here that edits or deletes a report because there is no
 * endpoint that would: a filed report is only ever appended to.
 *
 * @author Wang Ziyu
 */

/**
 * Reads one page of filed reports, the latest period first.
 * @param query Page, size and the optional elder and reader filters
 * @param signal Cancels an outstanding request
 * @return The page of reports, without their text
 */
export function listReports(
  { page, size, elderId, audience }: ReportListQuery,
  signal?: AbortSignal,
): Promise<ReportPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (elderId !== undefined) query.set('elderId', String(elderId))
  if (audience) query.set('audience', audience)
  return api<ReportPage>('/reports?' + query, { signal })
}

/**
 * Reads one report with its sections, disclaimer and corrections.
 * @param id Report identifier
 * @param signal Cancels an outstanding request
 * @return The whole report
 */
export function getReport(id: number, signal?: AbortSignal): Promise<ReportDetail> {
  return api<ReportDetail>('/reports/' + id, { signal })
}

/** Reads the family projection using the session's resource permissions.
 * @author Wang Zhili
 */
export function getFamilyReport(id: string, signal?: AbortSignal): Promise<FamilyReportDetail> {
  return api<FamilyReportDetail>('/reports/' + encodeURIComponent(id), { signal })
}

/** Reads the exact Monday-to-Sunday week without generating a report.
 * @author Wang Zhili
 */
export function getFamilyWeeklySummary(elderId: number, weekStart: string, signal?: AbortSignal): Promise<FamilyWeeklySummary> {
  const query = new URLSearchParams({ weekStart })
  return api<FamilyWeeklySummary>(`/elders/${elderId}/weekly-summary?${query}`, { signal })
}

/**
 * Generates and files the three readers' reports for a period (UC-MG07 steps 1
 * to 4, by hand). Asking again for a period already on file hands back the
 * same reports rather than new ones.
 * @param request The period, and the elder when only one is wanted
 * @param signal Cancels the request
 * @return Every report now on file for the period, three per elder
 */
export async function generateReports(
  request: GenerateReportsRequest,
  signal?: AbortSignal,
): Promise<Report[]> {
  await initialiseCsrf(signal)
  return api<Report[]>('/reports/generate', {
    method: 'POST',
    body: JSON.stringify(request),
    signal,
  })
}

/**
 * Appends a note to a filed report: a correction, or a follow-up on something
 * it recorded. The report's own text is not touched.
 * @param id Report identifier
 * @param note What the note says
 * @param kind A correction unless said otherwise
 * @param signal Cancels the request
 * @return The stored note
 */
export async function appendAmendment(
  id: number,
  note: string,
  kind: ReportAmendmentKind = 'CORRECTION',
  signal?: AbortSignal,
): Promise<ReportAmendment> {
  await initialiseCsrf(signal)
  return api<ReportAmendment>('/reports/' + id + '/amendments', {
    method: 'POST',
    body: JSON.stringify({ note, kind }),
    signal,
  })
}
