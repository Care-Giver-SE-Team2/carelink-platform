import { ApiError } from './errors'
import type {
  AccessToken,
  Application,
  ApplicationDetails,
  CredentialsSummary,
  Delivery,
  DeliveryStatus,
  IntegrationHealth,
  IssuedCredentials,
  WebhookEvent,
  WebhookSettings,
} from './types'

/**
 * The Open Platform as notification serves it (docs/api/open-platform.yaml), turned into the shapes
 * the Partner Portal's pages already use. Only the public API, `/api/open/v1/**`: signing in and
 * getting a server token, which notification issues, and the portal's calls with the portal user's
 * token. Under `/api` because that is what CloudFront sends to the services; through the Vite proxy
 * locally.
 *
 * The contract's names are mapped here and nowhere else: UPPER enums to the pages' lower case,
 * `nextAttemptAt` to `nextRetryAt`, a delivery's `request` to the text the delivery log shows.
 */

const env = import.meta.env

/** Where the Open API is; the same origin by default, through the Vite proxy locally. */
const API_BASE: string = env.VITE_OPEN_PLATFORM_API_URL ?? ''

/** The token endpoint, for the client-credentials grant. */
const TOKEN_PATH = '/api/open/v1/oauth2/token'

type Problem = { status?: number; code?: string; detail?: string }

function requestIdOf(response: Response): string {
  return response.headers.get('X-Request-Id') ?? `req_${Date.now().toString(36)}`
}

async function send(url: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(url, init)
  } catch {
    throw new ApiError(0, 'network_error', "Couldn't reach CareLink.", `req_offline_${Date.now().toString(36)}`)
  }
}

/** A call to the Open API; a refusal becomes an ApiError with the contract's code, in lower case. */
async function open<T>(path: string, token: string | null, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body !== undefined) headers.set('Content-Type', 'application/json')
  const response = await send(`${API_BASE}${path}`, { ...init, headers })
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as Problem
    throw new ApiError(
      response.status,
      (problem.code ?? 'error').toLowerCase(),
      problem.detail ?? response.statusText,
      requestIdOf(response),
    )
  }
  return (await response.json()) as T
}

// ---------- Sign-in and tokens ----------

/**
 * POST /api/open/v1/portal/sessions {email, password} → the portal user's access token. A wrong
 * password is 401 INVALID_CREDENTIALS; five in a row lock the sign-in for 15 minutes (429).
 */
export async function signIn(email: string, password: string): Promise<{ token: string; expiresAt: number }> {
  const session = await open<{ accessToken: string; expiresIn: number }>('/api/open/v1/portal/sessions', null, {
    method: 'POST',
    body: JSON.stringify({ email: email.trim(), password }),
  })
  return { token: session.accessToken, expiresAt: Date.now() + session.expiresIn * 1000 }
}

/** The claims of a token, read without checking it: CareLink checks it, this only shows the scope. */
function claimsOf(token: string): Record<string, unknown> {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), '='))) as Record<string, unknown>
  } catch {
    return {}
  }
}

/** The client-credentials grant, as a provider's server makes it. */
export async function requestToken(clientId: string, clientSecret: string): Promise<AccessToken> {
  const response = await send(`${API_BASE}${TOKEN_PATH}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${btoa(`${clientId.trim()}:${clientSecret.trim()}`)}`,
    },
    body: new URLSearchParams({ grant_type: 'client_credentials' }).toString(),
  })
  const body = (await response.json().catch(() => ({}))) as { access_token?: string; expires_in?: number; error?: string }
  if (!response.ok || !body.access_token) {
    throw new ApiError(response.status, body.error ?? 'token_failed', 'Client authentication failed.', requestIdOf(response))
  }
  const issuedAt = Date.now()
  const scope = claimsOf(body.access_token).scope
  return {
    accessToken: body.access_token,
    scope: typeof scope === 'string' ? scope : '',
    issuedAt,
    expiresAt: issuedAt + (body.expires_in ?? 3600) * 1000,
  }
}

// ---------- Onboarding and the portal ----------

type WireApplication = Omit<Application, 'status'> & { status: 'PENDING' | 'APPROVED' | 'REJECTED' }

function application(wire: WireApplication): Application {
  return { ...wire, status: wire.status.toLowerCase() as Application['status'] }
}

/** POST /api/open/v1/applications. Submitting the form is agreeing to the terms it shows. */
export function submitApplication(details: ApplicationDetails, password: string): Promise<{ applicationId: string }> {
  return open('/api/open/v1/applications', null, {
    method: 'POST',
    body: JSON.stringify({ ...details, password, agreedToTerms: true }),
  })
}

