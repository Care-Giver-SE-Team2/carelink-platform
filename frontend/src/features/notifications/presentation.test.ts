import { describe, expect, it } from 'vitest'

import { isRead, linkFor, portalOf, when } from './presentation'

/** Which client a path is, where each kind of message leads in it, and how its time reads. @author Wang Ziyu */

describe('portalOf', () => {
  it('reads the client from the path', () => {
    expect(portalOf('/family/changes')).toBe('family')
    expect(portalOf('/caregiver/visits/3')).toBe('caregiver')
    expect(portalOf('/elder')).toBe('elder')
    expect(portalOf('/manager/absences')).toBe('manager')
  })
})

describe('linkFor', () => {
  it('sends each message to the screen this client has for it', () => {
    expect(linkFor({ resourceType: 'INCIDENT', resourceId: 12 }, 'manager')).toBe('/manager/exceptions/12')
    expect(linkFor({ resourceType: 'INCIDENT', resourceId: 12 }, 'family')).toBe('/family/incidents/12')
    expect(linkFor({ resourceType: 'INCIDENT', resourceId: null }, 'manager')).toBe('/manager/exceptions')
    expect(linkFor({ resourceType: 'INCIDENT', resourceId: 12 }, 'caregiver')).toBe('/caregiver/incidents/12')
    expect(linkFor({ resourceType: 'INCIDENT', resourceId: null }, 'caregiver')).toBe('/caregiver/incidents')
    expect(linkFor({ resourceType: 'VISIT', resourceId: 88 }, 'manager')).toBe('/manager/extra-services?visit=88')
    expect(linkFor({ resourceType: 'VISIT', resourceId: null }, 'manager')).toBe('/manager/extra-services')
    expect(linkFor({ resourceType: 'VISIT', resourceId: 88 }, 'caregiver')).toBe('/caregiver/visits/88')
    expect(linkFor({ resourceType: 'VALUE_ADDED_REQUEST', resourceId: 5 }, 'family')).toBe('/family/extra-services')
    expect(linkFor({ resourceType: 'VALUE_ADDED_REQUEST', resourceId: 5 }, 'elder')).toBeNull()
    expect(linkFor({ resourceType: 'ROSTER_CHANGE', resourceId: 4 }, 'family')).toBe('/family/changes')
    expect(linkFor({ resourceType: 'ROSTER_CHANGE', resourceId: 4 }, 'caregiver')).toBe('/caregiver')
    expect(linkFor({ resourceType: 'SPOT_CHECK', resourceId: 9 }, 'family')).toBe('/family/spot-checks')
    expect(linkFor({ resourceType: 'SPOT_CHECK', resourceId: 9 }, 'manager')).toBe('/manager/quality')
    expect(linkFor({ resourceType: 'SPOT_CHECK', resourceId: 9 }, 'caregiver')).toBe('/caregiver/spot-checks?spotCheckId=9')
    expect(linkFor({ resourceType: 'SPOT_CHECK', resourceId: null }, 'caregiver')).toBe('/caregiver/spot-checks')
    expect(linkFor({ resourceType: 'CREDENTIAL', resourceId: 2 }, 'manager')).toBe('/manager/certifications')
    expect(linkFor({ resourceType: 'ABSENCE', resourceId: 5 }, 'manager')).toBe('/manager/absences/5')
    expect(linkFor({ resourceType: 'ABSENCE', resourceId: null }, 'manager')).toBe('/manager/absences')
  })

  it('has nowhere to go when this client has no screen for it', () => {
    expect(linkFor({ resourceType: 'ROSTER_CHANGE', resourceId: 4 }, 'elder')).toBeNull()
    expect(linkFor({ resourceType: 'SOMETHING_NEW', resourceId: 1 }, 'manager')).toBeNull()
    expect(linkFor({ resourceType: null, resourceId: null }, 'manager')).toBeNull()
  })
})

describe('family incident links', () => {
  it.each([null, 0, -1, 1.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1])('rejects unsafe incident number %s', (id) => {
    expect(linkFor({ resourceType: 'INCIDENT', resourceId: id }, 'family')).toBeNull()
  })
})

describe('isRead', () => {
  it('is read once opened, unread while only delivered', () => {
    expect(isRead({ status: 'READ' })).toBe(true)
    expect(isRead({ status: 'SENT' })).toBe(false)
  })
})

describe('when', () => {
  it('shows the wall-clock time the server wrote, whatever the browser zone', () => {
    expect(when('2026-10-07T09:00:05+08:00')).toBe('Wed 7 Oct, 09:00')
    expect(when('2026-10-07T09:00:05')).toBe('Wed 7 Oct, 09:00')
    expect(when('2026-11-03T18:30:00')).toBe('Tue 3 Nov, 18:30')
  })

  it('leaves text it cannot read as it is', () => {
    expect(when('soon')).toBe('soon')
  })
})
