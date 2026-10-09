import { useQuery } from '@tanstack/react-query'
import { fetchElder } from '../../../shared/api/profile'
import type { ElderResponse } from '../../../shared/api/profile'
import type { ElderRow } from '../data/elders'
import { ageFromDateOfBirth } from './age'

/** GET /api/elders/{id} as the care plan page uses it: ElderRow's identity fields plus the profile. */
export type ElderSummary = Pick<ElderRow, 'id' | 'name' | 'age' | 'sector'> & {
  address: string | null
  livesAlone: boolean | null
  preferredDialects: string | null
  mobilityLevel: ElderResponse['mobilityLevel']
  continuityPreference: ElderResponse['continuityPreference']
}

function toElderSummary(elder: ElderResponse): ElderSummary {
  return {
    id: String(elder.id),
    name: elder.fullName,
    age: ageFromDateOfBirth(elder.dateOfBirth),
    sector: elder.sector ?? '',
    address: elder.address,
    livesAlone: elder.livesAlone,
    preferredDialects: elder.preferredDialects,
    mobilityLevel: elder.mobilityLevel,
    continuityPreference: elder.continuityPreference,
  }
}

export function useElder(id: string | undefined) {
  return useQuery({
    queryKey: ['elder', id],
    queryFn: async () => toElderSummary(await fetchElder(id!)),
    enabled: id !== undefined,
  })
}
