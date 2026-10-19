import { api } from '../../shared/api/client'

export type ApplicationDetails = { fullName: string; phone: string; declaredDialects: string | null; requestedSector: string | null }
export type Application = {
  id: number; caregiverId: number; status: 'PENDING' | 'APPROVED' | 'REJECTED'; roundNumber: number
  details: ApplicationDetails; submittedAt: string; reviewedByUserId: number | null; reviewedAt: string | null; reviewReason: string | null
}
export type Admission = {
  caregiverId: number; userId: number; username: string; workStatus: string; phone: string | null; version: number
  application: Application | null
  history: { actorUserId: number; action: string; version: number; snapshot: Application; occurredAt: string }[]
  canWork: boolean; canEdit: boolean; canResubmit: boolean; compatibility: string | null
}
export function registerCaregiver(request: ApplicationDetails & { username: string; password: string }, signal?: AbortSignal) {
  return api<Admission>('/caregiver-registrations', { method: 'POST', body: JSON.stringify(request), signal })
}
export const getAdmission = (signal?: AbortSignal) => api<Admission>('/caregivers/me/application', { signal })
export function editApplication(details: ApplicationDetails, expectedVersion: number, clientRequestId: string, signal?: AbortSignal) {
  return api<Admission>('/caregivers/me/application', { method: 'PUT', body: JSON.stringify({ ...details, expectedVersion, clientRequestId }), signal })
}
export function resubmitApplication(expectedVersion: number, clientRequestId: string, signal?: AbortSignal) {
  return api<Admission>('/caregivers/me/application/resubmissions', { method: 'POST', body: JSON.stringify({ expectedVersion, clientRequestId }), signal })
}
export function updateContact(phone: string, expectedVersion: number, clientRequestId: string, signal?: AbortSignal) {
  return api<Admission>('/caregivers/me/contact', { method: 'PUT', body: JSON.stringify({ phone, expectedVersion, clientRequestId }), signal })
}
