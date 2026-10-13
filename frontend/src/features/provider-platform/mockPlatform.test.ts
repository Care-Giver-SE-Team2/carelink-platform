import { beforeEach, describe, expect, it } from 'vitest'
import { ApiError } from './errors'
import {
  acceptJob,
  approveApplication,
  collectCredentials,
  completeJob,
  declineJob,
  getMyApplication,
  signIn,
  signOut,
  getJob,
  listApplicationsForReview,
  listApprovedProviders,
  getCredentialsSummary,
  getIntegrationHealth,
  getWebhookSettings,
  listWebhookDeliveries,
  reinstateProvider,
  resendDelivery,
  rotateClientSecret,
  rotateWebhookSecret,
  sendTestEvent,
  suspendProvider,
  updateWebhookSettings,
  listJobs,
  rejectApplication,
  requestToken,
  resetMockPlatform,
  submitApplication,
} from './mockPlatform'

const NOW = Date.parse('2026-10-12T02:00:00Z')

const SEEDED_ID = 'APP-2026-00409'
const PASSWORD = 'kinetic-password'

/** A portal session for the seeded provider, signed in now. */
function seeded(): string {
  return signIn(SEEDED_EMAIL, 'brighthome-demo').session
}

/** A portal session for the provider the tests apply as. */
function mine(): string {
  return signIn(DETAILS.contactEmail, PASSWORD).session
}
const SEEDED_EMAIL = 'partners@brighthome.sg'

const DETAILS = {
  organisationName: 'Kinetic Care Pte Ltd',
  contactName: 'Rachel Ong',
  contactEmail: 'rachel@kinetic.sg',
  contactPhone: '+65 6123 4567',
  services: ['Companionship'],
  webhookUrl: 'https://hooks.kinetic.sg/carelink',
}

function apiError(fn: () => unknown): ApiError {
  try {
    fn()
  } catch (err) {
    if (err instanceof ApiError) return err
    throw err
  }
  throw new Error('expected an ApiError')
}

/** Submits and approves an application, collects its credentials and connects. */
function connectedProvider() {
  const { applicationId } = submitApplication(DETAILS, PASSWORD, NOW)
  const seq = listApplicationsForReview().find((a) => a.id === applicationId)!.seq
  approveApplication(seq, null, NOW)
  const credentials = collectCredentials(mine())
  const token = requestToken(credentials.clientId, credentials.clientSecret, NOW)
  return { applicationId, credentials, token: token.accessToken }
}

beforeEach(() => resetMockPlatform(NOW))

describe('applications and portal accounts', () => {
  it('takes an application and signs its provider in, ignoring the email’s case', () => {
    const { applicationId } = submitApplication(DETAILS, PASSWORD, NOW)
    expect(applicationId).toMatch(/^APP-2026-\d{5}$/)

    const found = getMyApplication(signIn('RACHEL@kinetic.sg', PASSWORD).session)
    expect(found.id).toBe(applicationId)
    expect(found.status).toBe('pending')
    expect(found.organisationName).toBe('Kinetic Care Pte Ltd')
    expect(listApplicationsForReview()[0].id).toBe(applicationId)
  })

  it('refuses a wrong password, an unknown email and a short password', () => {
    submitApplication(DETAILS, PASSWORD, NOW)
    expect(apiError(() => signIn(DETAILS.contactEmail, 'not-the-password')).code).toBe('invalid_login')
    expect(apiError(() => signIn('someone@else.sg', PASSWORD)).status).toBe(401)
    expect(apiError(() => submitApplication({ ...DETAILS, contactEmail: 'new@kinetic.sg' }, 'short', NOW)).code).toBe('weak_password')
  })

  it('keeps one account per email, unless its application was turned down', () => {
    submitApplication(DETAILS, PASSWORD, NOW)
    expect(apiError(() => submitApplication(DETAILS, PASSWORD, NOW)).code).toBe('account_exists')

    const seq = listApplicationsForReview().find((a) => a.contactEmail === DETAILS.contactEmail)!.seq
    rejectApplication(seq, 'We do not cover District 16 yet', NOW)
    const again = submitApplication(DETAILS, 'a-new-password', NOW + 1000).applicationId
    expect(getMyApplication(signIn(DETAILS.contactEmail, 'a-new-password').session).id).toBe(again)
    expect(apiError(() => signIn(DETAILS.contactEmail, PASSWORD)).status).toBe(401)
  })

  it('ends a session on sign-out, and after eight hours', () => {
    submitApplication(DETAILS, PASSWORD, NOW)
    const session = signIn(DETAILS.contactEmail, PASSWORD, NOW).session
    expect(getMyApplication(session, NOW + 7 * 3600_000).status).toBe('pending')
    expect(apiError(() => getMyApplication(session, NOW + 9 * 3600_000)).code).toBe('not_signed_in')

    const other = mine()
    signOut(other)
    expect(apiError(() => getMyApplication(other)).status).toBe(401)
  })

  it('shows the reason a rejected application was rejected', () => {
    const { applicationId } = submitApplication(DETAILS, PASSWORD, NOW)
    const seq = listApplicationsForReview().find((a) => a.id === applicationId)!.seq
    rejectApplication(seq, 'We do not cover District 16 yet', NOW)

    const found = getMyApplication(mine())
    expect(found.status).toBe('rejected')
    expect(found.reason).toBe('We do not cover District 16 yet')
    expect(apiError(() => rejectApplication(seq, 'again')).status).toBe(409)
  })
})

