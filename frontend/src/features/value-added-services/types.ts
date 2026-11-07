export type ValueAddedService = {
  id: number
  name: string
  description: string | null
  /** How long the visit lasts once approved. */
  durationMinutes: number
  status: 'AVAILABLE' | 'UNAVAILABLE'
}

/** A service in the manager's catalogue: how often it was asked for, and so whether it may be removed. */
export type CatalogueService = ValueAddedService & {
  requestCount: number
  /** Nobody ever asked for it. One that was asked for stays, for its requests; it can be made unavailable. */
  removable: boolean
}

/** A manager adds a service to the catalogue (POST /api/value-added-services). */
export type ValueAddedServiceCreate = {
  name: string
  description: string | null
  durationMinutes: number
}

/** What a manager may change about a service: everything but its name (PUT /api/value-added-services/{id}). */
export type ValueAddedServiceUpdate = {
  description: string | null
  durationMinutes: number
  status: ValueAddedService['status']
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
  /** The Open Platform provider a manager last offered it to; null when none was. */
  providerName?: string | null
  /** That provider's job: NEW, ACCEPTED, DECLINED, COMPLETED or CANCELLED. */
  providerJobStatus?: string | null
}

/** One caregiver in the picker for a dispatched visit, from the same search absences use. */
/** An Open Platform provider a manager may offer a request to: active, offering the service, not tried yet. */
export type ProviderOption = {
  providerId: number
  organisationName: string
}

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
