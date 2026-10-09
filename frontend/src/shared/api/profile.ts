import { api } from './client'

/** Mirrors profile.domain.model.Elder — GET /api/elders/{id} returns it as-is (no DTO yet). */
export type ElderResponse = {
  id: number
  userId: number | null
  fullName: string
  gender: 'MALE' | 'FEMALE' | 'OTHER' | null
  dateOfBirth: string | null
  phone: string | null
  address: string | null
  postalCode: string | null
  sector: string | null
  preferredDialects: string | null
  livesAlone: boolean | null
  mobilityLevel: 'INDEPENDENT' | 'ASSISTIVE_CANE' | 'WHEELCHAIR_BEDBOUND' | null
  continuityPreference: 'PREFERRED' | 'REQUIRED' | 'NONE' | null
  medicalNotes: string | null
  createdAt: string
  updatedAt: string
}

export function fetchElder(id: string): Promise<ElderResponse> {
  return api<ElderResponse>(`/elders/${id}`)
}

/** One row of GET /api/elders/{id}/family — profile.application.ElderFamilyContact. */
export type ElderFamilyContact = {
  fullName: string
  relationship: 'SON' | 'DAUGHTER' | 'SPOUSE' | 'GUARDIAN' | 'OTHER'
  primaryContact: boolean
}

/** The elder's currently bound family, primary contact first; [] when none is bound. */
export function fetchElderFamily(id: string): Promise<ElderFamilyContact[]> {
  return api<ElderFamilyContact[]>(`/elders/${id}/family`)
}

/**
 * Row shape for GET /api/elders — profile.controller.dto.ElderListItemResponse. The three
 * primaryCaregiver fields are all null while the elder has no primary caregiver.
 */
export type ElderListItem = {
  id: number
  fullName: string
  dateOfBirth: string | null
  address: string | null
  sector: string | null
  planStatus: 'published' | 'draft' | 'stopped' | 'none'
  planVersion: number | null
  nextVisitDate: string | null
  primaryCaregiverId: number | null
  primaryCaregiverName: string | null
  primaryCaregiverAssignedAt: string | null
}

/**
 * Lists elders visible to the current user.
 * @param signal Cancels an outstanding request
 * @return Elder list using the shared profile response
 * @author Wang Zhili
 */
export function fetchElderList(signal?: AbortSignal): Promise<ElderListItem[]> {
  return api<ElderListItem[]>('/elders', { signal })
}

/** One row of GET /api/caregivers — profile.controller.dto.CaregiverOptionResponse. */
export type CaregiverOption = {
  id: number
  fullName: string
  sector: string | null
  /** Comma-separated languages and dialects, as stored ("Malay,English"). Optional so older fixtures still type-check. */
  dialects?: string | null
  status: 'ONBOARDING' | 'AVAILABLE' | 'BUSY' | 'INACTIVE'
  /** Server-side Caregiver.isAssignable(): false for onboarding or inactive caregivers. */
  assignable: boolean
}

/** Every caregiver, by name, for the manager's picker (manager only). */
export function fetchCaregivers(): Promise<CaregiverOption[]> {
  return api<CaregiverOption[]>('/caregivers')
}

/** PUT /api/elders/{elderId}/primary-caregiver response — profile.controller.dto.PrimaryCaregiverResponse. */
export type PrimaryCaregiverResponse = {
  caregiverId: number
  fullName: string
  assignedAt: string
}

/** Names the caregiver as the elder's primary caregiver, replacing any existing one. */
export function assignPrimaryCaregiver(elderId: string, caregiverId: number): Promise<PrimaryCaregiverResponse> {
  return api<PrimaryCaregiverResponse>(`/elders/${elderId}/primary-caregiver`, {
    method: 'PUT',
    body: JSON.stringify({ caregiverId }),
  })
}

/** Removes the elder's primary caregiver; succeeds even if none is assigned. */
export function removePrimaryCaregiver(elderId: string): Promise<void> {
  return api<void>(`/elders/${elderId}/primary-caregiver`, { method: 'DELETE' })
}

/**
 * Where a certificate stands in the manager's register — profile.domain.service
 * CredentialRegisterPolicy.State. REMINDED = published and inside the 30-day warning window.
 */
export type CertificationState =
  | 'SUBMITTED'
  | 'REJECTED'
  | 'REMINDED'
  | 'PUBLISHED'
  | 'EXPIRED'
  | 'REVOKED'

