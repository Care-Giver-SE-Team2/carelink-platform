import type { CaregiverOption, CredentialRegisterRow } from '../../../shared/api/profile'
import type { CertificationTagState, TagTone } from '../../../shared/components/ui'
import type { ApprovedApplicant } from '../data/caregiverApplications'

export const CAREGIVER_PAGE_SIZE = 10

export type CaregiverStatus = CaregiverOption['status']

export const STATUS_TAG: Record<CaregiverStatus, { label: string; tone: TagTone }> = {
  ONBOARDING: { label: 'ONBOARDING', tone: 'accent' },
  AVAILABLE: { label: 'AVAILABLE', tone: 'ink' },
  BUSY: { label: 'BUSY', tone: 'ink' },
  INACTIVE: { label: 'INACTIVE', tone: 'muted' },
}

/** One line of the status, for the caregiver panel's header. */
export const STATUS_MEANING: Record<CaregiverStatus, string> = {
  ONBOARDING: 'not rostered until a certificate is published',
  AVAILABLE: 'can be rostered',
  BUSY: 'fully booked today; can still be rostered',
  INACTIVE: 'has left; not rostered',
}

/** One of a caregiver's certificates, as the caregiver list and panel show it. */
export type CertificateLine = {
  key: string
  name: string
  state: CertificationTagState
  /** "Valid until"; null when it never expires. */
  expiryDate: string | null
  daysUntilExpiry: number | null
  /** The register row it opens in Certifications; null for one approved from a mock application. */
  registerId: number | null
}

export type DirectoryCaregiver = {
  id: number
  fullName: string
  sector: string | null
  dialects: string | null
  status: CaregiverStatus
  certificates: CertificateLine[]
  /** Set while the caregiver exists only in the mock application store. */
  approvedAt: string | null
}

const DAY_MS = 24 * 60 * 60 * 1000

/** Whole days from `today` to the "yyyy-MM-dd" date, by calendar date. */
function daysUntil(date: string, today: Date): number {
  const start = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate())
  const [year, month, day] = date.split('-').map(Number)
  return Math.round((Date.UTC(year, month - 1, day) - start) / DAY_MS)
}

/**
 * Every caregiver with their certificates, by name: the real caregivers with their register
 * rows (rejected and revoked ones left out), plus applicants approved in the mock store, whose
 * uploaded certificates were all published on approval.
 */
export function directoryRows(
  caregivers: CaregiverOption[],
  register: CredentialRegisterRow[],
  approved: ApprovedApplicant[],
  today: Date,
): DirectoryCaregiver[] {
  const byCaregiver = new Map<number, CertificateLine[]>()
  for (const row of register) {
    if (row.state === 'REJECTED' || row.state === 'REVOKED') continue
    const lines = byCaregiver.get(row.caregiverId) ?? []
    lines.push({
      key: `credential-${row.id}`,
      name: row.credentialTypeName,
      state: row.state,
      expiryDate: row.expiryDate,
      daysUntilExpiry: row.daysUntilExpiry,
      registerId: row.id,
    })
    byCaregiver.set(row.caregiverId, lines)
  }

  const real: DirectoryCaregiver[] = caregivers.map((c) => ({
    id: c.id,
    fullName: c.fullName,
    sector: c.sector,
    dialects: c.dialects ?? null,
    status: c.status,
    certificates: byCaregiver.get(c.id) ?? [],
    approvedAt: null,
  }))
  const fromApplications: DirectoryCaregiver[] = approved.map((a) => ({
    id: a.caregiverId,
    fullName: a.fullName,
    sector: a.sector,
    dialects: a.dialects,
    status: a.certificates.length ? 'AVAILABLE' : 'ONBOARDING',
    certificates: a.certificates.map((cert) => ({
      key: `application-${cert.id}`,
      name: cert.credentialTypeName,
      state: 'PUBLISHED',
      expiryDate: cert.expiryDate,
      daysUntilExpiry: cert.expiryDate ? daysUntil(cert.expiryDate, today) : null,
      registerId: null,
    })),
    approvedAt: a.approvedAt,
  }))

  return [...real, ...fromApplications].sort((a, b) => a.fullName.localeCompare(b.fullName))
}

/** The list's certificate column: "2 valid · 1 expiring · 1 to review", or "none". */
export function certificateSummary(lines: CertificateLine[]): string {
  const valid = lines.filter((l) => l.state === 'PUBLISHED' || l.state === 'REMINDED').length
  const expiring = lines.filter((l) => l.state === 'REMINDED').length
  const toReview = lines.filter((l) => l.state === 'SUBMITTED').length
  const expired = lines.filter((l) => l.state === 'EXPIRED').length
  const parts = [
    valid && `${valid} valid`,
    expiring && `${expiring} expiring`,
    toReview && `${toReview} to review`,
    expired && `${expired} expired`,
  ].filter(Boolean)
  return parts.length ? parts.join(' · ') : 'none'
}

/** "Hokkien,Mandarin" → "Hokkien, Mandarin". */
export function languagesLabel(dialects: string | null): string | null {
  const list = (dialects ?? '')
    .split(',')
    .map((d) => d.trim())
    .filter(Boolean)
  return list.length ? list.join(', ') : null
}

/** The application to open after one is answered: the next one down, else the one above, else none. */
export function nextApplicationId(rows: { id: number }[], answeredId: number): number | null {
  const index = rows.findIndex((row) => row.id === answeredId)
  if (index === -1) return null
  return rows[index + 1]?.id ?? rows[index - 1]?.id ?? null
}
