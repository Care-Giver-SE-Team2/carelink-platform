import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createCaregiverReview,
  fetchCaregiverReviews,
  fetchReviewableCaregivers,
} from './api'

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('caregiver reviews API', () => {
  it('loads review history for the selected elder', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json([]))
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchCaregiverReviews(21)).resolves.toEqual([])
    expect(fetchMock.mock.calls[0][0]).toBe('/api/family/caregiver-reviews?elderId=21')
  })

  it('loads caregivers that can be reviewed', async () => {
    const caregivers = [{ id: 31, fullName: 'John Tan' }]
    const fetchMock = vi.fn().mockResolvedValue(json(caregivers))
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchReviewableCaregivers(21)).resolves.toEqual(caregivers)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/family/caregiver-reviews/caregivers?elderId=21')
  })

  it('creates a review with CSRF and the complete FM09 payload', async () => {
    document.cookie = 'XSRF-TOKEN=review%2Btoken; path=/'
    const response = {
      id: 51, familyMemberId: 11, elderId: 21, caregiverId: 31,
      caregiverName: 'John Tan', periodStart: '2026-09-01', periodEnd: '2026-09-30',
      overallRating: 5, punctualityScore: 4, careQualityScore: 5,
      feedbackNotes: 'Very patient.', renewalDecision: 'RENEW_CURRENT' as const,
      createdAt: '2026-10-07T20:00:00',
    }
    const fetchMock = vi.fn().mockResolvedValue(json(response, 201))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createCaregiverReview({
      elderId: 21,
      caregiverId: 31,
      periodStart: '2026-09-01',
      periodEnd: '2026-09-30',
      overallRating: 5,
      punctualityScore: 4,
      careQualityScore: 5,
      feedbackNotes: 'Very patient.',
      renewalDecision: 'RENEW_CURRENT',
    })).resolves.toEqual(response)

    expect(fetchMock.mock.calls[0][0]).toBe('/api/family/caregiver-reviews')
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toMatchObject({
      elderId: 21,
      caregiverId: 31,
      renewalDecision: 'RENEW_CURRENT',
    })
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('review+token')
  })
})
