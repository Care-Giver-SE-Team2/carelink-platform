import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  approveAbsence,
  assignCaregiver,
  confirmCoverage,
  decideChange,
  getAbsence,
  listAbsences,
  listFamilyChanges,
  listOwnAbsences,
  recordAbsence,
  rejectAbsence,
  requestAbsence,
  rerosterAbsence,
} from './api'

/**
 * The request each UC-MG04 function makes: path, method, body, and CSRF before
 * every write.
 *
 * @author Wang Ziyu
 */

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function stubFetch(body: unknown = {}) {
  const fetchMock = vi.fn().mockImplementation((url: string) => {
    if (url.endsWith('/csrf')) {
      document.cookie = 'XSRF-TOKEN=absence-token; path=/'
      return Promise.resolve(new Response(null))
    }
    return Promise.resolve(json(body))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function lastRequest(fetchMock: ReturnType<typeof vi.fn>) {
  const calls = fetchMock.mock.calls.filter(([url]) => !String(url).endsWith('/csrf'))
  const [url, init] = calls[calls.length - 1]
  return { url: String(url), init: init as RequestInit }
}

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('absence reads', () => {
  it('lists absences, filtered only when a status was chosen', async () => {
    const fetchMock = stubFetch([])

    await listAbsences()
    expect(lastRequest(fetchMock).url).toBe('/api/absences')
    await listAbsences('PENDING')
    expect(lastRequest(fetchMock).url).toBe('/api/absences?status=PENDING')
  })

  it('reads one absence, the family changes and a caregiver own absences', async () => {
    const fetchMock = stubFetch([])

    await getAbsence(4)
    expect(lastRequest(fetchMock).url).toBe('/api/absences/4')
    await listFamilyChanges()
    expect(lastRequest(fetchMock).url).toBe('/api/roster-changes')
    await listOwnAbsences()
    expect(lastRequest(fetchMock).url).toBe('/api/caregivers/me/absences')
  })
})

describe('absence writes', () => {
  it('initialises CSRF and posts each step to its endpoint', async () => {
    const fetchMock = stubFetch()

    await recordAbsence({ caregiverId: 5, type: 'SICK', startDate: '2026-10-08', endDate: '2026-10-08' })
    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/auth/csrf')
    let request = lastRequest(fetchMock)
    expect(request.url).toBe('/api/absences')
    expect(request.init.method).toBe('POST')
    expect(JSON.parse(String(request.init.body))).toEqual({
      caregiverId: 5,
      type: 'SICK',
      startDate: '2026-10-08',
      endDate: '2026-10-08',
    })
    expect(new Headers(request.init.headers).get('X-XSRF-TOKEN')).toBe('absence-token')

    await approveAbsence(4)
    expect(lastRequest(fetchMock).url).toBe('/api/absences/4/approve')
    await rejectAbsence(4)
    expect(lastRequest(fetchMock).url).toBe('/api/absences/4/reject')

    await rerosterAbsence(4, 'EVEN_WORKLOAD')
    request = lastRequest(fetchMock)
    expect(request.url).toBe('/api/absences/4/rerostering-runs')
    expect(JSON.parse(String(request.init.body))).toEqual({ objective: 'EVEN_WORKLOAD' })

    await assignCaregiver(4, 100, 10)
    request = lastRequest(fetchMock)
    expect(request.url).toBe('/api/absences/4/changes/100/assignment')
    expect(request.init.method).toBe('POST')
    expect(JSON.parse(String(request.init.body))).toEqual({ caregiverId: 10 })

    await confirmCoverage(4)
    expect(lastRequest(fetchMock).url).toBe('/api/absences/4/coverage-confirmation')
  })

  it('sends the family decision and the caregiver request as they are', async () => {
    const fetchMock = stubFetch()

    await decideChange(100, { choice: 'RESCHEDULE', newStart: '2026-10-10T09:00:00' })
    let request = lastRequest(fetchMock)
    expect(request.url).toBe('/api/roster-changes/100/decision')
    expect(JSON.parse(String(request.init.body))).toEqual({ choice: 'RESCHEDULE', newStart: '2026-10-10T09:00:00' })

    await requestAbsence({ type: 'ANNUAL', startDate: '2026-10-20', endDate: '2026-10-21', reason: 'trip' })
    request = lastRequest(fetchMock)
    expect(request.url).toBe('/api/caregivers/me/absences')
    expect(request.init.method).toBe('POST')
  })
})
