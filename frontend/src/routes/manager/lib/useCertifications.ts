import { useQuery } from '@tanstack/react-query'
import { fetchCredentialDocuments, fetchCredentialRegister } from '../../../shared/api/profile'
import { fetchVisitsAtRisk } from '../../../shared/api/rostering'
import { filterRows } from './certifications'

/**
 * The certification register (GET /api/credentials). Shared by the Certifications screen and
 * the nav's Certifications count, so both always show the same rows.
 */
export function useCredentialRegister() {
  return useQuery({
    queryKey: ['credentials'],
    queryFn: ({ signal }) => fetchCredentialRegister(signal),
  })
}

/** How many submitted certificates wait for the manager's review — the nav count and the review filter's count. */
export function useCertificationReviewCount() {
  return useQuery({
    queryKey: ['credentials'],
    queryFn: ({ signal }) => fetchCredentialRegister(signal),
    select: (rows) => filterRows(rows, 'review').length,
  })
}

/** The files uploaded with one certificate, for the review panel's scans. */
export function useCredentialDocuments(id: number) {
  return useQuery({
    queryKey: ['credentials', id, 'documents'],
    queryFn: ({ signal }) => fetchCredentialDocuments(id, signal),
  })
}

/** Visits at risk per credential id. Under the ['credentials'] key so a review refreshes it too. */
export function useVisitsAtRisk() {
  return useQuery({
    queryKey: ['credentials', 'visitsAtRisk'],
    queryFn: ({ signal }) => fetchVisitsAtRisk(signal),
    select: (risks) => new Map(risks.map((risk) => [risk.credentialId, risk.visitsAtRisk])),
  })
}
