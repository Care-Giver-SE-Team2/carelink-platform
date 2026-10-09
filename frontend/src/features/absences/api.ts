import { initialiseCsrf } from '../auth/api'
import { api } from '../../shared/api/client'
import type {
  AbsenceCase,
  AbsenceStatus,
  AbsenceSummary,
  FamilyChange,
  FamilyDecision,
  OwnAbsence,
  RecordAbsence,
  ReRostered,
  RequestAbsence,
  RosteringObjective,
} from './types'

/**
 * One function per endpoint of UC-MG04 and nothing else. Every write
 * initialises CSRF first, as the incident functions do.
 *
 * @author Wang Ziyu
 */

/** The manager's list of absences, optionally one status only. */
export function listAbsences(status?: AbsenceStatus, signal?: AbortSignal): Promise<AbsenceSummary[]> {
  const query = status ? '?status=' + status : ''
  return api<AbsenceSummary[]>('/absences' + query, { signal })
}

/** One absence: what is left to re-roster and every change with its candidates. */
export function getAbsence(id: number, signal?: AbortSignal): Promise<AbsenceCase> {
  return api<AbsenceCase>('/absences/' + id, { signal })
}

/** UC-MG04 step 1: the manager records an absence; it is approved at once. */
export async function recordAbsence(body: RecordAbsence, signal?: AbortSignal): Promise<AbsenceSummary> {
  await initialiseCsrf(signal)
  return api<AbsenceSummary>('/absences', { method: 'POST', body: JSON.stringify(body), signal })
}

/** A manager accepts an absence a caregiver asked for. */
export async function approveAbsence(id: number, signal?: AbortSignal): Promise<AbsenceSummary> {
  await initialiseCsrf(signal)
  return api<AbsenceSummary>('/absences/' + id + '/approve', { method: 'POST', signal })
}

/** A manager turns an absence a caregiver asked for down. */
export async function rejectAbsence(id: number, signal?: AbortSignal): Promise<AbsenceSummary> {
  await initialiseCsrf(signal)
  return api<AbsenceSummary>('/absences/' + id + '/reject', { method: 'POST', signal })
}

/** Steps 2 to 4: re-roster now, ranked by the chosen objective. */
export async function rerosterAbsence(
  id: number,
  objective: RosteringObjective,
  signal?: AbortSignal,
): Promise<ReRostered> {
  await initialiseCsrf(signal)
  return api<ReRostered>('/absences/' + id + '/rerostering-runs', {
    method: 'POST',
    body: JSON.stringify({ objective }),
    signal,
  })
}

/** The manager hand-picks who takes a vacated visit, in place of the search's pick. */
export async function assignCaregiver(
  absenceId: number,
  changeId: number,
  caregiverId: number,
  signal?: AbortSignal,
): Promise<AbsenceCase> {
  await initialiseCsrf(signal)
  return api<AbsenceCase>('/absences/' + absenceId + '/changes/' + changeId + '/assignment', {
    method: 'POST',
    body: JSON.stringify({ caregiverId }),
    signal,
  })
}

/** Step 7: the manager confirms every vacated visit is accounted for. */
export async function confirmCoverage(id: number, signal?: AbortSignal): Promise<AbsenceCase> {
  await initialiseCsrf(signal)
  return api<AbsenceCase>('/absences/' + id + '/coverage-confirmation', { method: 'POST', signal })
}

/** The family's changes, those still waiting for an answer first. */
export function listFamilyChanges(signal?: AbortSignal): Promise<FamilyChange[]> {
  return api<FamilyChange[]>('/roster-changes', { signal })
}

/** Step 5: keep the suggestion or pick another option, move the visit, or skip it. */
export async function decideChange(
  id: number,
  decision: FamilyDecision,
  signal?: AbortSignal,
): Promise<FamilyChange> {
  await initialiseCsrf(signal)
  return api<FamilyChange>('/roster-changes/' + id + '/decision', {
    method: 'POST',
    body: JSON.stringify(decision),
    signal,
  })
}

/** UC-CG02: the signed-in caregiver's own absences. */
export function listOwnAbsences(signal?: AbortSignal): Promise<OwnAbsence[]> {
  return api<OwnAbsence[]>('/caregivers/me/absences', { signal })
}

/** UC-CG02: the signed-in caregiver asks for leave; a manager reviews it. */
export async function requestAbsence(body: RequestAbsence, signal?: AbortSignal): Promise<OwnAbsence> {
  await initialiseCsrf(signal)
  return api<OwnAbsence>('/caregivers/me/absences', { method: 'POST', body: JSON.stringify(body), signal })
}

