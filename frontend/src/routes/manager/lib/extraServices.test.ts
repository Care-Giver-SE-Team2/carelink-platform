import { describe, expect, it } from 'vitest'
import type { ManagedValueAddedServiceRequest } from '../../../features/value-added-services/types'
import { canCancel, formatWhen, matchesFilter, selectedRow, stateTag } from './extraServices'

function row(overrides: Partial<ManagedValueAddedServiceRequest>): ManagedValueAddedServiceRequest {
  return {
    id: 1,
    elderId: 7,
    valueAddedServiceId: 2,
    serviceName: 'Hospital escort',
    requestedSchedule: '2026-10-10T10:00:00',
    specialInstructions: null,
    status: 'DISPATCHED',
    decidedAt: '2026-10-08T09:00:00',
    createdAt: '2026-10-07T17:00:00',
    visitId: 40,
    visitStatus: 'SCHEDULED',
    caregiverId: 9,
    needsCaregiver: false,
    ...overrides,
  }
}

const unstaffed = row({ caregiverId: null, needsCaregiver: true })
const pending = row({ status: 'PENDING_APPROVAL', decidedAt: null, visitId: null, visitStatus: null, caregiverId: null })

describe('extra services', () => {
  it('files each request under one working filter', () => {
    expect(matchesFilter(unstaffed, 'needs')).toBe(true)
    expect(matchesFilter(unstaffed, 'scheduled')).toBe(false)
    expect(matchesFilter(row({}), 'scheduled')).toBe(true)
    expect(matchesFilter(pending, 'awaiting')).toBe(true)
    expect(matchesFilter(row({ status: 'REJECTED' }), 'closed')).toBe(true)
    expect(matchesFilter(row({ status: 'COMPLETED' }), 'all')).toBe(true)
  })

  it('tags a request by its visit once it has one', () => {
    expect(stateTag(unstaffed)).toEqual({ label: 'NEEDS CAREGIVER', tone: 'danger' })
    expect(stateTag(pending).label).toBe('AWAITING FAMILY')
    expect(stateTag(row({ visitStatus: 'EXCEPTION', caregiverId: null })).tone).toBe('danger')
    expect(stateTag(row({ visitStatus: 'IN_PROGRESS' })).label).toBe('IN VISIT')
    expect(stateTag(row({ visitStatus: 'VERIFIED' })).label).toBe('COMPLETED')
    expect(stateTag(row({})).label).toBe('SCHEDULED')
  })

  it('allows cancelling only before the visit starts', () => {
    expect(canCancel(pending)).toBe(true)
    expect(canCancel(unstaffed)).toBe(true)
    expect(canCancel(row({ visitStatus: 'IN_PROGRESS' }))).toBe(false)
    expect(canCancel(row({ status: 'COMPLETED' }))).toBe(false)
  })

  it('reads the server wall-clock time as written', () => {
    expect(formatWhen('2026-10-10T10:00:00')).toBe('Sat 10 Oct, 10:00')
    expect(formatWhen(null)).toBe('—')
  })

  it('opens the row the URL names by request, then by visit, then the first shown', () => {
    const rows = [row({ id: 1, visitId: 40 }), row({ id: 2, visitId: 41 })]
    expect(selectedRow(rows, rows, 2, null)?.id).toBe(2)
    expect(selectedRow(rows, rows, null, 41)?.id).toBe(2)
    expect(selectedRow(rows, rows, null, null)?.id).toBe(1)
    expect(selectedRow(rows, [], 99, null)).toBeNull()
  })
})
