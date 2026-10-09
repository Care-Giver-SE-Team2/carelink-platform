import { useQuery } from '@tanstack/react-query'
import { fetchApprovedApplicants, fetchCaregiverApplications } from '../data/caregiverApplications'

/**
 * Pending caregiver applications, newest first. Shared by the Caregivers screen and its nav
 * count, so both always agree. Served by the mock store in data/caregiverApplications.ts.
 */
export function useCaregiverApplications() {
  return useQuery({
    queryKey: ['caregiverApplications', 'pending'],
    queryFn: fetchCaregiverApplications,
  })
}

/** How many caregiver applications wait for an answer — the Caregivers nav count. */
export function usePendingCaregiverApplicationCount() {
  return useQuery({
    queryKey: ['caregiverApplications', 'pending'],
    queryFn: fetchCaregiverApplications,
    select: (rows) => rows.length,
  })
}

/** Applicants approved in the mock store, until approving creates a real caregiver. */
export function useApprovedApplicants() {
  return useQuery({
    queryKey: ['caregiverApplications', 'approved'],
    queryFn: fetchApprovedApplicants,
  })
}
