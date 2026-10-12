import { useQuery } from '@tanstack/react-query'
import { fetchProviderApplications, fetchProviders } from '../data/providerApplications'

/**
 * Pending provider applications, newest first. Shared by the Providers screen and its nav count,
 * so both always agree. Served by the mock store in data/providerApplications.ts.
 */
export function useProviderApplications() {
  return useQuery({
    queryKey: ['providerApplications', 'pending'],
    queryFn: fetchProviderApplications,
  })
}

/** How many provider applications wait for an answer — the Providers nav count. */
export function usePendingProviderApplicationCount() {
  return useQuery({
    queryKey: ['providerApplications', 'pending'],
    queryFn: fetchProviderApplications,
    select: (rows) => rows.length,
  })
}

/** Approved providers, most recently approved first. */
export function useProviders() {
  return useQuery({
    queryKey: ['providers'],
    queryFn: fetchProviders,
  })
}
