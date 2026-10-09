import { useQuery } from '@tanstack/react-query'
import { fetchCarePlanVersions } from '../../../shared/api/careplan'

export function useCarePlanVersions(elderId: string | undefined) {
  return useQuery({
    queryKey: ['carePlan', 'versions', elderId],
    queryFn: () => fetchCarePlanVersions(elderId!),
    enabled: elderId !== undefined,
  })
}
