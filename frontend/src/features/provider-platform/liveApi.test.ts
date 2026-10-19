import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './errors'
import * as live from './liveApi'

/** A token whose payload carries `claims`; unsigned, as only CareLink checks tokens. */
function tokenWith(claims: Record<string, unknown>): string {
  const part = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_')
  return `${part({ alg: 'RS256' })}.${part(claims)}.signature`
}

function answer(status: number, body: unknown, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } })
}

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function lastRequest(): { url: string; init: RequestInit; headers: Headers } {
  const [url, init] = fetchMock.mock.calls.at(-1)!
  return { url: String(url), init: init!, headers: new Headers(init!.headers) }
}

describe('signing in to the portal', () => {
  it('sends the email and password and keeps the access token', async () => {
    fetchMock.mockResolvedValue(answer(200, { accessToken: 'portal-token', expiresIn: 3600, tokenType: 'Bearer' }))

    const session = await live.signIn(' ops@brighthome.sg ', 'correct horse battery')

    expect(session.token).toBe('portal-token')
    expect(session.expiresAt).toBeGreaterThan(Date.now() + 3500_000)
    const { url, headers, init } = lastRequest()
    expect(url).toBe('/api/open/v1/portal/sessions')
    expect(init.method).toBe('POST')
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(JSON.parse(String(init.body))).toEqual({ email: 'ops@brighthome.sg', password: 'correct horse battery' })
  })

  it('answers a wrong password as the pages expect it: 401', async () => {
    fetchMock.mockResolvedValue(answer(401, { status: 401, code: 'INVALID_CREDENTIALS', detail: 'Incorrect email or password.' }))

    const failure = await live.signIn('ops@brighthome.sg', 'wrong').catch((e: unknown) => e)

    expect(failure).toBeInstanceOf(ApiError)
    expect(failure).toMatchObject({ status: 401, code: 'invalid_credentials' })
  })

  it('passes on the lock after too many wrong passwords, with its message', async () => {
    fetchMock.mockResolvedValue(
      answer(429, { status: 429, code: 'TOO_MANY_ATTEMPTS', detail: 'Too many wrong passwords. Try again in 15 minutes.' }),
    )

    await expect(live.signIn('ops@brighthome.sg', 'wrong')).rejects.toMatchObject({
      status: 429,
      code: 'too_many_attempts',
      message: 'Too many wrong passwords. Try again in 15 minutes.',
    })
  })
})

