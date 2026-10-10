import { api } from '../../shared/api/client'
import type { ElderBasicDetails } from '../family-elders/api'

/** What the family is told: PLANNED once every activity is in a published care plan version (this outranks
 * a decline), DECLINED by the care team with a reason, SUBMITTED otherwise. Worked out by the server. */
export type ServiceApplicationOutcome = 'SUBMITTED' | 'PLANNED' | 'DECLINED'

/** One requested activity and the first care plan version that planned it, if any. */
export type ServiceApplicationNeed = { need: string; plannedVersion: number | null; plannedFrom: string | null }

export type ServiceApplication = {
  id: number
  elderId: number
  elderSnapshot: ElderBasicDetails
  careNeeds: string[]
  notes: string | null
  /** What was recorded; see outcome for what to show. */
  status: 'SUBMITTED' | 'DECLINED'
  createdAt: string
  outcome: ServiceApplicationOutcome
  needs: ServiceApplicationNeed[]
  declineReason: string | null
  declinedAt: string | null
}
export type ServiceApplicationPage = {
  items: ServiceApplication[]; page: number; size: number; totalElements: number
}
export type ServiceApplicationRequest = { elderId: number; careNeeds: string[]; notes: string | null }

export function listServiceApplications(page: number, signal?: AbortSignal) {
  return api<ServiceApplicationPage>(`/family/service-applications?page=${page}&size=20`, { signal })
}
export function getServiceApplication(id: string, signal?: AbortSignal) {
  return api<ServiceApplication>(`/family/service-applications/${encodeURIComponent(id)}`, { signal })
}
export async function submitServiceApplication(input: ServiceApplicationRequest, signal?: AbortSignal) {
  // Send only the request fields. The server supplies identity and the saved elder snapshot.
  const result = await api<ServiceApplication>('/family/service-applications', {
    method: 'POST', body: JSON.stringify(input), signal,
  })
  if (!result || !Number.isSafeInteger(result.id) || result.id <= 0 || result.status !== 'SUBMITTED') {
    throw new Error('The submission response could not be confirmed')
  }
  return result
}

export const careLabels: Record<string, string> = { BATHING: 'Bathing assistance', VITALS: 'Vital signs monitoring' }
