import { api } from '../../shared/api/client'

export type CaregiverProfile = { id: number; userId: number; fullName: string; phone: string | null; sector: string | null; dialects: string | null; status: string }
export type VisitSummary = { id: number; elderId: number; elderName: string; serviceType: string | null; scheduledStart: string; scheduledEnd: string | null; status: string; version: number }
export type RenewalState = 'NONE' | 'PENDING_REVIEW' | 'REJECTED' | 'APPROVED_NOT_EFFECTIVE' | 'REVOKED' | 'CHECK_REQUIRED'
export type CredentialAlert = {
  id: number; name: string; certificateNo: string | null; expiryDate: string; status: string; warning: 'EXPIRED' | 'EXPIRING'
  daysUntilExpiry?: number; renewalState?: RenewalState; renewalValidFrom?: string | null
}
export type CredentialAlertContext = { asOfDate: string; warningDays: number; reviewRequired: boolean }
export type Schedule = {
  dateFrom: string; dateTo: string; timeZone: string; upcomingVisits: VisitSummary[]
  certificationAlerts: CredentialAlert[]
  credentialAlertContext?: CredentialAlertContext
}
export type WorkPack = {
  visit: VisitSummary
  elder: { elderId: number; preferredName: string; serviceAddress: string | null; postalSector: string | null; languageNeeds: string[]; accessNotes: string | null; emergencyNotes: string | null }
  carePlanId: number | null; carePlanVersion: number | null; serviceInstructions: string[]
  tasks: { id: number; name: string; status: string; outcome: string | null; caregiverNote: string | null; completedAt?: string | null }[]
  requiredEvidenceKinds: string[]
  healthObservation?: { healthFlag: import('../caregiver-execution/api').HealthFlag | null; healthNote: string | null }
  execution?: { allowedActions: string[]; blockedReason: string | null; serverNow: string; checkInOpensAt: string; checkInClosesAt: string; checkedInAt: string | null; checkedOutAt: string | null; lateArrival: boolean; locationSource: string | null }
}
export function getMyProfile(signal?: AbortSignal) { return api<CaregiverProfile>('/caregivers/me', { signal }) }
export function getMySchedule(query: string, signal?: AbortSignal) { return api<Schedule>('/caregivers/me/schedule' + (query ? '?' + query : ''), { signal }) }
export function getWorkPack(id: string, signal?: AbortSignal) { return api<WorkPack>('/visits/' + encodeURIComponent(id) + '/work-pack', { signal }) }
