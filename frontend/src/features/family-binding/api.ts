import { api } from '../../shared/api/client'

import type {
  CreateFamilyBindingRequest,
  FamilyBinding,
} from './types'

/**
 * Loads family bindings belonging to the currently authenticated elder.
 */
export function getFamilyBindings(): Promise<
  FamilyBinding[]
> {
  return api<FamilyBinding[]>(
    '/elders/me/family-bindings',
  )
}

/**
 * Creates a family binding request for the current elder.
 */
export function createFamilyBinding(
  request: CreateFamilyBindingRequest,
): Promise<FamilyBinding> {
  return api<FamilyBinding>(
    '/elders/me/family-bindings',
    {
      method: 'POST',
      body: JSON.stringify(request),
    },
  )
}

/**
 * Revokes one of the current elder's family bindings.
 */
export function revokeFamilyBinding(
  bindingId: number,
): Promise<FamilyBinding> {
  return api<FamilyBinding>(
    `/elders/me/family-bindings/${bindingId}`,
    {
      method: 'DELETE',
    },
  )
}
/** Family-facing incoming binding requests and decisions. */
export interface IncomingFamilyBinding {
  id: number
  elderId: number
  elderName: string
  relationship: string
  primaryContact: boolean
  accessScope: 'FULL' | 'READ_ONLY'
  status: 'PENDING_CONFIRMATION' | 'ACTIVE' | 'REJECTED' | 'REVOKED'
  confirmedAt: string | null
  createdAt: string | null
}

export function getIncomingFamilyBindings(): Promise<IncomingFamilyBinding[]> {
  return api<IncomingFamilyBinding[]>('/family/family-bindings')
}

export function decideIncomingFamilyBinding(bindingId: number, approve: boolean): Promise<IncomingFamilyBinding> {
  return api<IncomingFamilyBinding>(`/family/family-bindings/${bindingId}/decision`, {
    method: 'POST', body: JSON.stringify({ approve }),
  })
}
