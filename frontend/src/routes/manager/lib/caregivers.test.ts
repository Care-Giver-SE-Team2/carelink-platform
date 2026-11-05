import { describe, expect, it } from 'vitest'
import type { CaregiverOption } from '../../../shared/api/profile'
import { certRow } from './certRow.fixture'
import { certificateSummary, directoryRows, languagesLabel, matchesCaregiverQuery, nextApplicationId } from './caregivers'
import type { CertificateLine } from './caregivers'

function caregiver(overrides: Partial<CaregiverOption>): CaregiverOption {
  return { id: 114, fullName: 'Devi Raman', sector: 'AMK', dialects: 'Tamil,English', status: 'AVAILABLE', assignable: true, ...overrides }
}

function line(state: CertificateLine['state']): CertificateLine {
  return { key: state, name: 'First aid', state, expiryDate: null, daysUntilExpiry: null, registerId: 1 }
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
    )

    expect(rows.map((r) => r.fullName)).toEqual(['Devi Raman', 'Ong Wei Jie'])
    expect(rows[0].certificates.map((c) => c.registerId)).toEqual([1])
    expect(rows[1].certificates).toMatchObject([{ name: 'Dementia care', state: 'SUBMITTED', registerId: 4 }])
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

describe('matchesCaregiverQuery', () => {
  const devi = { id: 114, fullName: 'Devi Raman' }

  it('matches part of the name in any case, ignoring surrounding spaces', () => {
    expect(matchesCaregiverQuery(devi, 'raman')).toBe(true)
    expect(matchesCaregiverQuery(devi, '  DEVI ')).toBe(true)
    expect(matchesCaregiverQuery(devi, 'ong')).toBe(false)
  })

  it('matches the CGV id, with or without its prefix', () => {
    expect(matchesCaregiverQuery(devi, 'cgv-0114')).toBe(true)
    expect(matchesCaregiverQuery(devi, '114')).toBe(true)
    expect(matchesCaregiverQuery(devi, '120')).toBe(false)
  })

  it('matches everyone when the search is blank', () => {
    expect(matchesCaregiverQuery(devi, '   ')).toBe(true)
  })
})
