/**
 * The shapes of UC-MG04 re-roster on caregiver absence, exactly as the
 * rostering module's AbsenceQueryService and AbsenceController send them.
 * Times are Singapore local time without an offset, as everywhere else in
 * CareLink.
 *
 * @author Wang Ziyu
 */

export type AbsenceType = 'SICK' | 'ANNUAL' | 'EMERGENCY' | 'OTHER'

export type AbsenceStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

/** The objective tabs of the roster screen; COST is refused by the backend. */
export type RosteringObjective = 'CONTINUITY' | 'EVEN_WORKLOAD' | 'TRAVEL_TIME'

export type ChangeStatus = 'AWAITING_FAMILY' | 'UNCOVERED' | 'RESOLVED'

export type ChangeOutcome = 'REPLACED' | 'RESCHEDULED' | 'SKIPPED' | 'WITHDRAWN'

export type DecidedBy = 'FAMILY' | 'DEFAULT_PLAN' | 'MANAGER'

export type CandidateOutcome = 'SELECTED' | 'SUGGESTED' | 'EXCLUDED'

export type CheckResult = 'PASS' | 'FAIL' | 'NOT_APPLICABLE'

export type RuleKind = 'HARD' | 'SOFT'

/** One absence and how far its re-rostering has got. */
export type AbsenceSummary = {
  id: number
  caregiverId: number
  caregiverName: string
  type: AbsenceType
  startDate: string
  endDate: string
  reason: string | null
  status: AbsenceStatus
  reviewedByUserId: number | null
  coverageConfirmedAt: string | null
  notYetRerostered: number
  awaitingFamily: number
  uncovered: number
  settled: number
}

export type VacatedVisit = {
  visitId: number
  elderId: number
  elderName: string
  serviceType: string | null
  start: string
  end: string | null
}

export type Person = { caregiverId: number; name: string }

export type CheckView = {
  code: string | null
  name: string | null
  kind: RuleKind | null
  result: CheckResult
  detail: string | null
}

export type CandidateView = {
  caregiverId: number
  name: string
  rank: number | null
  score: number | null
  reason: string | null
  outcome: CandidateOutcome
  excludedBy: string | null
  checks: CheckView[]
}

/** One vacated visit, with the run behind its current state. */
export type ChangeView = {
  id: number
  visitId: number
  elderId: number
  elderName: string
  visitStart: string
  visitEnd: string | null
  status: ChangeStatus
  outcome: ChangeOutcome | null
  decidedBy: DecidedBy | null
  decidedAt: string | null
  respondBy: string | null
  note: string | null
  absentCaregiver: Person | null
  proposedCaregiver: Person | null
  assignedCaregiver: Person | null
  rescheduledVisitId: number | null
  rescheduledStart: string | null
  incidentId: number | null
  rosteringRunId: number | null
  objective: RosteringObjective | 'COST' | null
  candidates: CandidateView[]
  /** Whether the manager may hand-pick who takes it: nobody was found, or the replacement was not the family's choice. */
  managerMayAssign: boolean
}

export type AbsenceCase = {
  absence: AbsenceSummary
  notYetRerostered: VacatedVisit[]
  changes: ChangeView[]
}

export type ReRosterOutcome = {
  absenceId: number
  runId: number | null
  searched: number
  offered: number
  settled: number
  uncovered: number
}

export type ReRostered = { outcome: ReRosterOutcome; absence: AbsenceCase }

export type RecordAbsence = {
  caregiverId: number
  type: AbsenceType
  startDate: string
  endDate: string
  reason?: string
}

/** A caregiver's own absence (UC-CG02). */
export type OwnAbsence = {
  id: number
  type: AbsenceType
  startDate: string
  endDate: string
  reason: string | null
  status: AbsenceStatus
}

export type RequestAbsence = Omit<RecordAbsence, 'caregiverId'>

export type FamilyOption = { caregiverId: number; name: string; rank: number; reason: string | null }

/** A change as the family sees it. */
export type FamilyChange = {
  id: number
  elderId: number
  elderName: string
  visitStart: string
  visitEnd: string | null
  usualCaregiverName: string
  status: ChangeStatus
  respondBy: string | null
  suggestedCaregiverId: number | null
  options: FamilyOption[]
  outcome: ChangeOutcome | null
  decidedBy: DecidedBy | null
  decidedAt: string | null
  assignedCaregiver: Person | null
  rescheduledStart: string | null
  note: string | null
}

export type FamilyDecision =
  | { choice: 'CHANGE_CAREGIVER'; caregiverId?: number }
  | { choice: 'RESCHEDULE'; newStart: string }
  | { choice: 'SKIP' }
