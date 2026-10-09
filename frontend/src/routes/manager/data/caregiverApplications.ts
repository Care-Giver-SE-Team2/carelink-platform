/**
 * Caregiver applications: an in-memory store standing in for the caregiver sign-up endpoints,
 * which don't exist yet. The caregiver fills in a form on their side and uploads their
 * certificates; the manager approves or declines here. The real endpoints this mimics:
 *
 *   GET  /api/caregiver-applications              pending applications, newest first
 *   POST /api/caregiver-applications/{id}/approve { message }  → { caregiverId }
 *        enables the login the applicant chose, creates the caregiver row and publishes every
 *        certificate they uploaded (so the caregiver is rosterable straight away)
 *   POST /api/caregiver-applications/{id}/decline { message }  (message required)
 *   GET  /api/caregiver-applications/{id}/certificates/{certificateId}/scan
 *        the uploaded file, served with its own content type (`scanUrl` points here)
 *
 * The fields follow the `caregiver` and `credential` tables, so the real response can keep
 * these names. The applicant picks their own username and password, so approval issues
 * nothing for the manager to pass on.
 *
 * The mock holds no files, so every `scanUrl` is null and each certificate shows the scan
 * placeholder; once the endpoint serves real scans, the thumbnail opens them full size.
 *
 * Because the mock can't create a real caregiver, approved applicants are kept here and merged
 * into the caregiver list (see lib/caregivers.ts `directoryRows`). Drop that merge, and
 * `fetchApprovedApplicants`, once approve creates the caregiver server-side.
 */

export type ApplicationCertificate = {
  id: number
  credentialTypeName: string
  certificateNo: string | null
  issuingBody: string | null
  validFrom: string | null
  /** "Valid until"; null when it never expires. */
  expiryDate: string | null
  /** The uploaded scan's file name, as the applicant's device named it. */
  fileName: string
  /** Where the scan can be fetched from; null if the upload never arrived. */
  scanUrl: string | null
  /** The scan's media type: an image (image/jpeg, image/png) or application/pdf. */
  scanContentType: string | null
}

export type CaregiverApplication = {
  id: number
  fullName: string
  /** The login the applicant chose; enabled on approval. */
  username: string
  phone: string | null
  sector: string | null
  /** Comma-separated, as the caregiver table stores them ("Malay,English"). */
  dialects: string | null
  submittedAt: string
  certificates: ApplicationCertificate[]
}

export type ApprovedApplicant = CaregiverApplication & { caregiverId: number; approvedAt: string }

/** Mock caregiver ids start here so they never collide with real ones. */
const MOCK_CAREGIVER_ID_BASE = 9000

const HOUR_MS = 60 * 60 * 1000

function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * HOUR_MS).toISOString()
}

/** Today plus `days`, as "yyyy-MM-dd". */
function inDays(days: number): string {
  return new Date(Date.now() + days * 24 * HOUR_MS).toISOString().slice(0, 10)
}

let pending: CaregiverApplication[] = [
  {
    id: 7,
    fullName: 'Lim Hui Min',
    username: 'lim.huimin',
    phone: '+65 9234 1180',
    sector: 'AMK',
    dialects: 'Hokkien,Mandarin,English',
    submittedAt: hoursAgo(3),
    certificates: [
      {
        id: 71,
        credentialTypeName: 'First aid',
        certificateNo: 'SRC-FA-73310',
        issuingBody: 'Singapore Red Cross',
        validFrom: inDays(-120),
        expiryDate: inDays(610),
        fileName: 'first-aid-certificate.pdf',
        scanUrl: null,
        scanContentType: null,
      },
      {
        id: 72,
        credentialTypeName: 'Dementia care',
        certificateNo: 'DSG-24418',
        issuingBody: 'Dementia Singapore',
        validFrom: inDays(-40),
        expiryDate: inDays(1055),
        fileName: 'dementia-care.jpg',
        scanUrl: null,
        scanContentType: null,
      },
    ],
  },
  {
    id: 6,
    fullName: 'Mohamed Faizal bin Rahman',
    username: 'faizal.rahman',
    phone: '+65 8812 4407',
    sector: 'TPY',
    dialects: 'Malay,English',
    submittedAt: hoursAgo(26),
    certificates: [
      {
        id: 61,
        credentialTypeName: 'Manual handling',
        certificateNo: 'MH-31877',
        issuingBody: 'Agency for Integrated Care',
        validFrom: null,
        expiryDate: null,
        fileName: 'manual-handling.pdf',
        scanUrl: null,
        scanContentType: null,
      },
    ],
  },
  {
    id: 5,
    fullName: 'Priya Nair',
    username: 'priya.nair',
    phone: null,
    sector: 'AMK',
    dialects: 'Tamil,English',
    submittedAt: hoursAgo(50),
    certificates: [
      {
        id: 51,
        credentialTypeName: 'First aid',
        certificateNo: 'SRC-FA-60952',
        issuingBody: 'Singapore Red Cross',
        validFrom: inDays(-700),
        expiryDate: inDays(20),
        fileName: 'IMG_2231.jpg',
        scanUrl: null,
        scanContentType: null,
      },
      {
        id: 52,
        credentialTypeName: 'Medication prompt',
        certificateNo: 'MP-12088',
        issuingBody: 'Agency for Integrated Care',
        validFrom: inDays(-15),
        expiryDate: inDays(715),
        fileName: 'medication-prompt.pdf',
        scanUrl: null,
        scanContentType: null,
      },
    ],
  },
]

let approved: ApprovedApplicant[] = []

function take(id: number): CaregiverApplication {
  const application = pending.find((a) => a.id === id)
  if (!application) throw new Error('This application has already been answered.')
  pending = pending.filter((a) => a.id !== id)
  return application
}

/** Applications waiting for an answer, newest first. */
export async function fetchCaregiverApplications(): Promise<CaregiverApplication[]> {
  return [...pending].sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))
}

/** Applicants approved in this session. The mock's stand-in for the caregivers they became. */
export async function fetchApprovedApplicants(): Promise<ApprovedApplicant[]> {
  return [...approved]
}

/** Approves the application: the login is enabled and every uploaded certificate is published. */
export async function approveCaregiverApplication(id: number, _message: string | null): Promise<{ caregiverId: number }> {
  const application = take(id)
  const caregiverId = MOCK_CAREGIVER_ID_BASE + application.id
  approved = [...approved, { ...application, caregiverId, approvedAt: new Date().toISOString() }]
  return { caregiverId }
}

/** Declines the application; `message` tells the applicant why. */
export async function declineCaregiverApplication(id: number, _message: string): Promise<void> {
  take(id)
}
