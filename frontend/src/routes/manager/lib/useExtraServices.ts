import { useQuery } from '@tanstack/react-query'
import {
  fetchCaregiverCoverOptions,
  fetchManagedValueAddedServiceRequests,
  fetchProviderOptions,
  fetchServiceCatalogue,
} from '../../../features/value-added-services/api'

/**
 * Every extra-service request with its visit (GET /api/value-added-service-requests). Shared by
 * the Extra services screen and its nav count, so both always agree.
 */
export function useExtraServices() {
  return useQuery({
    queryKey: ['extra-services'],
    queryFn: ({ signal }) => fetchManagedValueAddedServiceRequests(signal),
  })
}

/** How many dispatched visits have nobody on them — the Extra services nav count. */
export function useExtraServicesNeedingCaregiverCount() {
  return useQuery({
    queryKey: ['extra-services'],
    queryFn: ({ signal }) => fetchManagedValueAddedServiceRequests(signal),
    select: (rows) => rows.filter((row) => row.needsCaregiver).length,
  })
}

/** Who can take one request's visit, best first, then who cannot and why. */
export function useCaregiverCoverOptions(requestId: number) {
  return useQuery({
    queryKey: ['extra-services', requestId, 'caregiver-options'],
    queryFn: ({ signal }) => fetchCaregiverCoverOptions(requestId, signal),
  })
}

/** Which providers a request can be offered to, by name. */
export function useProviderOptions(requestId: number) {
  return useQuery({
    queryKey: ['extra-services', requestId, 'provider-options'],
    queryFn: ({ signal }) => fetchProviderOptions(requestId, signal),
  })
}

/** The catalogue of extra services, withdrawn ones too (GET /api/value-added-services). */
export function useServiceCatalogue() {
  return useQuery({
    queryKey: ['value-added-catalogue'],
    queryFn: ({ signal }) => fetchServiceCatalogue(signal),
  })
}
