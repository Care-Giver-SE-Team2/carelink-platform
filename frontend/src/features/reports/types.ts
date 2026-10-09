/**
 * The report module's contract, field for field as the backend publishes it
 * (`docs/api/openapi-draft.yaml`, tag Reports: Report, ReportDetail,
 * ReportAmendment and the list page).
 *
 * Dates and times are strings and stay strings. The period is a Java
 * `LocalDate` ("2026-09-14"); `createdAt` is a `LocalDateTime` with no offset,
 * and with a fractional part only when the application has just written it -
 * a report read back from the database comes whole-second. Typing either as
 * `Date` would invite `new Date(value)`, which reads the text in whatever zone
 * the browser is in. See `presentation.ts` for how they are shown.
 *
 * @author Wang Ziyu
 */

/** Who a report is for. The same facts are filtered differently for each. */
export type ReportAudience = 'FAMILY' | 'REGULATOR' | 'INTERNAL'

/** Only PUBLISHED is produced: reports are filed as they are generated. */
export type ReportStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

/** TEMPLATE until a language-model summary exists; it is not a failure. */
export type ReportGeneratedBy = 'MODEL' | 'TEMPLATE'

/** A correction says the report was wrong; a follow-up records what was done about something in it. */
export type ReportAmendmentKind = 'CORRECTION' | 'FOLLOW_UP'

/**
 * The numbers of the basis a report was filed from: the same for the three
 * readers' versions of one elder's period. A rate or an average with nothing
 * to be taken over is null, not zero.
 */
export interface ReportMetrics {
  visitsPlanned: number
  visitsCompleted: number
  fulfilmentRate: number | null
  vitalsOutOfRange: number
  incidentCount: number
  averageElderRating: number | null
  ratingCount: number
  dataComplete: boolean
}

/** One number a section states: "2 of 3", "66.67 %", "3.5 of 5". */
export interface ReportFigure {
  key: string
  label: string
  value: number
  outOf: number | null
  unit: string | null
}

/**
 * One point of a series: a reading (`low === high`, `at` a date and time) or,
 * in the family's version, a day's lowest and highest (`at` a date).
 */
export interface ReportPoint {
  at: string
  low: number
  high: number
  flagged: boolean
}

/** One measured metric over the period, oldest point first. */
export interface ReportSeries {
  key: string
  label: string
  unit: string | null
  points: ReportPoint[]
}

/** One filed report, without its text - a row of the list, or what Generate hands back. */
export interface Report {
  id: number
  elderId: number
  audience: ReportAudience
  periodStart: string
  periodEnd: string
  status: ReportStatus
  dataComplete: boolean
  missingItems: string[]
  generatedBy: ReportGeneratedBy
  createdAt: string | null
  archivedAt: string | null
  /** The basis the report was filed from; absent or null for one filed before bases were kept. */
  basisId?: number | null
  /** That basis's numbers, on the manager's list and detail. */
  metrics?: ReportMetrics | null
}

/**
 * One titled section. The body is plain text, one item per line; reports since V19 also carry the section's key, the numbers it states and the series it
 * summarises.
 */
export interface ReportSection {
  title: string
  body: string
  key?: string
  figures?: ReportFigure[]
  series?: ReportSeries[]
}

/** A note appended to a report: dated, signed, never edited afterwards. */
export interface ReportAmendment {
  id: number
  note: string
  authorUserId: number
  createdAt: string
  /** CORRECTION when absent: every note was one before follow-ups existed. */
  kind?: ReportAmendmentKind
}

/** One report with its text, its disclaimer if its reader gets one, and every correction. */
export interface ReportDetail extends Report {
  sections: ReportSection[]
  disclaimer: string | null
  amendments: ReportAmendment[]
}

/** Family projection: offset timestamps and corrections without internal author IDs.
 * @author Wang Zhili
 */
export interface FamilyReportDetail extends Report {
  audience: 'FAMILY'
  status: 'PUBLISHED' | 'ARCHIVED'
  sections: ReportSection[]
  disclaimer: string
  amendments: Omit<ReportAmendment, 'authorUserId'>[]
}

/** A saved report's weekly reading view; completeness and corrections are in its detail.
 * @author Wang Zhili
 */
export interface FamilyWeeklySummary {
  reportId: number
  elderId: number
  periodStart: string
  periodEnd: string
  summaryText: string
  generatedBy: ReportGeneratedBy
  disclaimer: string
}

/** One page of the list. */
export interface ReportPage {
  items: Report[]
  page: number
  size: number
  totalElements: number
}

export interface ReportListQuery {
  page: number
  size: number
  elderId?: number
  audience?: ReportAudience
}

/** Body of POST /reports/generate. No elder means every elder with a visit in the period. */
export interface GenerateReportsRequest {
  elderId?: number
  periodStart: string
  periodEnd: string
}
