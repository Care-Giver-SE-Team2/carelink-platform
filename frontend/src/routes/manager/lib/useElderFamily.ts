import { useQuery } from '@tanstack/react-query'
import { fetchElderFamily } from '../../../shared/api/profile'

export function useElderFamily(id: string | undefined) {
  return useQuery({
    queryKey: ['elderFamily', id],
    queryFn: () => fetchElderFamily(id!),
    enabled: id !== undefined,
  })
}
