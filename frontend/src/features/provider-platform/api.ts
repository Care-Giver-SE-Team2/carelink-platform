import { ApiError } from './errors'
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
 * Until the Open API exists every call is served by mockPlatform.ts after a short delay, and
 * fails like a dropped request while the browser is offline. Each function names the endpoint
 * it stands for; swapping in fetch calls should not change these signatures.
 */

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

/** POST /v1/applications {…, password} → { application_id }, and the portal account. */
export function submitApplication(details: ApplicationDetails, password: string): Promise<{ applicationId: string }> {
  return call(() => platform.submitApplication(details, password))
}

/** POST /v1/portal/sessions {email, password} */
export async function signIn(email: string, password: string): Promise<PortalSession> {
  const { session, expiresAt } = await call(() => platform.signIn(email, password))
  return { token: session, expiresAt }
}

/** DELETE /v1/portal/sessions/current */
export function signOut(session: PortalSession): Promise<void> {
  return call(() => platform.signOut(session.token))
}

/** GET /v1/portal/application */
export function getMyApplication(session: PortalSession): Promise<Application> {
  return call(() => platform.getMyApplication(session.token))
}

/** POST /v1/portal/credentials/collect — once, after approval. */
export function collectCredentials(session: PortalSession): Promise<IssuedCredentials> {
  return call(() => platform.collectCredentials(session.token))
}

/** POST /oauth2/token (grant_type=client_credentials) */
export function requestToken(clientId: string, clientSecret: string): Promise<AccessToken> {
  return call(() => platform.requestToken(clientId, clientSecret))
}

/** GET /v1/jobs?status=new|accepted|completed|closed&page= */
export function listJobs(token: string, tab: JobTab, page: number): Promise<JobPage> {
  return call(() => platform.listJobs(token, tab, page))
}

/** GET /v1/jobs/{id} */
export function getJob(token: string, jobId: string): Promise<Job> {
  return call(() => platform.getJob(token, jobId))
}

/** POST /v1/jobs/{id}/accept */
export function acceptJob(token: string, jobId: string, idempotencyKey: string): Promise<Job> {
  return call(() => platform.acceptJob(token, jobId, idempotencyKey))
}

/** POST /v1/jobs/{id}/decline {reason, note} */
export function declineJob(
  token: string,
  jobId: string,
  body: { reason: DeclineReason; note: string | null },
  idempotencyKey: string,
): Promise<Job> {
  return call(() => platform.declineJob(token, jobId, body, idempotencyKey))
}

/** POST /v1/jobs/{id}/complete {actual_start, actual_end, note} */
export function completeJob(
  token: string,
  jobId: string,
  body: { actualStart: string; actualEnd: string; note: string },
  idempotencyKey: string,
): Promise<Job> {
  return call(() => platform.completeJob(token, jobId, body, idempotencyKey))
}

/** GET /v1/portal/credentials */
export function getCredentialsSummary(session: PortalSession): Promise<CredentialsSummary> {
  return call(() => platform.getCredentialsSummary(session.token))
}

/** POST /v1/portal/credentials/rotate → the new client secret, shown once. */
export function rotateClientSecret(session: PortalSession): Promise<{ clientSecret: string }> {
  return call(() => platform.rotateClientSecret(session.token))
}

/** GET /v1/portal/webhook */
export function getWebhookSettings(session: PortalSession): Promise<WebhookSettings> {
  return call(() => platform.getWebhookSettings(session.token))
}

/** PUT /v1/portal/webhook {url, events} */
export function updateWebhookSettings(session: PortalSession, body: { url: string; events: WebhookEvent[] }): Promise<WebhookSettings> {
  return call(() => platform.updateWebhookSettings(session.token, body))
}

/** POST /v1/portal/webhook/rotate-secret → the new signing secret, shown once. */
export function rotateWebhookSecret(session: PortalSession): Promise<{ webhookSecret: string }> {
  return call(() => platform.rotateWebhookSecret(session.token))
}

/** GET /v1/portal/webhook/deliveries */
export function listWebhookDeliveries(session: PortalSession): Promise<Delivery[]> {
  return call(() => platform.listWebhookDeliveries(session.token))
}

/** POST /v1/portal/webhook/deliveries/{id}/resend */
export function resendDelivery(session: PortalSession, deliveryId: string): Promise<Delivery> {
  return call(() => platform.resendDelivery(session.token, deliveryId))
}

/** POST /v1/portal/webhook/test */
export function sendTestEvent(session: PortalSession): Promise<Delivery> {
  return call(() => platform.sendTestEvent(session.token))
}

/** GET /v1/portal/health */
export function getIntegrationHealth(session: PortalSession): Promise<IntegrationHealth> {
  return call(() => platform.getIntegrationHealth(session.token))
}

/** A fresh key for one attempt at a state-changing call. */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
}
