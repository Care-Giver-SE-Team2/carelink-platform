import { api } from '../../shared/api/client'
import type { ElderBasicDetails } from '../family-elders/api'

export type ServiceApplication = {
  id: number
  elderId: number
  elderSnapshot: ElderBasicDetails
  careNeeds: string[]
  notes: string | null
  status: 'SUBMITTED'
  createdAt: string
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
