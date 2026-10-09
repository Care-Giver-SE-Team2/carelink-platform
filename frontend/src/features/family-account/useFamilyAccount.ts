import { useQuery } from '@tanstack/react-query'
import { getCurrentUser } from '../auth/api'
import { ApiError } from '../../shared/api/client'
import { fetchElderList } from '../../shared/api/profile'

/** A lost session or access will not come back on retry; anything else gets two more tries. */
function retry(count: number, error: unknown) {
  return !(error instanceof ApiError && (error.status === 401 || error.status === 403)) && count < 2
}

/** The signed-in family member, from the server-side session (GET /api/auth/me). */
export function useFamilyMe() {
  return useQuery({
    queryKey: ['currentUser'],
    queryFn: ({ signal }) => getCurrentUser(signal),
    staleTime: 5 * 60 * 1000,
    retry,
  })
}

/** Elders this family member is actively linked to (GET /api/elders, scoped by the server). */
export function useFamilyElders() {
  return useQuery({
    queryKey: ['elders', 'family'],
    queryFn: ({ signal }) => fetchElderList(signal),
    retry,
  })
}