describe('credentials and tokens', () => {
  it('hands credentials over once, and only after approval', () => {
    const { applicationId } = submitApplication(DETAILS, PASSWORD, NOW)
    expect(apiError(() => collectCredentials(mine())).status).toBe(409)

    const seq = listApplicationsForReview().find((a) => a.id === applicationId)!.seq
    approveApplication(seq, 'Welcome', NOW)
    const credentials = collectCredentials(mine())
    expect(credentials.clientId).toMatch(/^cl_prov_/)
    expect(credentials.clientSecret).toMatch(/^cs_live_/)
    expect(credentials.webhookSecret).toMatch(/^whsec_/)
    expect(apiError(() => collectCredentials(mine())).status).toBe(410)

    const provider = listApprovedProviders().find((p) => p.id === applicationId)!
    expect(provider.clientId).toBe(credentials.clientId)
    expect(provider).not.toHaveProperty('clientSecret')
  })

  it('issues an hour-long token for the right secret and refuses a wrong one', () => {
    const { credentials } = connectedProvider()
    const token = requestToken(credentials.clientId, credentials.clientSecret, NOW)
    expect(token.scope).toBe('jobs:read jobs:write')
    expect(token.expiresAt - token.issuedAt).toBe(60 * 60 * 1000)

    const refused = apiError(() => requestToken(credentials.clientId, 'wrong', NOW))
    expect(refused.status).toBe(401)
    expect(refused.code).toBe('invalid_client')
  })

  it('refuses job calls with an expired token', () => {
    const { token } = connectedProvider()
    expect(listJobs(token, 'new', 1, NOW + 59 * 60 * 1000).total).toBeGreaterThan(0)
    expect(apiError(() => listJobs(token, 'new', 1, NOW + 61 * 60 * 1000)).status).toBe(401)
  })
})

