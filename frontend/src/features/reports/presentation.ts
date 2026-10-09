import { ApiError } from '../../shared/api/client'
import type {
  ReportAmendmentKind,
  ReportAudience,
  ReportFigure,
  ReportGeneratedBy,
  ReportMetrics,
  ReportPoint,
  ReportSection,
  ReportStatus,
} from './types'

/**
 * Everything the report screens display but do not fetch: labels, the date
 * formats, the default period and the one way of reading an error body.
 *
 * Pure functions, so the parts that go wrong quietly - a date read in the
 * wrong zone, a week that starts on the wrong day, an error that says nothing
 * - are testable without rendering a page.
 *
 * @author Wang Ziyu
 */

export const audienceLabels: Record<ReportAudience, string> = {
  FAMILY: 'Family',
  REGULATOR: 'Regulator',
  INTERNAL: 'Internal',
}

/** What each reader's version leaves out, in a line; shown so nobody has to open three reports to find out. */
export const audienceNotes: Record<ReportAudience, string> = {
  FAMILY: 'Caregivers by name, vital signs as ranges, no medical notes or inspection findings, with the medical disclaimer',
  REGULATOR: 'The full record with the elder and caregivers by number; notes, comments and findings counted, not quoted',
  INTERNAL: 'Everything, names, medical notes and findings included',
}

export const amendmentKindLabels: Record<ReportAmendmentKind, string> = {
  CORRECTION: 'Correction',
  FOLLOW_UP: 'Follow-up',
}

export const statusLabels: Record<ReportStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  ARCHIVED: 'Archived',
}