/** One row of GET /api/credentials — profile.application.CredentialRegisterRow (manager only). */
export type CredentialRegisterRow = {
  id: number
  caregiverId: number
  caregiverName: string
  credentialTypeId: number
  credentialTypeName: string
  /** Submitted as the renewal of `replacesId`, rather than as a first certificate of its type. */
  renewal: boolean
  state: CertificationState
  /** The date the row is racing — the replaced certificate's expiry for a pending renewal; null when nothing is due. */
  watchedExpiry: string | null
  daysUntilExpiry: number | null
  /** In the Expiring filter: due within the warning window, or already past it. */
  expiring: boolean
  certificateNo: string | null
  issuingBody: string | null
  validFrom: string | null
  /** This certificate's own expiry ("valid until"); null when it never expires. */
  expiryDate: string | null
  submittedAt: string | null
  /** The reason given for a rejection. */
  reviewNote: string | null
  reviewedAt: string | null
  replacesId: number | null
  replacesExpiryDate: string | null
}

/** The certification register: submitted rows first, then soonest to lapse (manager only). */
export function fetchCredentialRegister(signal?: AbortSignal): Promise<CredentialRegisterRow[]> {
  return api<CredentialRegisterRow[]>('/credentials', { signal })
}

/** Publishes a submitted certificate; a renewal supersedes the one it replaces. */
export function publishCredential(id: number): Promise<void> {
  return api<void>(`/credentials/${id}/publish`, { method: 'POST' })
}

/** Rejects a submitted certificate; the caregiver is told `reason`. */
export function rejectCredential(id: number, reason: string): Promise<void> {
  return api<void>(`/credentials/${id}/reject`, { method: 'POST', body: JSON.stringify({ reason: reason.trim() }) })
}

/** One screening check on a family application. `count` is the caregivers counted by `sector`
 *  (free now) and `dialect` (could take the elder); null for `contact` and when the sector is unknown.
 *  Whether the person is already on record is not a check: the server refuses that outright. */
export type IntakeCheck = {
  key: 'contact' | 'sector' | 'dialect'
  pass: boolean
  count: number | null
}

export type IntakeStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED'
export type IntakeMobilityLevel = 'INDEPENDENT' | 'ASSISTIVE_CANE' | 'WHEELCHAIR_BEDBOUND'

/** A pending family application as the manager reviews it (GET /api/intake-reviews): the family's
 *  submission, with the same field names the family app reads from /api/intake-applications, plus
 *  the applicant, the caregiver sector the postcode falls in (null when unknown) and the checks. */
export type IntakeReview = {
  id: number
  applicantFamilyMemberId: number
  applicant: { fullName: string; username: string | null; phone: string | null }
  targetElderName: string
  targetElderAge: number | null
  targetAddress: string
  postalCode: string
  mobilityLevel: IntakeMobilityLevel
  preferredDialects: string | null
  /** The form's checkbox codes (BATHING, VITALS) and the family's own words. */
  careNeeds: string[]
  medicalNotes: string | null
  status: IntakeStatus
  reviewRemarks: string | null
  /** ISO with offset. */
  createdAt: string
  reviewedAt: string | null
  elderId: number | null
  sector: string | null
  checks: IntakeCheck[]
}

/** `elderLogin` is set on approval only; its password is the one plain-text copy, shown once. */
export type IntakeDecision = {
  id: number
  status: IntakeStatus
  elderId: number | null
  elderLogin: { username: string; temporaryPassword: string } | null
}

/** Applications waiting for an answer, newest first. */
export function fetchIntakeReviews(signal?: AbortSignal): Promise<IntakeReview[]> {
  return api<IntakeReview[]>('/intake-reviews', { signal })
}

/** Approves: creates the elder record and the elder's login. `message` is shown to the family with the decision.
 *  Refused (409 ELDER_ALREADY_REGISTERED) if the elder has been put on record since the family applied. */
export function approveIntakeApplication(id: number, message: string | null): Promise<IntakeDecision> {
  return api<IntakeDecision>(`/intake-reviews/${id}/approve`, { method: 'POST', body: JSON.stringify({ message }) })
}

/** Declines: nothing is created; `message` (required) tells the family why. */
export function declineIntakeApplication(id: number, message: string): Promise<IntakeDecision> {
  return api<IntakeDecision>(`/intake-reviews/${id}/decline`, { method: 'POST', body: JSON.stringify({ message }) })
}

/** Body of POST /api/family-registrations — profile.controller.dto.FamilyRegistrationRequest. */
export type FamilyRegistrationRequest = {
  username: string
  password: string
  fullName: string
  phone: string
}

/** profile.controller.dto.FamilyRegistrationResponse. */
export type FamilyRegistrationResponse = {
  familyMemberId: number
  username: string
  fullName: string
}

/**
 * Family sign-up, made before there is a session: creates a FAMILY login and its family profile.
 * It does not sign in; the caller signs in with the same username and password afterwards.
 * 409 when the username is taken. The caller initialises the CSRF cookie first.
 */
export function registerFamily(request: FamilyRegistrationRequest): Promise<FamilyRegistrationResponse> {
  return api<FamilyRegistrationResponse>('/family-registrations', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
