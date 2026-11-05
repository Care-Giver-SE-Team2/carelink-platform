import { afterEach, describe, expect, it, vi } from 'vitest'
import { getCredential, getCredentials, getCredentialTypes, submitCredential } from './api'

afterEach(() => { vi.unstubAllGlobals(); document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/' })
describe('credential contract', () => {
  it('reads own catalogue, paginated records and an encoded detail path', async () => {
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(new Response('[]', { headers: { 'Content-Type': 'application/json' } })))
    vi.stubGlobal('fetch', fetch)
    await getCredentialTypes(); await getCredentials(2); await getCredential('a/b')
    expect(fetch.mock.calls.map(call => call[0])).toEqual(['/api/caregivers/me/credential-types', '/api/caregivers/me/credentials?page=2&size=20', '/api/caregivers/me/credentials/a%2Fb'])
  })
  it.each([['NEW', null, ''], ['RENEWAL', 7, '/7/renewals'], ['RESUBMIT', 8, '/8/resubmissions']] as const)('submits %s with browser multipart boundary and CSRF', async (action, id, suffix) => {
    const fetch = vi.fn().mockResolvedValue(new Response('{}', { headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetch); document.cookie = 'XSRF-TOKEN=credential-token; path=/'
    const file = new File(['demo'], 'certificate.png', { type: 'image/png' })
    await submitCredential(action, id, { credentialTypeId: 2, certificateNo: 'N', issuingBody: 'Demo', validFrom: '2026-10-11', expiryDate: '2027-01-01', noExpiry: false, clientRequestId: 'same-key', files: [file] })
    const [url, init] = fetch.mock.calls[0]
    expect(url).toBe('/api/caregivers/me/credentials' + suffix)
    expect(init.headers.has('Content-Type')).toBe(false)
    expect(init.headers.get('X-XSRF-TOKEN')).toBe('credential-token')
    expect(init.credentials).toBe('include'); expect(init.body.getAll('files')).toEqual([file])
    expect(init.body.get('expiryDate')).toBe('2027-01-01')
  })
  it('omits expiry only for explicit No expiry', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('{}', { headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetch)
    await submitCredential('NEW', null, { credentialTypeId: 1, certificateNo: '', issuingBody: 'Demo', validFrom: '2026-10-11', expiryDate: '', noExpiry: true, clientRequestId: 'key', files: [] })
    expect(fetch.mock.calls[0][1].body.has('expiryDate')).toBe(false)
  })
})
