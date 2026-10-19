import { ApiError } from './errors'
import * as live from './liveApi'
import * as platform from './mockPlatform'
import type {
  AccessToken,
  Application,
  ApplicationDetails,
  CredentialsSummary,
  DeclineReason,
  Delivery,
  IntegrationHealth,
  IssuedCredentials,
  Job,
  JobPage,
  JobTab,
  WebhookEvent,
  WebhookSettings,
} from './types'

/**
 * The public provider API, as the Partner Portal calls it. It is the only way the Partner Portal
 * reaches CareLink: no session cookie, no internal endpoint, nothing from shared/api.
 *
 * Two kinds of call. Developer-portal calls (the application, credentials, webhook settings
 * and deliveries) need the provider signed in to the portal with the email and password it
 * chose when applying.
 * Job calls are what a provider's own server makes: they take the OAuth2 access token got with
 * the client credentials, and state-changing ones take an Idempotency-Key, which a retry must
 * re-send unchanged. The Partner Portal makes job calls only from its Sandbox.
 *
 * The portal's calls go to the Open API that notification serves (liveApi.ts). The job API is not
 * connected yet: live job calls fail explicitly instead of handing real tokens to the mock.
 * Under Vitest, or in local development with VITE_OPEN_PLATFORM_MOCK=true, every call uses the
 * mock after a short delay, failing like a dropped request while the browser is offline.
 * Production builds exclude the mock and reject that environment flag.
 */

/** Whether the portal talks to CareLink; the tests and a backend-less demo use the mock. */
export const LIVE = import.meta.env.PROD || (import.meta.env.MODE !== 'test' && import.meta.env.VITE_OPEN_PLATFORM_MOCK !== 'true')

let latencyMs = 350

/** Tests set this to 0. */
export function setApiLatency(ms: number) {
  latencyMs = ms
}

async function call<T>(serve: () => T): Promise<T> {
  if (latencyMs > 0) await new Promise((resolve) => setTimeout(resolve, latencyMs))
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    throw new ApiError(0, 'network_error', "Couldn't reach CareLink.", `req_offline_${Date.now().toString(36)}`)
  }
  return serve()
}

/** A signed-in portal session. */
export type PortalSession = { token: string; expiresAt: number }

/** POST /api/open/v1/applications {…, password, agreedToTerms} → { applicationId }, and the portal user. */
export function submitApplication(details: ApplicationDetails, password: string): Promise<{ applicationId: string }> {
  if (LIVE) return live.submitApplication(details, password)
  return call(() => platform.submitApplication(details, password))
}

/** POST /api/open/v1/portal/sessions {email, password}: the portal user's access token. */
export async function signIn(email: string, password: string): Promise<PortalSession> {
  if (LIVE) return live.signIn(email, password)
  const { session, expiresAt } = await call(() => platform.signIn(email, password))
  return { token: session, expiresAt }
}

/** Forgets the session. The token runs out by itself, within the hour. */
export function signOut(session: PortalSession): Promise<void> {
  if (LIVE) return Promise.resolve()
  return call(() => platform.signOut(session.token))
}

/** GET /api/open/v1/portal/application */
export function getMyApplication(session: PortalSession): Promise<Application> {
  if (LIVE) return live.getMyApplication(session.token)
  return call(() => platform.getMyApplication(session.token))
}

/** POST /api/open/v1/portal/credentials/collect — once, after approval. */
export function collectCredentials(session: PortalSession): Promise<IssuedCredentials> {
  if (LIVE) return live.collectCredentials(session.token)
  return call(() => platform.collectCredentials(session.token))
}

/** POST /oauth2/token (grant_type=client_credentials) */
export async function requestToken(clientId: string, clientSecret: string): Promise<AccessToken> {
  if (LIVE) return live.requestToken(clientId, clientSecret)
  return call(() => platform.requestToken(clientId, clientSecret))
}

function jobsUnavailable(): Promise<never> {
  return Promise.reject(new ApiError(501, 'jobs_not_available', 'The provider job API is not connected in this deployment.', 'local_jobs_unavailable'))
}