export async function getMyApplication(token: string): Promise<Application> {
  return application(await open<WireApplication>('/api/open/v1/portal/application', token))
}

export function collectCredentials(token: string): Promise<IssuedCredentials> {
  return open('/api/open/v1/portal/credentials/collect', token, { method: 'POST' })
}

type WireCredentialsSummary = { clientId: string; secretHint: string; secretIssuedAt: string; scopes: string[]; tokenUrl: string }

export async function getCredentialsSummary(token: string): Promise<CredentialsSummary> {
  const wire = await open<WireCredentialsSummary>('/api/open/v1/portal/credentials', token)
  return {
    clientId: wire.clientId,
    secretHint: wire.secretHint,
    secretIssuedAt: wire.secretIssuedAt,
    scope: wire.scopes.join(' '),
    tokenEndpoint: `POST ${absolute(wire.tokenUrl)}`,
  }
}

/** A path on CareLink's public address as a provider's server must call it: with the address. */
function absolute(url: string): string {
  return url.startsWith('/') ? `${API_BASE || window.location.origin}${url}` : url
}

/** A new secret, and perhaps a new client: the client id it gives is the one to use from now on. */
export function rotateClientSecret(token: string): Promise<{ clientId: string; clientSecret: string }> {
  return open('/api/open/v1/portal/credentials/rotate', token, { method: 'POST' })
}

export function getIntegrationHealth(token: string): Promise<IntegrationHealth> {
  return open('/api/open/v1/portal/health', token)
}

// ---------- Webhooks ----------

export function getWebhookSettings(token: string): Promise<WebhookSettings> {
  return open<WebhookSettings>('/api/open/v1/portal/webhook', token)
}

export function updateWebhookSettings(token: string, body: { url: string; events: WebhookEvent[] }): Promise<WebhookSettings> {
  return open('/api/open/v1/portal/webhook', token, {
    method: 'PUT',
    body: JSON.stringify({ url: body.url, events: body.events.filter((event) => event !== 'ping') }),
  })
}

export function rotateWebhookSecret(token: string): Promise<{ webhookSecret: string }> {
  return open('/api/open/v1/portal/webhook/secret/rotate', token, { method: 'POST' })
}

type WireDelivery = {
  id: string
  event: WebhookEvent
  jobId: string | null
  createdAt: string
  status: 'DELIVERED' | 'RETRYING' | 'FAILED'
  attempts: { at: string; responseStatus: number | null; durationMs: number }[]
  nextAttemptAt: string | null
  request: { url: string; headers: Record<string, string>; body: string }
}

/** The request as the delivery log shows it: the request line, the headers, and the body laid out. */
export function delivery(wire: WireDelivery): Delivery {
  const url = new URL(wire.request.url)
  const headers = [
    `POST ${url.pathname || '/'}${url.search} HTTP/1.1`,
    `Host: ${url.host}`,
    ...Object.entries(wire.request.headers).map(([name, value]) => `${name}: ${value}`),
  ].join('\n')
  let payload = wire.request.body
  try {
    payload = JSON.stringify(JSON.parse(wire.request.body), null, 2)
  } catch {
    // Shown as sent
  }
  return {
    id: wire.id,
    event: wire.event,
    createdAt: wire.createdAt,
    status: wire.status.toLowerCase() as DeliveryStatus,
    attempts: wire.attempts.map((attempt) => ({ at: attempt.at, status: attempt.responseStatus, ms: attempt.durationMs })),
    nextRetryAt: wire.nextAttemptAt,
    url: wire.request.url,
    headers,
    payload,
  }
}

/** The last 30 days' deliveries, newest first: the first hundred, which the log shows. */
export async function listWebhookDeliveries(token: string): Promise<Delivery[]> {
  const page = await open<{ items: WireDelivery[] }>('/api/open/v1/portal/webhook/deliveries?size=100', token)
  return page.items.map(delivery)
}

export async function resendDelivery(token: string, deliveryId: string): Promise<Delivery> {
  return delivery(
    await open<WireDelivery>(`/api/open/v1/portal/webhook/deliveries/${encodeURIComponent(deliveryId)}/resend`, token, {
      method: 'POST',
    }),
  )
}

export async function sendTestEvent(token: string): Promise<Delivery> {
  return delivery(await open<WireDelivery>('/api/open/v1/portal/webhook/test', token, { method: 'POST' }))
}
