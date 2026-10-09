export type ValueAddedService = {
  id: number
  name: string
  description: string | null
  /** How long the visit lasts once approved. */
  durationMinutes: number
  status: 'AVAILABLE' | 'UNAVAILABLE'
}

export type ValueAddedServiceRequestStatus =
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'DISPATCHED'
  | 'COMPLETED'
  | 'CANCELLED'

export type ValueAddedServiceRequest = {
  id: number
  elderId: number
  valueAddedServiceId: number
  serviceName: string
  requestedByFamilyMemberId: number | null
  approvingFamilyMemberId: number | null
  visitId: number | null
  requestedSchedule: string | null
  specialInstructions: string | null
  status: ValueAddedServiceRequestStatus
  decidedAt: string | null
  createdAt: string
}

export type ValueAddedServiceRequestCreate = {
  valueAddedServiceId: number
  requestedSchedule: string
  specialInstructions: string | null
}

/** A request as the manager's Extra services screen sees it, with its work order's visit. */
export type ManagedValueAddedServiceRequest = {
  id: number
  elderId: number
  valueAddedServiceId: number
  serviceName: string | null
  requestedSchedule: string | null
  specialInstructions: string | null
  status: ValueAddedServiceRequestStatus
  decidedAt: string | null
  createdAt: string
  visitId: number | null
  /** The visit's state, e.g. SCHEDULED or EXCEPTION; null before dispatch. */
  visitStatus: string | null
  /** Who is on the visit; null while nobody is. */
  caregiverId: number | null
  /** Dispatched, with nobody on a visit that has not started yet. */
  needsCaregiver: boolean
}

/** One caregiver in the picker for a dispatched visit, from the same search absences use. */
export type CaregiverCoverOption = {
  caregiverId: number
  name: string
  /** 1 for the best suggestion; null when a hard rule excludes them. */
  rank: number | null
  /** Why they are suggested, or what excludes them. */
  reason: string | null
  eligible: boolean
}

/** A family member's request on the elder's behalf; it is dispatched at once. */
export type FamilyValueAddedServiceRequestCreate = ValueAddedServiceRequestCreate & { elderId: number }