/** GET /v1/jobs?status=new|accepted|completed|closed&page= */
export function listJobs(token: string, tab: JobTab, page: number): Promise<JobPage> {
  if (LIVE) return jobsUnavailable()
  return call(() => platform.listJobs(token, tab, page))
}

/** GET /v1/jobs/{id} */
export function getJob(token: string, jobId: string): Promise<Job> {
  if (LIVE) return jobsUnavailable()
  return call(() => platform.getJob(token, jobId))
}

/** POST /v1/jobs/{id}/accept */
export function acceptJob(token: string, jobId: string, idempotencyKey: string): Promise<Job> {
  if (LIVE) return jobsUnavailable()
  return call(() => platform.acceptJob(token, jobId, idempotencyKey))
}

/** POST /v1/jobs/{id}/decline {reason, note} */
export function declineJob(
  token: string,
  jobId: string,
  body: { reason: DeclineReason; note: string | null },
  idempotencyKey: string,
): Promise<Job> {
  if (LIVE) return jobsUnavailable()
  return call(() => platform.declineJob(token, jobId, body, idempotencyKey))
}

/** POST /v1/jobs/{id}/complete {actual_start, actual_end, note} */
export function completeJob(
  token: string,
  jobId: string,
  body: { actualStart: string; actualEnd: string; note: string },
  idempotencyKey: string,
): Promise<Job> {
  if (LIVE) return jobsUnavailable()
  return call(() => platform.completeJob(token, jobId, body, idempotencyKey))
}

/** GET /api/open/v1/portal/credentials */
export function getCredentialsSummary(session: PortalSession): Promise<CredentialsSummary> {
  if (LIVE) return live.getCredentialsSummary(session.token)
  return call(() => platform.getCredentialsSummary(session.token))
}

/**
 * POST /api/open/v1/portal/credentials/rotate → the new client secret, shown once, with the client id to
 * use from now on: rotating may issue a new client.
 */
export function rotateClientSecret(session: PortalSession): Promise<{ clientId?: string; clientSecret: string }> {
  if (LIVE) return live.rotateClientSecret(session.token)
  return call(() => platform.rotateClientSecret(session.token))
}

/** GET /api/open/v1/portal/webhook */
export function getWebhookSettings(session: PortalSession): Promise<WebhookSettings> {
  if (LIVE) return live.getWebhookSettings(session.token)
  return call(() => platform.getWebhookSettings(session.token))
}

/** PUT /api/open/v1/portal/webhook {url, events} */
export function updateWebhookSettings(session: PortalSession, body: { url: string; events: WebhookEvent[] }): Promise<WebhookSettings> {
  if (LIVE) return live.updateWebhookSettings(session.token, body)
  return call(() => platform.updateWebhookSettings(session.token, body))
}

/** POST /api/open/v1/portal/webhook/secret/rotate → the new signing secret, shown once. */
export function rotateWebhookSecret(session: PortalSession): Promise<{ webhookSecret: string }> {
  if (LIVE) return live.rotateWebhookSecret(session.token)
  return call(() => platform.rotateWebhookSecret(session.token))
}

/** GET /api/open/v1/portal/webhook/deliveries */
export function listWebhookDeliveries(session: PortalSession): Promise<Delivery[]> {
  if (LIVE) return live.listWebhookDeliveries(session.token)
  return call(() => platform.listWebhookDeliveries(session.token))
}

/** POST /api/open/v1/portal/webhook/deliveries/{id}/resend */
export function resendDelivery(session: PortalSession, deliveryId: string): Promise<Delivery> {
  if (LIVE) return live.resendDelivery(session.token, deliveryId)
  return call(() => platform.resendDelivery(session.token, deliveryId))
}

/** POST /api/open/v1/portal/webhook/test */
export function sendTestEvent(session: PortalSession): Promise<Delivery> {
  if (LIVE) return live.sendTestEvent(session.token)
  return call(() => platform.sendTestEvent(session.token))
}

/** GET /api/open/v1/portal/health */
export function getIntegrationHealth(session: PortalSession): Promise<IntegrationHealth> {
  if (LIVE) return live.getIntegrationHealth(session.token)
  return call(() => platform.getIntegrationHealth(session.token))
}

/** A fresh key for one attempt at a state-changing call. */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
}
