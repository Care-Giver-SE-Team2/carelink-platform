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

/** Care the family applied for on the elder's behalf: the intake that created the elder, or a later service
 * application. careNeeds are care activity codes, or free text on applications that predate the catalog.
 * outcome is worked out from the published plan versions: PLANNED once every activity is in one (this outranks
 * a decline), DECLINED with the reason the family was given, SUBMITTED otherwise. */
export type ElderCareRequest = {
  source: 'INTAKE' | 'SERVICE_APPLICATION'
  applicationId: number
  careNeeds: string[]
  notes: string | null
  submittedAt: string
  outcome: 'SUBMITTED' | 'PLANNED' | 'DECLINED'
  needs: { need: string; plannedVersion: number | null; plannedFrom: string | null }[]
  declineReason: string | null
}

/** Newest first; [] when the family hasn't applied for anything (e.g. the elder registered themselves). */
export function fetchElderCareRequests(id: string): Promise<ElderCareRequest[]> {
  return api<ElderCareRequest[]>(`/elders/${id}/care-requests`)
}

/** The care team won't plan a family's service application; the reason is shown to the family. */
export function declineServiceApplication(applicationId: number, reason: string): Promise<void> {
  return api<void>(`/service-applications/${applicationId}/decline`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
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
  /**
   * Server-side Caregiver.isAssignable(): false for onboarding or inactive caregivers. Asked for one
   * elder (fetchCaregiversForElder), also false when that elder's certificates or timetable rule them out.
   */
  assignable: boolean
  /** What rules them out, when they are: CAREGIVER_NOT_ASSIGNABLE, CAREGIVER_MISSING_CREDENTIAL or CAREGIVER_TIME_CLASH. */
  refusalCode?: string | null
  /** Why, ready to show; only when asked for one elder. */
  refusal?: string | null
}

/** Every caregiver, by name, for the manager's picker (manager only). */
export function fetchCaregivers(): Promise<CaregiverOption[]> {
  return api<CaregiverOption[]>('/caregivers')
}

/** Every caregiver, each saying whether they may be named this elder's primary caregiver and why not. */
export function fetchCaregiversForElder(elderId: string): Promise<CaregiverOption[]> {
  return api<CaregiverOption[]>(`/caregivers?elderId=${encodeURIComponent(elderId)}`)
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

export type CredentialDocument = {
  id: number
  credentialId: number
  fileName: string
  contentType: string
  byteSize: number
  uploadedAt: string
  /** False when the stored file can't be found; `contentUrl` then won't load. */
  contentAvailable: boolean
  /** The file itself, served by the backend after its access check (e.g. /api/credentials/7/documents/3/content). */
  contentUrl: string
}

/** The files uploaded with a certificate, in upload order (GET /api/credentials/{id}/documents). */
export function fetchCredentialDocuments(id: number, signal?: AbortSignal): Promise<CredentialDocument[]> {
  return api<CredentialDocument[]>(`/credentials/${id}/documents`, { signal })
}

/** One certificate an applicant uploaded — CaregiverApplicationReviewService.Certificate. */
export type CaregiverApplicationCertificate = {
  /** Its row in the certification register, where it is published or rejected. */
  id: number
  credentialTypeName: string
  state: CertificationState
  certificateNo: string | null
  issuingBody: string | null
  validFrom: string | null
  /** "Valid until"; null when it never expires. */
  expiryDate: string | null
  /** The first uploaded scan's file name; null when no file was uploaded. */
  fileName: string | null
  /** Where the scan can be fetched from; null when there is no readable file. */
  scanUrl: string | null
  /** An image type or application/pdf. */
  scanContentType: string | null
}

/** A pending caregiver application — CaregiverApplicationReviewService.Item (manager only). */
export type CaregiverApplication = {
  id: number
  caregiverId: number
  /** The login the applicant chose at sign-up. */
  username: string
  fullName: string
  phone: string | null
  /** Comma-separated ("Malay,English"). */
  dialects: string | null
  /** The sector they asked for; not an assignment. */
  requestedSector: string | null
  submittedAt: string
  /** 1 for a first application, more after each resubmission. */
  roundNumber: number
  /** Quoted back with a decision, so one made on a stale screen is refused. */
  version: number
  certificates: CaregiverApplicationCertificate[]
}

export type CaregiverApplicationPage = { items: CaregiverApplication[]; page: number; size: number; total: number }

export type CaregiverApplicationDecision = { applicationId: number; caregiverId: number; status: 'APPROVED' | 'REJECTED' }

/** Pending caregiver applications, newest first. 100 is the most the endpoint returns in one page. */
export function fetchCaregiverApplications(signal?: AbortSignal): Promise<CaregiverApplicationPage> {
  return api<CaregiverApplicationPage>('/caregiver-applications?page=0&size=100', { signal })
}

/** Admits the applicant; their certificates stay as they are, for review in Certifications. */
export function approveCaregiverApplication(
  id: number,
  expectedVersion: number,
  message: string | null,
): Promise<CaregiverApplicationDecision> {
  return api<CaregiverApplicationDecision>(`/caregiver-applications/${id}/approve`, {
    method: 'POST',
    body: JSON.stringify({ message, expectedVersion, clientRequestId: crypto.randomUUID() }),
  })
}

/** Turns the application down; the applicant sees `reason` and may edit and resubmit. */
export function rejectCaregiverApplication(
  id: number,
  expectedVersion: number,
  reason: string,
): Promise<CaregiverApplicationDecision> {
  return api<CaregiverApplicationDecision>(`/caregiver-applications/${id}/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason, expectedVersion, clientRequestId: crypto.randomUUID() }),
  })
}
