import {
  approveApplication,
  listApplicationsForReview,
  listApprovedProviders,
  rejectApplication,
  reinstateProvider as reinstate,
  suspendProvider as suspend,
} from '../../../features/provider-platform/mockPlatform'
import type { ReviewApplication } from '../../../features/provider-platform/mockPlatform'
import type { IntegrationHealth, JobTab } from '../../../features/provider-platform/types'

/**
 * Provider applications (PT01), served by the mock Open Platform in
 * features/provider-platform/mockPlatform.ts until the onboarding endpoints exist. A third-party
 * provider applies from the Partner Portal (/provider) through the public Open API; the
 * manager approves or declines here, and the Partner Portal sees the decision. The internal
 * endpoints this stands for (path and owning service still to be settled with the Open API
 * contract):
 *
 *   GET  /api/provider-applications              pending applications, newest first
 *   POST /api/provider-applications/{id}/approve { message }  → { providerId }
 *        registers the provider, creates its OAuth2 client for the Open API and its webhook
 *        signing secret; the provider collects both once from the Partner Portal
 *   POST /api/provider-applications/{id}/decline { message }  (message required)
 *   GET  /api/providers                          approved providers, newest first, with the
 *        last seven days' webhook deliveries and their jobs by state
 *   POST /api/providers/{id}/suspend             tokens refused, no jobs or webhooks
 *   POST /api/providers/{id}/reinstate
 *
 * The manager never sees the client secret or the webhook secret, only the client id.
 * `services` are names from report's value-added catalogue, so a provider can be dispatched the
 * jobs it offers.
 */

export type ProviderApplication = {
  id: number
  /** "APP-2026-00412": what the provider quotes. */
  reference: string
  organisationName: string
  contactName: string
  contactEmail: string
  contactPhone: string | null
  services: string[]
  /** Where job webhooks are sent once approved. */
  webhookUrl: string
  submittedAt: string
}

export type Provider = ProviderApplication & {
  providerId: number
  /** The OAuth2 client id for the Open API. Not secret; the secret is shown only to the provider. */
  clientId: string
  approvedAt: string
  suspended: boolean
  health: IntegrationHealth
  /** Jobs dispatched to it, by state; `closed` is declined and cancelled. */
  jobs: Record<JobTab, number>
}

function toApplication(app: ReviewApplication): ProviderApplication {
  return {
    id: app.seq,
    reference: app.id,
    organisationName: app.organisationName,
    contactName: app.contactName,
    contactEmail: app.contactEmail,
    contactPhone: app.contactPhone || null,
    services: app.services,
    webhookUrl: app.webhookUrl,
    submittedAt: app.submittedAt,
  }
}

/** Applications waiting for an answer, newest first. */
export async function fetchProviderApplications(): Promise<ProviderApplication[]> {
  return listApplicationsForReview().map(toApplication)
}

/** Approved providers, most recently approved first. */
export async function fetchProviders(): Promise<Provider[]> {
  return listApprovedProviders().map((p) => ({
    ...toApplication(p),
    providerId: p.providerId,
    clientId: p.clientId,
    approvedAt: p.approvedAt,
    suspended: p.suspended,
    health: p.health,
    jobs: p.jobs,
  }))
}

/** Suspends the provider: its tokens stop working, so it can't see or act on jobs. */
export async function suspendProvider(id: number): Promise<void> {
  suspend(id)
}

/** Lifts a suspension. */
export async function reinstateProvider(id: number): Promise<void> {
  reinstate(id)
}

/** Approves the application: the provider is registered and its Open API client created. */
export async function approveProviderApplication(id: number, message: string | null): Promise<{ providerId: number }> {
  return approveApplication(id, message)
}

/** Declines the application; `message` tells the provider why when they check their status. */
export async function declineProviderApplication(id: number, message: string): Promise<void> {
  rejectApplication(id, message)
}
