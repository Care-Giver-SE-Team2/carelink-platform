import { api } from '../../shared/api/client'
import type {
  CaregiverCoverOption,
  CatalogueService,
  FamilyValueAddedServiceRequestCreate,
  ManagedValueAddedServiceRequest,
  ProviderOption,
  ValueAddedService,
  ValueAddedServiceCreate,
  ValueAddedServiceRequest,
  ValueAddedServiceRequestCreate,
  ValueAddedServiceUpdate,
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

/** The providers a manager may offer the request to, by name (manager only). */
export function fetchProviderOptions(id: number, signal?: AbortSignal): Promise<ProviderOption[]> {
  return api<ProviderOption[]>(`/value-added-service-requests/${id}/provider-options`, { signal })
}

/** Offers a dispatched visit nobody holds to the provider the manager picked (manager only). */
export function offerValueAddedServiceToProvider(id: number, providerId: number): Promise<ManagedValueAddedServiceRequest> {
  return api<ManagedValueAddedServiceRequest>(`/value-added-service-requests/${id}/provider`, {
    method: 'POST',
    body: JSON.stringify({ providerId }),
  })
}

/** Calls the request off, and its visit with it if it has one (manager only). */
export function cancelValueAddedServiceRequest(id: number): Promise<ManagedValueAddedServiceRequest> {
  return api<ManagedValueAddedServiceRequest>(`/value-added-service-requests/${id}/cancellation`, { method: 'POST' })
}

/** The whole catalogue, withdrawn services too, by name, with how often each was asked for (manager only). */
export function fetchServiceCatalogue(signal?: AbortSignal): Promise<CatalogueService[]> {
  return api<CatalogueService[]>('/value-added-services', { signal })
}

/** Removes a service nobody ever asked for; 409 when one was (manager only). */
export function removeValueAddedService(id: number): Promise<void> {
  return api<void>(`/value-added-services/${id}`, { method: 'DELETE' })
}

/** Adds a service, available at once; 409 when the name is taken (manager only). */
export function addValueAddedService(service: ValueAddedServiceCreate): Promise<ValueAddedService> {
  return api<ValueAddedService>('/value-added-services', { method: 'POST', body: JSON.stringify(service) })
}

/** Changes a service's description, length or status; its name stays (manager only). */
export function updateValueAddedService(id: number, update: ValueAddedServiceUpdate): Promise<ValueAddedService> {
  return api<ValueAddedService>(`/value-added-services/${id}`, { method: 'PUT', body: JSON.stringify(update) })
}
