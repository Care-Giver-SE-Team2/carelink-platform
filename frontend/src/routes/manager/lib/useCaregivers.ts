import { useQuery } from '@tanstack/react-query'
import { fetchCaregivers, fetchCaregiversForElder } from '../../../shared/api/profile'

export function useCaregivers(enabled = true) {
  return useQuery({
    queryKey: ['caregivers'],
    queryFn: fetchCaregivers,
    enabled,
  })
}

/**
 * Every caregiver as candidates to be this elder's primary caregiver, each with what rules them
 * out. Read afresh each time the picker opens, since another elder's change can rule one out.
 */
export function useCaregiversForElder(elderId: string | null) {
  return useQuery({
    queryKey: ['caregivers', 'forElder', elderId],
    queryFn: () => fetchCaregiversForElder(elderId!),
    enabled: elderId !== null,
  })
}
