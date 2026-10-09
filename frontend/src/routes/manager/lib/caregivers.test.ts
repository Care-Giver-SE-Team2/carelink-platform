import { describe, expect, it } from 'vitest'
import type { CaregiverOption } from '../../../shared/api/profile'
import type { ApprovedApplicant } from '../data/caregiverApplications'
import { certRow } from './certRow.fixture'
import { certificateSummary, directoryRows, languagesLabel, nextApplicationId } from './caregivers'
import type { CertificateLine } from './caregivers'

const TODAY = new Date(2026, 9, 9)

function caregiver(overrides: Partial<CaregiverOption>): CaregiverOption {
  return { id: 114, fullName: 'Devi Raman', sector: 'AMK', dialects: 'Tamil,English', status: 'AVAILABLE', assignable: true, ...overrides }
}

function line(state: CertificateLine['state']): CertificateLine {
  return { key: state, name: 'First aid', state, expiryDate: null, daysUntilExpiry: null, registerId: 1 }
}

const approvedApplicant: ApprovedApplicant = {
  id: 7,
  caregiverId: 9007,
  fullName: 'Lim Hui Min',
  username: 'lim.huimin',
  phone: null,
  sector: 'AMK',
  dialects: 'Hokkien',
  submittedAt: '2026-10-09T01:00:00Z',
  approvedAt: '2026-10-09T05:00:00Z',
  certificates: [
    {
      id: 71,
      credentialTypeName: 'First aid',
      certificateNo: 'SRC-FA-73310',
      issuingBody: 'Singapore Red Cross',
      validFrom: null,
      expiryDate: '2026-10-29',
      fileName: 'first-aid.pdf',
      scanUrl: null,
      scanContentType: null,
    },
  ],
}

describe('directoryRows', () => {
  it('attaches each caregiver their register rows, leaving out rejected and revoked ones', () => {
    const rows = directoryRows(
      [caregiver({}), caregiver({ id: 120, fullName: 'Ong Wei Jie' })],
      [
        certRow({ id: 1, caregiverId: 114, state: 'PUBLISHED' }),
        certRow({ id: 2, caregiverId: 114, state: 'REJECTED' }),
        certRow({ id: 3, caregiverId: 114, state: 'REVOKED' }),
        certRow({ id: 4, caregiverId: 120, state: 'SUBMITTED', credentialTypeName: 'Dementia care' }),
      ],
      [],
      TODAY,
    )

    expect(rows.map((r) => r.fullName)).toEqual(['Devi Raman', 'Ong Wei Jie'])
    expect(rows[0].certificates.map((c) => c.registerId)).toEqual([1])
    expect(rows[1].certificates).toMatchObject([{ name: 'Dementia care', state: 'SUBMITTED', registerId: 4 }])
  })

  it('adds approved applicants, by name, with every uploaded certificate published', () => {
    const rows = directoryRows([caregiver({}), caregiver({ id: 120, fullName: 'Ong Wei Jie' })], [], [approvedApplicant], TODAY)

    expect(rows.map((r) => r.fullName)).toEqual(['Devi Raman', 'Lim Hui Min', 'Ong Wei Jie'])
    expect(rows[1]).toMatchObject({ id: 9007, status: 'AVAILABLE', approvedAt: '2026-10-09T05:00:00Z' })
    expect(rows[1].certificates).toEqual([
      {
        key: 'application-71',
        name: 'First aid',
        state: 'PUBLISHED',
        expiryDate: '2026-10-29',
        daysUntilExpiry: 20,
        registerId: null,
      },
    ])
  })

  it('leaves an applicant approved without certificates onboarding', () => {
    const [row] = directoryRows([], [], [{ ...approvedApplicant, certificates: [] }], TODAY)
    expect(row.status).toBe('ONBOARDING')
  })
})

describe('certificateSummary', () => {
  it('counts valid, expiring, waiting and expired certificates', () => {
    expect(certificateSummary([line('PUBLISHED'), line('REMINDED'), line('SUBMITTED'), line('EXPIRED')])).toBe(
      '2 valid · 1 expiring · 1 to review · 1 expired',
    )
    expect(certificateSummary([line('PUBLISHED')])).toBe('1 valid')
    expect(certificateSummary([])).toBe('none')
  })
})

describe('languagesLabel', () => {
  it('spaces the stored list and treats an empty one as not given', () => {
    expect(languagesLabel('Hokkien,Mandarin')).toBe('Hokkien, Mandarin')
    expect(languagesLabel(' , ')).toBeNull()
    expect(languagesLabel(null)).toBeNull()
  })
})

describe('nextApplicationId', () => {
  it('opens the next application down, else the one above, else none', () => {
    const rows = [{ id: 7 }, { id: 6 }, { id: 5 }]
    expect(nextApplicationId(rows, 7)).toBe(6)
    expect(nextApplicationId(rows, 5)).toBe(6)
    expect(nextApplicationId([{ id: 7 }], 7)).toBeNull()
  })
})
