import { api } from '../../shared/api/client'
import type {
  CaregiverCoverOption,
  FamilyValueAddedServiceRequestCreate,
  ManagedValueAddedServiceRequest,
  ValueAddedService,
  ValueAddedServiceRequest,
  ValueAddedServiceRequestCreate,
} from './types'

export function fetchValueAddedServices(): Promise<ValueAddedService[]> {
  return api<ValueAddedService[]>('/elders/me/value-added-services')
}

export function fetchElderValueAddedServiceRequests(): Promise<ValueAddedServiceRequest[]> {
  return api<ValueAddedServiceRequest[]>('/elders/me/value-added-service-requests')
}

export function createElderValueAddedServiceRequest(
  request: ValueAddedServiceRequestCreate,
): Promise<ValueAddedServiceRequest> {
  return api<ValueAddedServiceRequest>('/elders/me/value-added-service-requests', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

/** The elder withdraws their own request; a booked visit that has not started is called off. */
export function withdrawElderValueAddedServiceRequest(id: number): Promise<ValueAddedServiceRequest> {
  return api<ValueAddedServiceRequest>(`/elders/me/value-added-service-requests/${id}/cancellation`, { method: 'POST' })
}

/** The catalogue as a family member sees it, to ask on the elder's behalf. */
export function fetchFamilyValueAddedServices(): Promise<ValueAddedService[]> {
  return api<ValueAddedService[]>('/family/value-added-services')
}

/** A family member asks on the elder's behalf; their asking is their approval. */
export function createFamilyValueAddedServiceRequest(
  request: FamilyValueAddedServiceRequestCreate,
): Promise<ValueAddedServiceRequest> {
  return api<ValueAddedServiceRequest>('/family/value-added-service-requests', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function fetchFamilyValueAddedServiceRequests(elderId: number): Promise<ValueAddedServiceRequest[]> {
  return api<ValueAddedServiceRequest[]>(`/family/value-added-service-requests?elderId=${elderId}`)
}

export function decideValueAddedServiceRequest(
  id: number,
  decision: 'APPROVED' | 'REJECTED',
): Promise<ValueAddedServiceRequest> {
  return api<ValueAddedServiceRequest>(`/family/value-added-service-requests/${id}/decision`, {
    method: 'POST',
    body: JSON.stringify({ decision }),
  })
}

/** Every request, newest first, with its visit (manager only). */
export function fetchManagedValueAddedServiceRequests(signal?: AbortSignal): Promise<ManagedValueAddedServiceRequest[]> {
  return api<ManagedValueAddedServiceRequest[]>('/value-added-service-requests', { signal })
}

/** Who can take a dispatched request's visit, best first, then who cannot and why (manager only). */
export function fetchCaregiverCoverOptions(id: number, signal?: AbortSignal): Promise<CaregiverCoverOption[]> {
  return api<CaregiverCoverOption[]>(`/value-added-service-requests/${id}/caregiver-options`, { signal })
}

export function assignValueAddedServiceCaregiver(
  id: number,
  caregiverId: number,
): Promise<ManagedValueAddedServiceRequest> {
  return api<ManagedValueAddedServiceRequest>(`/value-added-service-requests/${id}/caregiver`, {
    method: 'POST',
    body: JSON.stringify({ caregiverId }),
  })
}

/** Calls the request off, and its visit with it if it has one (manager only). */
export function cancelValueAddedServiceRequest(id: number): Promise<ManagedValueAddedServiceRequest> {
  return api<ManagedValueAddedServiceRequest>(`/value-added-service-requests/${id}/cancellation`, { method: 'POST' })
}