describe('jobs', () => {
  it('dispatches jobs on approval and lists them by tab, with every count', () => {
    const { token } = connectedProvider()
    const page = listJobs(token, 'new', 1, NOW)
    expect(page.counts).toEqual({ new: 4, accepted: 0, completed: 0, closed: 0 })
    expect(page.items).toHaveLength(4)
    expect(page.items[0].receivedAt >= page.items[1].receivedAt).toBe(true)
    expect(page.items.every((j) => /^\d\d$/.test(j.district))).toBe(true)
  })

  it('pages the seeded provider’s jobs five at a time', () => {
    const token = requestToken('cl_prov_BrightHome9', 'cs_demo_brighthome', NOW).accessToken
    const first = listJobs(token, 'new', 1, NOW)
    expect(first.counts).toEqual({ new: 7, accepted: 2, completed: 3, closed: 2 })
    expect(first.items).toHaveLength(5)
    const second = listJobs(token, 'new', 2, NOW)
    expect(second.items).toHaveLength(2)
    expect(second.page).toBe(2)
  })

  it('withholds the address until the job is accepted', () => {
    const { token } = connectedProvider()
    const [newest] = listJobs(token, 'new', 1, NOW).items
    expect(getJob(token, newest.id, NOW).address).toBeNull()

    const accepted = acceptJob(token, newest.id, 'key-1', NOW)
    expect(accepted.status).toBe('accepted')
    expect(accepted.address?.line1).toContain('Blk')
    expect(accepted.timeline.map((t) => t.step)).toEqual(['dispatched', 'accepted'])
  })

  it('applies a repeated Idempotency-Key once', () => {
    const { token } = connectedProvider()
    const [newest] = listJobs(token, 'new', 1, NOW).items
    acceptJob(token, newest.id, 'key-1', NOW)
    const again = acceptJob(token, newest.id, 'key-1', NOW)
    expect(again.timeline.filter((t) => t.step === 'accepted')).toHaveLength(1)
    expect(apiError(() => acceptJob(token, newest.id, 'key-2', NOW)).status).toBe(409)
  })

  it('answers 409 and cancels a job the family called off before the acceptance landed', () => {
    const { token } = connectedProvider()
    const doomed = listJobs(token, 'new', 1, NOW).items[2]
    const refused = apiError(() => acceptJob(token, doomed.id, 'key-1', NOW))
    expect(refused.status).toBe(409)
    expect(refused.code).toBe('job_cancelled')

    const job = getJob(token, doomed.id, NOW)
    expect(job.status).toBe('cancelled')
    expect(job.cancelReason).toMatch(/family cancelled/)
    expect(listWebhookDeliveries(mine()).some((d) => d.event === 'job.cancelled')).toBe(true)
  })

  it('declines a new job and completes an accepted one, checking the times', () => {
    const { token } = connectedProvider()
    const [a, b] = listJobs(token, 'new', 1, NOW).items

    expect(declineJob(token, a.id, { reason: 'OUTSIDE_AREA', note: null }, 'd-1', NOW).status).toBe('declined')

    acceptJob(token, b.id, 'a-1', NOW)
    const start = '2026-10-15T02:00:00.000Z'
    expect(apiError(() => completeJob(token, b.id, { actualStart: start, actualEnd: start, note: 'x' }, 'c-1', NOW)).status).toBe(422)
    const done = completeJob(token, b.id, { actualStart: start, actualEnd: '2026-10-15T03:00:00.000Z', note: 'All good' }, 'c-2', NOW)
    expect(done.status).toBe('completed')
    expect(done.completion?.note).toBe('All good')

    expect(listJobs(token, 'closed', 1, NOW).counts).toEqual({ new: 2, accepted: 0, completed: 1, closed: 1 })
  })

  it('does not show one provider another provider’s job', () => {
    const { token } = connectedProvider()
    const other = requestToken('cl_prov_BrightHome9', 'cs_demo_brighthome', NOW).accessToken
    const [theirs] = listJobs(other, 'new', 1, NOW).items
    expect(apiError(() => getJob(token, theirs.id, NOW)).status).toBe(404)
  })
})

