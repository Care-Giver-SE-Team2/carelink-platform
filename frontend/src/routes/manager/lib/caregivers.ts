import type { CaregiverOption, CredentialRegisterRow } from '../../../shared/api/profile'
import type { CertificationTagState, TagTone } from '../../../shared/components/ui'
import { caregiverRef } from './certifications'

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
  ONBOARDING: 'application not approved yet; not rostered',
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
  /** The register row it opens in Certifications. */
  registerId: number
}

export type DirectoryCaregiver = {
  id: number
  fullName: string
  sector: string | null
  dialects: string | null
  status: CaregiverStatus
  certificates: CertificateLine[]
}

/** Whether a caregiver matches the search box: part of their name or of their CGV-0114 id, any case. */
export function matchesCaregiverQuery(caregiver: { id: number; fullName: string }, query: string): boolean {
  const q = query.trim().toLowerCase()
  if (!q) return true
  return caregiver.fullName.toLowerCase().includes(q) || caregiverRef(caregiver.id).toLowerCase().includes(q)
}

/** Every caregiver with their register rows (rejected and revoked ones left out), by name. */
export function directoryRows(caregivers: CaregiverOption[], register: CredentialRegisterRow[]): DirectoryCaregiver[] {
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

  return caregivers
    .map((c) => ({
      id: c.id,
      fullName: c.fullName,
      sector: c.sector,
      dialects: c.dialects ?? null,
      status: c.status,
      certificates: byCaregiver.get(c.id) ?? [],
    }))
    .sort((a, b) => a.fullName.localeCompare(b.fullName))
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
