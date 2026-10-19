import { fetchElderList } from '../../../shared/api/profile'
import { ageFromDateOfBirth } from '../lib/age'

export type PlanStatus = 'published' | 'draft' | 'stopped' | 'none'

export type ElderRow = {
  id: string
  name: string
  /** Null when no date of birth is on record. */
  age: number | null
  street: string
  sector: string
  planStatus: PlanStatus
  planVersion: number | null
  /** A published version is in force — still true while a newer version is being drafted. */
  hasPublishedPlan: boolean
  /** The family has applied for care (intake or service application). */
  hasCareRequest: boolean
  primaryCaregiver: string | null
  /** Caregiver id as a string; null while unassigned. */
  primaryCaregiverId: string | null
  /** ISO datetime the primary caregiver was assigned; null while unassigned. */
  primaryCaregiverSince: string | null
  nextVisitAt: string | null
}

/**
 * GET /api/elders, mapped down to ElderRow. nextVisitAt is the elder's nextVisitDate as an ISO
 * "yyyy-MM-dd" string (derived server-side from the published plan's start date and its nodes'
 * weekly schedule) — null when there is no published plan or no scheduled visit. Format it for
 * display with lib/nextVisit.
 */
export async function fetchElders(): Promise<ElderRow[]> {
  const rows = await fetchElderList()
  return rows.map((r) => ({
    id: String(r.id),
    name: r.fullName,
    age: ageFromDateOfBirth(r.dateOfBirth),
    street: r.address ?? '',
    sector: r.sector ?? '',
    planStatus: r.planStatus,
    planVersion: r.planVersion,
    hasPublishedPlan: r.hasPublishedPlan,
    hasCareRequest: r.hasCareRequest,
    primaryCaregiver: r.primaryCaregiverName,
    primaryCaregiverId: r.primaryCaregiverId == null ? null : String(r.primaryCaregiverId),
    primaryCaregiverSince: r.primaryCaregiverAssignedAt,
    nextVisitAt: r.nextVisitDate,
  }))
}
