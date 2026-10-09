import { useQuery } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { ApiError } from '../../shared/api/client'
import { getFamilyElderProfile, getFamilyElderProfiles } from './api'

const profileKey = ['family-elder-profiles'] as const
export const elderProfileKey = (id: number) => [...profileKey, id] as const

function retry(count: number, error: unknown) {
  return !(error instanceof ApiError && [401, 403, 404].includes(error.status)) && count < 2
}
export function useFamilyElderProfiles() {
  return useQuery({ queryKey: profileKey, queryFn: ({ signal }) => getFamilyElderProfiles(signal), retry })
}
export function useFamilyElderProfile(id: number) {
  return useQuery({
    queryKey: elderProfileKey(id), queryFn: ({ signal }) => getFamilyElderProfile(id, signal),
    enabled: Number.isSafeInteger(id) && id > 0, retry,
  })
}

/** Keep My elders and the shared Following selector current after a binding decision or profile save. */
export async function refreshFamilyElders(client: QueryClient) {
  await Promise.all([
    client.invalidateQueries({ queryKey: profileKey }),
    client.invalidateQueries({ queryKey: ['elders', 'family'] }),
  ])
}
