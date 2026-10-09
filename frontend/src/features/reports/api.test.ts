import { afterEach, describe, expect, it, vi } from 'vitest'

import { appendAmendment, generateReports, getReport, listReports } from './api'
import { ApiError } from '../../shared/api/client'

/**
 * The request each function makes and the body it hands back, against
 * responses shaped exactly as the backend serialises them - copied from what
 * `ReportControllerTest` renders, `archivedAt: null` included.
 *
 * @author Wang Ziyu
 */

const familyReport = {
  id: 40,
  elderId: 1,
  audience: 'FAMILY',
  periodStart: '2026-09-14',
  periodEnd: '2026-09-20',
  status: 'PUBLISHED',
  dataComplete: false,
  missingItems: ['Visit 13 on 2026-09-18 not closed'],
  generatedBy: 'TEMPLATE',
  createdAt: '2026-09-20T23:00:00',
  archivedAt: null,
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** Answers the CSRF bootstrap like the server does, then everything else with `body`. */
function stubFetch(body: unknown, status = 200) {
  const fetchMock = vi.fn().mockImplementation((url: string) => {
    if (url.endsWith('/csrf')) {
      document.cookie = 'XSRF-TOKEN=report-token; path=/'
      return Promise.resolve(new Response(null))
    }
    return Promise.resolve(json(body, status))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

/** The last call that was not the CSRF bootstrap. */
function lastRequest(fetchMock: ReturnType<typeof vi.fn>) {
  const calls = fetchMock.mock.calls.filter(([url]) => !String(url).endsWith('/csrf'))
  return { url: String(calls[calls.length - 1][0]), init: calls[calls.length - 1][1] as RequestInit }
}

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('report API reads', () => {
  it('asks for a page of reports, with the filters only when they were chosen', async () => {
    const fetchMock = stubFetch({ items: [familyReport], page: 0, size: 20, totalElements: 1 })

    await expect(listReports({ page: 0, size: 20 })).resolves.toMatchObject({ totalElements: 1 })
    expect(lastRequest(fetchMock).url).toBe('/api/reports?page=0&size=20')

    await listReports({ page: 1, size: 5, elderId: 1, audience: 'REGULATOR' })
    expect(lastRequest(fetchMock).url).toBe('/api/reports?page=1&size=5&elderId=1&audience=REGULATOR')
  })

  it('reads one report by its id', async () => {
    const fetchMock = stubFetch({
      ...familyReport,
      sections: [{ title: 'Service completion', body: '3 visits: 1 scheduled, 2 verified.' }],
      disclaimer: 'This summary is prepared from care records for information only and does not constitute medical advice.',
      amendments: [],
    })

    const report = await getReport(40)

    expect(lastRequest(fetchMock).url).toBe('/api/reports/40')
    expect(report.sections[0].title).toBe('Service completion')
    expect(report.disclaimer).toMatch(/does not constitute medical advice/)
  })

  it('hands a 404 back as an ApiError carrying the problem body', async () => {
    stubFetch({ status: 404, title: 'Resource not found', detail: 'Report [9] does not exist' }, 404)

    await expect(getReport(9)).rejects.toBeInstanceOf(ApiError)
  })
})

describe('report API writes', () => {
  it('generates for a period with the CSRF header, leaving the elder out when none was given', async () => {
    const fetchMock = stubFetch([familyReport], 202)

    await expect(
      generateReports({ periodStart: '2026-09-14', periodEnd: '2026-09-20' }),
    ).resolves.toHaveLength(1)

    const { url, init } = lastRequest(fetchMock)
    expect(url).toBe('/api/reports/generate')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({ periodStart: '2026-09-14', periodEnd: '2026-09-20' })
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('report-token')
  })

  it('names the elder when only one is wanted', async () => {
    const fetchMock = stubFetch([familyReport], 202)

    await generateReports({ elderId: 1, periodStart: '2026-09-14', periodEnd: '2026-09-20' })

    expect(JSON.parse(String(lastRequest(fetchMock).init.body))).toEqual({
      elderId: 1,
      periodStart: '2026-09-14',
      periodEnd: '2026-09-20',
    })
  })

  it('appends a correction and hands back what was stored', async () => {
    const fetchMock = stubFetch(
      { id: 3, note: 'Visit 13 was cancelled by the family.', authorUserId: 11, createdAt: '2026-09-21T09:30:00' },
      201,
    )

    const stored = await appendAmendment(40, 'Visit 13 was cancelled by the family.')

    const { url, init } = lastRequest(fetchMock)
    expect(url).toBe('/api/reports/40/amendments')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({ note: 'Visit 13 was cancelled by the family.', kind: 'CORRECTION' })
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('report-token')
    expect(stored.authorUserId).toBe(11)
  })

  it('appends a follow-up when asked to', async () => {
    const fetchMock = stubFetch(
      { id: 4, kind: 'FOLLOW_UP', note: 'Grab bar fitted.', authorUserId: 11, createdAt: '2026-09-24T15:00:00' },
      201,
    )

    const stored = await appendAmendment(40, 'Grab bar fitted.', 'FOLLOW_UP')

    expect(JSON.parse(String(lastRequest(fetchMock).init.body))).toEqual({ note: 'Grab bar fitted.', kind: 'FOLLOW_UP' })
    expect(stored.kind).toBe('FOLLOW_UP')
  })
})
