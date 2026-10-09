import { api } from '../../shared/api/client'

/** Only family-maintained details; account, clinical and manager fields are excluded. */
export type ElderBasicDetails = {
  fullName: string
  gender: 'MALE' | 'FEMALE' | 'OTHER' | null
  dateOfBirth: string | null
  phone: string | null
  address: string | null
  postalCode: string | null
  preferredDialects: string | null
  livesAlone: boolean | null
  mobilityLevel: 'INDEPENDENT' | 'ASSISTIVE_CANE' | 'WHEELCHAIR_BEDBOUND' | null
}
export type FamilyElderProfile = ElderBasicDetails & {
  id: number
  accessScope: 'FULL' | 'READ_ONLY'
}

export function getFamilyElderProfiles(signal?: AbortSignal) {
  return api<FamilyElderProfile[]>('/family/elders', { signal })
}
export function getFamilyElderProfile(id: number, signal?: AbortSignal) {
  return api<FamilyElderProfile>(`/family/elders/${id}`, { signal })
}
export function updateFamilyElderProfile(id: number, details: ElderBasicDetails) {
  return api<FamilyElderProfile>(`/family/elders/${id}`, {
    method: 'PUT', body: JSON.stringify(details),
  })
}
