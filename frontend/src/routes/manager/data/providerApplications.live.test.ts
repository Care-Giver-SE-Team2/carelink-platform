import { afterEach, beforeEach, expect, it, vi } from 'vitest'

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  vi.resetModules()
  vi.stubEnv('MODE', 'production')
  vi.stubEnv('VITE_OPEN_PLATFORM_MOCK', 'false')
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
})

afterEach(() => {
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
  vi.resetModules()
})

it('uses the shared client API prefix once for provider reads and decisions', async () => {
  const providers = await import('./providerApplications')
  fetchMock.mockResolvedValueOnce(new Response('[]', { headers: { 'Content-Type': 'application/json' } }))
  await providers.fetchProviderApplications()
  fetchMock.mockResolvedValueOnce(new Response('[]', { headers: { 'Content-Type': 'application/json' } }))
  await providers.fetchProviders()
  fetchMock.mockResolvedValueOnce(new Response('{"id":7}', { headers: { 'Content-Type': 'application/json' } }))
  await expect(providers.approveProviderApplication(3, null)).resolves.toEqual({ providerId: 7 })
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
  await providers.declineProviderApplication(4, 'Missing details')
  await providers.suspendProvider(7)
  await providers.reinstateProvider(7)

  expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
    '/api/provider-applications', '/api/providers', '/api/provider-applications/3/approve',
    '/api/provider-applications/4/reject', '/api/providers/7/suspend', '/api/providers/7/reinstate',
  ])
  expect(JSON.parse(String(fetchMock.mock.calls[3][1]?.body))).toEqual({ reason: 'Missing details' })
})