describe('the Open API', () => {
  it('sends the portal token and turns the status into the pages’ lower case', async () => {
    fetchMock.mockResolvedValue(
      answer(200, {
        id: 'APP-2026-00417',
        organisationName: 'BrightHome Care',
        contactName: 'Mei Ling Tan',
        contactEmail: 'ops@brighthome.sg',
        contactPhone: '+6591234567',
        services: ['Companionship'],
        webhookUrl: 'https://hooks.brighthome.sg/carelink',
        status: 'APPROVED',
        submittedAt: '2026-10-15T09:02:11+08:00',
        reviewedAt: '2026-10-15T10:00:00+08:00',
        reason: null,
        suspended: false,
      }),
    )

    const application = await live.getMyApplication('portal-token')

    expect(application.status).toBe('approved')
    expect(application.id).toBe('APP-2026-00417')
    expect(lastRequest().url).toBe('/api/open/v1/portal/application')
    expect(lastRequest().headers.get('Authorization')).toBe('Bearer portal-token')
  })

  it('turns a refusal into an ApiError with the contract’s code in lower case', async () => {
    fetchMock.mockResolvedValue(answer(409, { status: 409, code: 'ACCOUNT_EXISTS', detail: 'Sign in instead.' }))

    const failure = await live
      .submitApplication(
        {
          organisationName: 'BrightHome Care',
          contactName: 'Mei Ling Tan',
          contactEmail: 'ops@brighthome.sg',
          contactPhone: '+6591234567',
          services: ['Companionship'],
          webhookUrl: 'https://hooks.brighthome.sg/carelink',
        },
        'correct horse battery',
      )
      .catch((e: unknown) => e)

    expect(failure).toMatchObject({ status: 409, code: 'account_exists', message: 'Sign in instead.' })
    expect(JSON.parse(String(lastRequest().init.body))).toMatchObject({ password: 'correct horse battery', agreedToTerms: true })
    expect(lastRequest().headers.get('Authorization')).toBeNull()
  })

  it('reports a request that never got an answer as status 0', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(live.getIntegrationHealth('portal-token')).rejects.toMatchObject({ status: 0, code: 'network_error' })
  })

  it('shows the credentials summary’s scopes and token endpoint as the page does', async () => {
    fetchMock.mockResolvedValue(
      answer(200, {
        clientId: 'client-1',
        secretHint: 'wxyz',
        secretIssuedAt: '2026-10-15T09:02:11+08:00',
        scopes: ['open-platform/jobs.read', 'open-platform/jobs.write'],
        tokenUrl: '/api/open/v1/oauth2/token',
      }),
    )

    expect(await live.getCredentialsSummary('portal-token')).toEqual({
      clientId: 'client-1',
      secretHint: 'wxyz',
      secretIssuedAt: '2026-10-15T09:02:11+08:00',
      scope: 'open-platform/jobs.read open-platform/jobs.write',
      tokenEndpoint: `POST ${window.location.origin}/api/open/v1/oauth2/token`,
    })
  })

  it('never sends ping as a subscription', async () => {
    fetchMock.mockResolvedValue(answer(200, { url: 'https://new.example.sg', events: ['job.dispatched'], secretHint: 'abcd', secretIssuedAt: '' }))

    await live.updateWebhookSettings('portal-token', { url: 'https://new.example.sg', events: ['job.dispatched', 'ping'] })

    expect(lastRequest().init.method).toBe('PUT')
    expect(JSON.parse(String(lastRequest().init.body))).toEqual({ url: 'https://new.example.sg', events: ['job.dispatched'] })
  })

  it('lays a delivery out as the delivery log shows it', async () => {
    fetchMock.mockResolvedValue(
      answer(202, {
        id: 'dlv_1',
        event: 'ping',
        jobId: null,
        createdAt: '2026-10-15T09:02:11+08:00',
        status: 'RETRYING',
        attempts: [{ at: '2026-10-15T09:02:12+08:00', responseStatus: 500, durationMs: 31 }],
        nextAttemptAt: '2026-10-15T09:03:12+08:00',
        request: {
          url: 'https://hooks.brighthome.sg/carelink?v=1',
          headers: { 'Content-Type': 'application/json', 'X-CareLink-Delivery': 'dlv_1' },
          body: '{"id":"dlv_1","type":"ping"}',
        },
      }),
    )

    const delivery = await live.sendTestEvent('portal-token')

    expect(delivery.status).toBe('retrying')
    expect(delivery.nextRetryAt).toBe('2026-10-15T09:03:12+08:00')
    expect(delivery.attempts).toEqual([{ at: '2026-10-15T09:02:12+08:00', status: 500, ms: 31 }])
    expect(delivery.headers.split('\n')).toEqual([
      'POST /carelink?v=1 HTTP/1.1',
      'Host: hooks.brighthome.sg',
      'Content-Type: application/json',
      'X-CareLink-Delivery: dlv_1',
    ])
    expect(delivery.payload).toBe('{\n  "id": "dlv_1",\n  "type": "ping"\n}')
  })

  it('asks for the first hundred deliveries', async () => {
    fetchMock.mockResolvedValue(answer(200, { items: [], page: 0, size: 100, totalElements: 0 }))

    expect(await live.listWebhookDeliveries('portal-token')).toEqual([])
    expect(lastRequest().url).toBe('/api/open/v1/portal/webhook/deliveries?size=100')
  })
})

describe('a server token from the client credentials', () => {
  it('uses the client-credentials grant with Basic authentication, and reads the scope from the token', async () => {
    const accessToken = tokenWith({ client_id: 'client-1', scope: 'open-platform/jobs.read open-platform/jobs.write' })
    fetchMock.mockResolvedValue(answer(200, { access_token: accessToken, expires_in: 3600, token_type: 'Bearer' }))

    const token = await live.requestToken(' client-1 ', 'secret-1')

    expect(token.accessToken).toBe(accessToken)
    expect(token.scope).toBe('open-platform/jobs.read open-platform/jobs.write')
    expect(token.expiresAt - token.issuedAt).toBe(3600_000)
    const { url, headers, init } = lastRequest()
    expect(url).toBe('/api/open/v1/oauth2/token')
    expect(headers.get('Authorization')).toBe(`Basic ${btoa('client-1:secret-1')}`)
    expect(String(init.body)).toBe('grant_type=client_credentials')
  })

  it('answers a wrong secret as the Sandbox expects it: 401 invalid_client', async () => {
    fetchMock.mockResolvedValue(answer(401, { error: 'invalid_client' }))

    await expect(live.requestToken('client-1', 'wrong')).rejects.toMatchObject({ status: 401, code: 'invalid_client' })
  })
})

