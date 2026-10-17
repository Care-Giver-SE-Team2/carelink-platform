import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const fetchMock = vi.fn()

beforeEach(() => {
  vi.resetModules()
  vi.stubEnv('MODE', 'production')
  vi.stubEnv('VITE_OPEN_PLATFORM_MOCK', '')
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
  localStorage.clear()
  sessionStorage.clear()
})

afterEach(() => {
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
  vi.resetModules()
})

describe('the live provider API does not use the job simulator', () => {
  it('rejects every job operation without HTTP calls or mock storage mutations', async () => {
    // A pre-existing browser mock must neither authorize nor supply the live job pages.
    localStorage.setItem('carelink.mock.providerPlatform.v1', '{"legacy":"untouched"}')
    const before = localStorage.getItem('carelink.mock.providerPlatform.v1')
    const api = await import('./api')
    expect(api.LIVE).toBe(true)

    const calls = [
      () => api.listJobs('real-token', 'new', 1),
      () => api.getJob('real-token', 'JOB-1'),
      () => api.acceptJob('real-token', 'JOB-1', 'accept-1'),
      () => api.declineJob('real-token', 'JOB-1', { reason: 'NO_PRACTITIONER', note: null }, 'decline-1'),
      () => api.completeJob('real-token', 'JOB-1', { actualStart: '', actualEnd: '', note: '' }, 'complete-1'),
    ]
    for (const call of calls) {
      await expect(call()).rejects.toMatchObject({ status: 501, code: 'jobs_not_available' })
    }
    expect(fetchMock).not.toHaveBeenCalled()
    expect(localStorage.getItem('carelink.mock.providerPlatform.v1')).toBe(before)
    expect(localStorage.length).toBe(1)
    expect(sessionStorage.length).toBe(0)
  })

  it('keeps the real token endpoint without copying its token into mock localStorage', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ access_token: 'real-issued-token', expires_in: 3600 }), { status: 200 }))
    const api = await import('./api')

    expect(await api.requestToken('real-client', 'real-secret')).toMatchObject({ accessToken: 'real-issued-token' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/open/v1/oauth2/token')
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })
})