describe('webhook deliveries', () => {
  it('records one signed delivery per dispatched job, delivered at the first attempt', () => {
    connectedProvider()
    const deliveries = listWebhookDeliveries(mine())
    expect(deliveries).toHaveLength(4)
    const [latest] = deliveries
    expect(latest.event).toBe('job.dispatched')
    expect(latest.status).toBe('delivered')
    expect(latest.attempts).toEqual([expect.objectContaining({ status: 200 })])
    expect(latest.url).toBe(DETAILS.webhookUrl)
    expect(latest.headers).toContain(`X-CareLink-Delivery: ${latest.id}`)
    expect(latest.headers).toMatch(/X-CareLink-Signature: v1=[0-9a-f]{64}/)
    const payload = JSON.parse(latest.payload)
    expect(payload.delivery_id).toBe(latest.id)
    expect(payload.sent_at).toMatch(/^2026-10-\d\dT\d\d:\d\d:\d\d\+08:00$/)
  })

  it('shows the seeded provider’s retried, failed and still-retrying deliveries on the schedule', () => {
    const deliveries = listWebhookDeliveries(seeded())
    const retried = deliveries.find((d) => d.status === 'delivered' && d.attempts.length === 3)!
    expect(retried.attempts.map((a) => a.status)).toEqual([500, null, 200])
    expect(Date.parse(retried.attempts[1].at) - Date.parse(retried.attempts[0].at)).toBe(60_000)
    expect(Date.parse(retried.attempts[2].at) - Date.parse(retried.attempts[1].at)).toBe(5 * 60_000)

    const failed = deliveries.find((d) => d.status === 'failed')!
    expect(failed.attempts).toHaveLength(6)
    expect(failed.nextRetryAt).toBeNull()

    const retrying = deliveries.find((d) => d.status === 'retrying')!
    expect(retrying.attempts).toHaveLength(3)
    expect(Date.parse(retrying.nextRetryAt!) - Date.parse(retrying.attempts[2].at)).toBe(30 * 60_000)

    expect(getIntegrationHealth(seeded(), NOW)).toEqual(
      expect.objectContaining({ retrying: 1, failed: 1, suspended: false }),
    )
  })

  it('resends with the same delivery ID, and sends a test ping', () => {
    const failed = listWebhookDeliveries(seeded()).find((d) => d.status === 'failed')!
    const resent = resendDelivery(seeded(), failed.id, NOW)
    expect(resent.id).toBe(failed.id)
    expect(resent.status).toBe('delivered')
    expect(resent.attempts).toHaveLength(7)

    const ping = sendTestEvent(seeded(), NOW)
    expect(ping.event).toBe('ping')
    expect(JSON.parse(ping.payload)).not.toHaveProperty('job')
    expect(listWebhookDeliveries(seeded())[0].id).toBe(ping.id)
  })

  it('changes the endpoint and its events, refusing anything but HTTPS', () => {
    const changed = updateWebhookSettings(seeded(), { url: 'https://hooks.brighthome.sg/v2', events: ['job.dispatched'] })
    expect(changed).toEqual(expect.objectContaining({ url: 'https://hooks.brighthome.sg/v2', events: ['job.dispatched'] }))
    expect(getWebhookSettings(seeded()).url).toBe('https://hooks.brighthome.sg/v2')
    expect(apiError(() => updateWebhookSettings(seeded(), { url: 'http://brighthome.sg', events: [] })).status).toBe(422)
  })
})

describe('the developer portal', () => {
  it('rotates the client secret: the new one works, the old one stops', () => {
    expect(getCredentialsSummary(seeded())).toEqual(
      expect.objectContaining({ clientId: 'cl_prov_BrightHome9', secretHint: 'home', scope: 'jobs:read jobs:write' }),
    )
    const { clientSecret } = rotateClientSecret(seeded(), NOW)
    expect(requestToken('cl_prov_BrightHome9', clientSecret, NOW).accessToken).toMatch(/^at_/)
    expect(apiError(() => requestToken('cl_prov_BrightHome9', 'cs_demo_brighthome', NOW)).status).toBe(401)
    expect(getCredentialsSummary(seeded()).secretHint).toBe(clientSecret.slice(-4))
  })

  it('rotates the signing secret', () => {
    const { webhookSecret } = rotateWebhookSecret(seeded(), NOW)
    expect(webhookSecret).toMatch(/^whsec_/)
    expect(getWebhookSettings(seeded()).secretHint).toBe(webhookSecret.slice(-4))
  })

  it('is closed to an application that is not approved', () => {
    submitApplication(DETAILS, PASSWORD, NOW)
    expect(apiError(() => getCredentialsSummary(mine())).status).toBe(403)
  })

  it('suspends a provider: tokens refused and existing ones stop working, until reinstated', () => {
    const seq = listApprovedProviders().find((p) => p.id === SEEDED_ID)!.seq
    const token = requestToken('cl_prov_BrightHome9', 'cs_demo_brighthome', NOW).accessToken
    suspendProvider(seq)
    expect(apiError(() => listJobs(token, 'new', 1, NOW)).status).toBe(403)
    expect(apiError(() => requestToken('cl_prov_BrightHome9', 'cs_demo_brighthome', NOW)).code).toBe('provider_suspended')
    expect(getMyApplication(seeded()).suspended).toBe(true)
    expect(listApprovedProviders().find((p) => p.id === SEEDED_ID)!.health.suspended).toBe(true)

    reinstateProvider(seq)
    expect(listJobs(token, 'new', 1, NOW).total).toBe(7)
  })

  it('reports each provider’s jobs by state to the manager', () => {
    const provider = listApprovedProviders(NOW).find((p) => p.id === SEEDED_ID)!
    expect(provider.jobs).toEqual({ new: 7, accepted: 2, completed: 3, closed: 2 })
  })
})