describe('the job calls, which report serves', () => {
  const wireJob = {
    id: 'JOB-24081',
    version: 1,
    status: 'ACCEPTED',
    serviceName: 'Hospital escort',
    district: '19',
    areaName: 'Hougang, Punggol, Serangoon Gardens',
    scheduledStart: '2026-10-17T10:00:00+08:00',
    durationMinutes: 180,
    respondBy: null,
    receivedAt: '2026-10-16T09:02:11+08:00',
    cancelReason: null,
    elderSummary: 'Male, 81 · uses a walking aid',
    languages: 'Hokkien, English',
    bookedBy: 'Family member',
    instructions: null,
    address: { text: 'Blk 123 Hougang Ave 1, #05-67, Singapore 530123', accessNotes: null },
    timeline: [
      { step: 'DISPATCHED', at: '2026-10-16T09:02:11+08:00' },
      { step: 'ACCEPTED', at: '2026-10-16T09:30:00+08:00' },
    ],
    completion: null,
    decline: null,
  }

  it('asks for a tab as the job API’s states, from page 0, and adds the closed states’ counts', async () => {
    fetchMock.mockResolvedValue(
      answer(200, {
        items: [{ ...wireJob, status: 'DECLINED' }],
        page: 1,
        size: 5,
        totalElements: 6,
        counts: { NEW: 2, ACCEPTED: 1, DECLINED: 4, COMPLETED: 3, CANCELLED: 2 },
      }),
    )

    const page = await live.listJobs('server-token', 'closed', 2)

    const { url, headers } = lastRequest()
    expect(url).toBe('/api/open/v1/jobs?status=DECLINED&status=CANCELLED&page=1&size=5')
    expect(headers.get('Authorization')).toBe('Bearer server-token')
    expect(page).toMatchObject({ page: 2, pageSize: 5, total: 6 })
    expect(page.counts).toEqual({ new: 2, accepted: 1, completed: 3, closed: 6 })
    expect(page.items[0]).toMatchObject({ id: 'JOB-24081', service: 'Hospital escort', durationMin: 180, status: 'declined' })
  })

  it('accepts with the Idempotency-Key and shows the address as the Sandbox does', async () => {
    fetchMock.mockResolvedValue(answer(200, wireJob))

    const job = await live.acceptJob('server-token', 'JOB-24081', 'key-1')

    const { url, init, headers } = lastRequest()
    expect(url).toBe('/api/open/v1/jobs/JOB-24081/accept')
    expect(init.method).toBe('POST')
    expect(headers.get('Idempotency-Key')).toBe('key-1')
    expect(job.address).toEqual({ line1: 'Blk 123 Hougang Ave 1, #05-67, Singapore 530123', line2: '' })
    expect(job.timeline.map((step) => step.step)).toEqual(['dispatched', 'accepted'])
    expect(job.instructions).toBe('')
  })

  it('completes with the contract’s field names, and no empty note', async () => {
    fetchMock.mockResolvedValue(answer(200, { ...wireJob, status: 'COMPLETED' }))

    await live.completeJob(
      'server-token',
      'JOB-24081',
      { actualStart: '2026-10-17T10:05:00+08:00', actualEnd: '2026-10-17T13:00:00+08:00', note: ' ' },
      'key-2',
    )

    const { url, init } = lastRequest()
    expect(url).toBe('/api/open/v1/jobs/JOB-24081/complete')
    expect(JSON.parse(String(init.body))).toEqual({
      actualStart: '2026-10-17T10:05:00+08:00',
      actualEnd: '2026-10-17T13:00:00+08:00',
      note: null,
    })
  })

  it('passes on a cancelled job’s refusal with its code', async () => {
    fetchMock.mockResolvedValue(answer(409, { status: 409, code: 'JOB_CANCELLED', detail: 'The elder withdrew it.' }))

    await expect(live.acceptJob('server-token', 'JOB-24081', 'key-3')).rejects.toMatchObject({
      status: 409,
      code: 'job_cancelled',
      message: 'The elder withdrew it.',
    })
  })
})
