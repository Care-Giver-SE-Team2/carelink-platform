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
import { api } from '../../../shared/api/client'

/**
 * Provider applications (PT01). A third-party provider applies from the Partner Portal (/provider)
 * through the public Open API; the manager approves or declines here, and the Partner Portal sees the
 * decision. Served by notification's internal endpoints, with the manager's session:
 *
 *   GET  /api/provider-applications              pending applications, newest first
 *   POST /api/provider-applications/{id}/approve → the provider: registered, with its OAuth2 client
 *        for the Open API and its webhook signing secret, which the provider collects once from
 *        the Partner Portal
 *   POST /api/provider-applications/{id}/reject  { reason }  (required; the provider is shown it)
 *   GET  /api/providers                          approved providers, newest first, with the last
 *        seven days' webhook deliveries
 *   POST /api/providers/{id}/suspend             tokens refused, no jobs or webhooks
 *   POST /api/providers/{id}/reinstate
 *
 * The manager never sees the client secret or the webhook secret, only the client id.
 * `services` are names from report's value-added catalogue, so a provider can be dispatched the
 * jobs it offers. Under Vitest, and with VITE_OPEN_PLATFORM_MOCK=true, the mock Open Platform in
 * features/provider-platform/mockPlatform.ts answers instead.
 */
const LIVE = import.meta.env.MODE !== 'test' && import.meta.env.VITE_OPEN_PLATFORM_MOCK !== 'true'

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
  /**
   * Jobs dispatched to it, by state; `closed` is declined and cancelled. Null until report, which
   * keeps the jobs, serves their counts.
   */
  jobs: Record<JobTab, number> | null
}

type WireApplication = {
  id: number
  reference: string
  organisationName: string
  contactName: string
  contactEmail: string
  contactPhone: string
  services: string[]
  webhookUrl: string
  submittedAt: string
}

type WireProvider = WireApplication & {
  clientId: string
  status: 'ACTIVE' | 'SUSPENDED'
  approvedAt: string
  health: IntegrationHealth
}

function fromWire(app: WireApplication): ProviderApplication {
  return { ...app, contactPhone: app.contactPhone || null }
}

/** An approved provider as notification lists it, with the application it was approved from. */
function providerFromWire(p: WireProvider): Provider {
  return {
    ...fromWire(p),
    providerId: p.id,
    clientId: p.clientId,
    approvedAt: p.approvedAt,
    suspended: p.status === 'SUSPENDED',
    health: p.health,
    jobs: null,
  }
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
  if (LIVE) return (await api<WireApplication[]>('/provider-applications')).map(fromWire)
  return listApplicationsForReview().map(toApplication)
}

/** Approved providers, most recently approved first. */
export async function fetchProviders(): Promise<Provider[]> {
  if (LIVE) return (await api<WireProvider[]>('/providers')).map(providerFromWire)
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
  if (LIVE) {
    await api(`/providers/${id}/suspend`, { method: 'POST' })
    return
  }
  suspend(id)
}

/** Lifts a suspension. */
export async function reinstateProvider(id: number): Promise<void> {
  if (LIVE) {
    await api(`/providers/${id}/reinstate`, { method: 'POST' })
    return
  }
  reinstate(id)
}

/** Approves the application: the provider is registered and its Open API client created. */
export async function approveProviderApplication(id: number, message: string | null): Promise<{ providerId: number }> {
  if (LIVE) {
    const provider = await api<WireProvider>(`/provider-applications/${id}/approve`, { method: 'POST' })
    return { providerId: provider.id }
  }
  return approveApplication(id, message)
}

/** Declines the application; `message` tells the provider why when they check their status. */
export async function declineProviderApplication(id: number, message: string): Promise<void> {
  if (LIVE) {
    await api(`/provider-applications/${id}/reject`, { method: 'POST', body: JSON.stringify({ reason: message }) })
    return
  }
  rejectApplication(id, message)
}
