import { useQuery } from '@tanstack/react-query'
import { fetchCaregiverApplications } from '../../../shared/api/profile'

/**
 * Pending caregiver applications, newest first. Shares one cached query with the nav count, so
 * both always agree.
 */
export function useCaregiverApplications() {
  return useQuery({
    queryKey: ['caregiverApplications', 'pending'],
    queryFn: ({ signal }) => fetchCaregiverApplications(signal),
    select: (page) => page.items,
  })
}

/** How many caregiver applications wait for an answer — the Caregivers nav count. */
export function usePendingCaregiverApplicationCount() {
  return useQuery({
    queryKey: ['caregiverApplications', 'pending'],
    queryFn: ({ signal }) => fetchCaregiverApplications(signal),
    select: (page) => page.total,
  })
}