export const generatedByLabels: Record<ReportGeneratedBy, string> = {
  MODEL: 'Language-model summary',
  TEMPLATE: 'Structured template',
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/*
 * The backend sends the period as a LocalDate ("2026-09-14") and createdAt as
 * a LocalDateTime with no offset. Both are read by their characters and never
 * through `new Date(value)`: an offsetless timestamp read by the browser lands
 * in the browser's zone, and a bare date read that way is midnight UTC, which
 * is the previous evening anywhere west of Greenwich.
 */
const DATE = /^(\d{4})-(\d{2})-(\d{2})/
const TIMESTAMP = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/**
 * Formats a date.
 * @param value "2026-09-14", or the date part of a timestamp
 * @return "14 Sep 2026", or a placeholder when there is nothing to show
 */
export function reportDay(value: string | null): string {
  const match = value ? DATE.exec(value) : null
  if (!match) return 'Not available'
  return `${Number(match[3])} ${MONTHS[Number(match[2]) - 1]} ${match[1]}`
}

/**
 * Formats a period, naming the year once when both ends share it.
 * @param start First day, "2026-09-14"
 * @param end Last day, "2026-09-20"
 * @return "14 Sep – 20 Sep 2026", "28 Dec 2026 – 3 Jan 2027" across a year end, or one day
 */
export function reportPeriod(start: string, end: string): string {
  const from = DATE.exec(start)
  const to = DATE.exec(end)
  if (!from || !to) return 'Not available'
  if (from[0] === to[0]) return reportDay(start)
  const first = from[1] === to[1]
    ? `${Number(from[3])} ${MONTHS[Number(from[2]) - 1]}`
    : reportDay(start)
  return `${first} – ${reportDay(end)}`
}

/**
 * Formats a timestamp.
 * @param value "2026-09-20T23:00:00", with or without fractional seconds
 * @return "20 Sep 2026 23:00", or a placeholder when there is nothing to show
 */
export function reportTime(value: string | null): string {
  const match = value ? TIMESTAMP.exec(value) : null
  if (!match) return 'Not available'
  return `${Number(match[3])} ${MONTHS[Number(match[2]) - 1]} ${match[1]} ${match[4]}:${match[5]}`
}

/** Family responses include offsets; display their instants in Singapore time.
 * @author Wang Zhili
 */
export function familyReportTime(value: string): string {
  return new Intl.DateTimeFormat('en-SG', {
    timeZone: 'Asia/Singapore', day: 'numeric', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).format(new Date(value))
}

/**
 * Today's date on the browser's own calendar, in the backend's LocalDate form.
 * The browser is where the manager is, which is the calendar the default
 * period should follow.
 * @return "YYYY-MM-DD"
 */
export function todayIso(): string {
  const now = new Date()
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/**
 * The Monday-to-Sunday week before the one a day falls in: what the generate
 * form offers by default ("last week").
 *
 * Worked out with Date.UTC on the date's own numbers, so no zone is involved
 * and the answer is the same on every machine.
 * @param today "YYYY-MM-DD"
 * @return The first and last day of last week, in the same form
 */
export function previousWeek(today: string): { start: string; end: string } {
  const match = DATE.exec(today)
  if (!match) throw new Error('Not a date: ' + today)
  const day = Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
  const sinceMonday = (new Date(day).getUTCDay() + 6) % 7
  const DAY = 24 * 60 * 60 * 1000
  const thisMonday = day - sinceMonday * DAY
  return { start: isoDay(thisMonday - 7 * DAY), end: isoDay(thisMonday - DAY) }
}

function isoDay(utcMillis: number): string {
  const date = new Date(utcMillis)
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`
}

/**
 * A section body as lines. The backend writes one item per line, and indents
 * the steps of an incident's timeline by two spaces under the incident.
 * @param body Section text
 * @return Each line, with whether it is one of those indented steps
 */
export function sectionLines(body: string): { text: string; nested: boolean }[] {
  return body
    .split('\n')
    .filter((line) => line.trim() !== '')
    .map((line) => ({ text: line.trim(), nested: line.startsWith('  ') }))
}

/**
 * Reads the message out of an RFC 9457 problem body.
 *
 * The same reading as the incident screens' - the shared client looks for a
 * `message` field the backend never sends, so the sentence a manager needs is
 * in `detail`, and a 400's complaints are in `fields`. Kept as its own copy
 * rather than imported across features; the day it moves to `shared/`, both go.
 *
 * @param error Whatever was thrown
 * @return The server's own words when it supplied any, otherwise a plain fallback
 */
export function problemDetail(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'The request could not be completed. Check your connection and try again.'
  }

  const body = error.body
  if (body && typeof body === 'object') {
    const problem = body as { detail?: unknown; title?: unknown; fields?: unknown }
    const headline =
      typeof problem.detail === 'string' && problem.detail.trim()
        ? problem.detail
        : typeof problem.title === 'string' && problem.title.trim()
          ? problem.title
          : ''
    const fields =
      problem.fields && typeof problem.fields === 'object'
        ? Object.entries(problem.fields as Record<string, unknown>).map(
            ([name, message]) => `${name}: ${String(message)}`,
          )
        : []

    if (headline || fields.length) return [headline, ...fields].filter(Boolean).join(' — ')
  }

  return error.message
}

/**
 * A number as the report wrote it: no trailing zeros, no thousands separator.
 * @param value 66.67, 3.5, 2
 * @return "66.67", "3.5", "2"
 */
export function reportNumber(value: number): string {
  return String(Number(value.toFixed(2)))
}

/**
 * One figure in words.
 * @param figure A count, a share of a total or a percentage
 * @return "2 of 3", "66.67%", "1"
 */
export function figureText(figure: ReportFigure): string {
  if (figure.unit === '%') return reportNumber(figure.value) + '%'
  if (figure.outOf !== null) return `${reportNumber(figure.value)} of ${reportNumber(figure.outOf)}`
  return reportNumber(figure.value)
}

/**
 * The basis's numbers in one line for a row of the list.
 * @param metrics The numbers, or nothing for a report filed before bases were kept
 * @return "2 of 3 visits (66.67%) · 1 incident · rated 3.5", or a dash
 */
export function metricsLine(metrics: ReportMetrics | null | undefined): string {
  if (!metrics) return '—'
  const visits =
    metrics.visitsPlanned === 0
      ? 'no visits planned'
      : `${metrics.visitsCompleted} of ${metrics.visitsPlanned} visits` +
        (metrics.fulfilmentRate === null ? '' : ` (${reportNumber(metrics.fulfilmentRate)}%)`)
  const incidents = `${metrics.incidentCount} ${metrics.incidentCount === 1 ? 'incident' : 'incidents'}`
  const rating = metrics.averageElderRating === null ? 'not rated' : `rated ${reportNumber(metrics.averageElderRating)}`
  return [visits, incidents, rating].join(' · ')
}

/** Where a series' points sit in a small chart, in the chart's own units. */
export interface SparklineShape {
  /** SVG path through the middle of each point. */
  line: string
  points: { x: number; low: number; high: number; mid: number; flagged: boolean }[]
  min: number
  max: number
}

/**
 * Lays a series out in a small chart: points evenly spaced left to right in
 * the order they were taken, values scaled between the lowest and the highest
 * of the series. A point that is a day's range keeps both ends, so the chart
 * can draw it as a bar rather than pretending to a single reading.
 * @param points Oldest first
 * @param width Chart width
 * @param height Chart height
 * @param pad Space kept clear at every edge
 * @return The line and each point's position, or null with nothing to draw
 */
export function sparkline(points: ReportPoint[], width: number, height: number, pad = 4): SparklineShape | null {
  if (points.length === 0) return null
  const min = Math.min(...points.map((point) => point.low))
  const max = Math.max(...points.map((point) => point.high))
  const span = max - min
  const y = (value: number) => (span === 0 ? height / 2 : pad + ((max - value) / span) * (height - 2 * pad))
  const step = points.length === 1 ? 0 : (width - 2 * pad) / (points.length - 1)
  const placed = points.map((point, index) => ({
    x: points.length === 1 ? width / 2 : pad + index * step,
    low: y(point.low),
    high: y(point.high),
    mid: y((point.low + point.high) / 2),
    flagged: point.flagged,
  }))
  const line = placed.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)} ${point.mid.toFixed(1)}`).join(' ')
  return { line, points: placed, min, max }
}

/**
 * The key a section is found by. Sections filed before keys existed have none,
 * and get the one the backend would make from the title.
 * @param section A section of a report
 * @return "vital-signs", "ratings-and-spot-checks"
 */
export function sectionKey(section: Pick<ReportSection, 'key' | 'title'>): string {
  if (section.key) return section.key
  return section.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
}

/** How a status reads at a glance: done, waiting, wrong, or neither. */
export type ReportTone = 'ok' | 'warn' | 'bad' | 'neutral'

/** One line of a section body, split into what a row shows. */
export interface ReportLine {
  /** The line as the report wrote it. */
  text: string
  /** A timeline step under the line before it. */
  nested: boolean
  /** "Tue 15 Sep 10:15", "Mon 14 Sep" or "14 Sep – 20 Sep", when the line has one. */
  time: string | null
  /** The other parts, in the report's order, without the time and the status. */
  parts: string[]
  /** The part that says how the thing stands, when there is one. */
  status: { text: string; tone: ReportTone } | null
}

const DAY_PART = /^(Mon|Tue|Wed|Thu|Fri|Sat|Sun) \d{1,2} (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)( \d{2}:\d{2})?$/
const SPAN_PART = /^\d{1,2} (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) – \d{1,2} (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)$/

/*
 * The states the report's sections write, as the assemblers word them
 * (ReportAssembler and the three readers' versions). A part has to be one of
 * these whole; "evidence 2 of 2 verified" is not "verified".
 */
const STATUSES: [RegExp, ReportTone][] = [
  [/^(verified|done|approved|meets the standard)$/, 'ok'],
  [/^resolved( .+)?$/i, 'ok'],
  [/^approved and booked( as visit \d+)?$/, 'ok'],
  [/ took the visit$/, 'ok'],
  [/^replaced by .+$/, 'ok'],
  [/^(scheduled|caregiver arrived|in progress|awaiting the elder's confirmation|planned)$/, 'warn'],
  [/^(still being followed up|waiting for the family's choice|awaiting the family|awaiting the family's approval|awaiting the family's consent)$/, 'warn'],
  [/^(OPEN|UNRESOLVED_ESCALATED)$/, 'bad'],
  [/^(ACKNOWLEDGED|IN_PROGRESS)$/, 'warn'],
  [/^(ended in an exception|needs improvement|the caregiver did not turn up|no replacement found yet|uncovered|out of range)$/, 'bad'],
  [/^(cancelled|cancelled at the family's request|closed without the elder's confirmation|declined|declined by the family|withdrawn|called off|skipped|skipped at the family's request|moved to another time)$/, 'neutral'],
  [/^rescheduled( with .+)?$/, 'neutral'],
]

/**
 * Splits a body line on the report's own separator, " · ", into the time it
 * happened, the state it ended in, and everything else. A line the report wrote
 * as a sentence comes back with no time, one part and no status, so a screen
 * can show it as it is.
 * @param line A line of a section body, as sectionLines gives it
 * @return The line in parts
 */
export function reportLine(line: { text: string; nested: boolean }): ReportLine {
  const all = line.text.split(' · ')
  const timeAt = all.findIndex((part) => DAY_PART.test(part) || SPAN_PART.test(part))
  const rest = timeAt < 0 ? all : all.filter((_, index) => index !== timeAt)
  let status: ReportLine['status'] = null
  const parts: string[] = []
  for (const part of rest) {
    const match: [RegExp, ReportTone] | undefined =
      status === null && rest.length > 1 ? STATUSES.find(([pattern]) => pattern.test(part)) : undefined
    if (match) status = { text: part, tone: match[1] }
    else parts.push(part)
  }
  return { text: line.text, nested: line.nested, time: timeAt < 0 ? null : all[timeAt], parts, status }
}
