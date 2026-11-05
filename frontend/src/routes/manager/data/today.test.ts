import { describe, expect, it } from 'vitest'
import type { CaregiverOption } from '../../../shared/api/profile'
import type { VisitResponse } from '../../../shared/api/visit'
import type { ElderRow } from './elders'
import { toRoster } from './today'

const elders = [{ id: '1', name: 'Lim Ah Kow', sector: 'S45' }] as ElderRow[]
const caregivers = [{ id: 10, fullName: 'Siti Rahmah' }] as CaregiverOption[]

const visit = (extra: Partial<VisitResponse>): VisitResponse => ({
  id: 1,
  elderId: 1,
  caregiverId: 10,
  carePlanNodeId: null,
  absenceId: null,
  serviceType: 'Bathing assist',
  scheduledStart: '2026-09-27T09:00:00',
  scheduledEnd: '2026-09-27T10:00:00',
  checkedInAt: null,
  checkedOutAt: null,
  status: 'SCHEDULED',
  stateDeadline: null,
  carePlanId: null,
  version: 0,
  createdAt: null,
  updatedAt: null,
  ...extra,
})

// 09:05 in Singapore, written in UTC so the test doesn't depend on the machine's zone.
const NOW = new Date('2026-09-27T01:05:00Z')

const only = (v: VisitResponse, now = NOW) => toRoster([v], elders, caregivers, now).visits[0]

describe('toRoster', () => {
  it('flags an assigned visit whose start has passed with nobody checked in', () => {
    expect(only(visit({})).state).toBe('not_checked_in')
  })

  it('keeps a visit scheduled until its start', () => {
    expect(only(visit({}), new Date('2026-09-27T00:59:00Z')).state).toBe('scheduled')
  })

  it('reports an overdue unassigned visit as needing cover, not as a missed check-in', () => {
    expect(only(visit({ caregiverId: null })).state).toBe('needs_cover')
  })

  it('leaves an exception as an exception', () => {
    expect(only(visit({ status: 'EXCEPTION' })).state).toBe('exception')
  })

  it('shows when the caregiver checked in, and how late', () => {
    const row = only(visit({ status: 'IN_PROGRESS', checkedInAt: '2026-09-27T09:14:30' }))
    expect(row.state).toBe('in_visit')
    expect(row.attendance).toEqual({ checkedIn: '09:14', checkedOut: undefined, lateMinutes: 14 })
  })

  it('counts an early check-in as on time, and adds the check-out', () => {
    const row = only(
      visit({ status: 'COMPLETED', checkedInAt: '2026-09-27T08:50:00', checkedOutAt: '2026-09-27T09:55:00' }),
    )
    expect(row.attendance).toEqual({ checkedIn: '08:50', checkedOut: '09:55', lateMinutes: 0 })
  })

  it('has no attendance before check-in', () => {
    expect(only(visit({})).attendance).toBeUndefined()
  })
})
