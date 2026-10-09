import { useQuery } from '@tanstack/react-query'
import { getFamilyVisitTasks } from './api'

/**
 * Task records for the visit on the family home's live card, re-read every 15 seconds while it is shown.
 * @param visitId Visit to read, or null when no visit is under way
 */
export function useFamilyVisitTasks(visitId: number | null) {
  return useQuery({
    queryKey: ['family-visit-tasks', visitId],
    queryFn: ({ signal }) => getFamilyVisitTasks(String(visitId), signal),
    enabled: visitId !== null,
    refetchInterval: 15_000,
  })
}
