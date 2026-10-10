import { useQuery } from '@tanstack/react-query'

import { fetchFamilyValueAddedServiceRequests } from './api'

/** Key of one elder's requests as a family member sees them; writes patch it with `setQueryData`. */
export function familyRequestsKey(elderId: number | null) {
  return ['value-added-service-requests', 'family', elderId] as const
}

/** The followed elder's extra-service requests; nothing is asked until an elder is known. */
export function useFamilyValueAddedRequests(elderId: number | null) {
  return useQuery({
    queryKey: familyRequestsKey(elderId),
    queryFn: () => fetchFamilyValueAddedServiceRequests(elderId as number),
    enabled: elderId !== null,
  })
}
