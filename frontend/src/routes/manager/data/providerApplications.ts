/**
 * Provider applications (PT01): an in-memory store standing in for the provider onboarding
 * endpoints, which don't exist yet. A third-party provider applies from the provider workbench
 * through the public Open API; the manager approves or declines here. The internal endpoints
 * this mimics (path and owning service still to be settled with the Open API contract):
 *
 *   GET  /api/provider-applications              pending applications, newest first
 *   POST /api/provider-applications/{id}/approve { message }  → { providerId }
 *        registers the provider, creates its OAuth2 client for the Open API and its webhook
 *        signing secret; the provider collects both once from the workbench
 *   POST /api/provider-applications/{id}/decline { message }  (message required)
 *   GET  /api/providers                          approved providers, newest first
 *
 * The manager never sees the client secret or the webhook secret, only the client id.
 * `services` are names from report's value-added catalogue, so a provider can be dispatched the
 * jobs it offers; `serviceArea` uses the same sector codes as caregivers.
 */

export type ProviderApplication = {
  id: number
  organisationName: string
  /** Singapore business registration number. */
  uen: string
  contactName: string
  contactEmail: string
  contactPhone: string | null
  services: string[]
  serviceArea: string[]
  /** Where job webhooks are sent once approved. */
  webhookUrl: string
  submittedAt: string
}

export type Provider = ProviderApplication & {
  providerId: number
  /** The OAuth2 client id for the Open API. Not secret; the secret is shown only to the provider. */
  clientId: string
  approvedAt: string
}

/** Mock provider ids start here so they never collide with application ids. */
const MOCK_PROVIDER_ID_BASE = 100

const HOUR_MS = 60 * 60 * 1000

function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * HOUR_MS).toISOString()
}

let pending: ProviderApplication[] = [
  {
    id: 14,
    organisationName: 'Kampung Care Escorts Pte Ltd',
    uen: '202312345K',
    contactName: 'Siti Aminah',
    contactEmail: 'ops@kampungcare.sg',
    contactPhone: '+65 6123 4501',
    services: ['Hospital escort', 'Companionship'],
    serviceArea: ['AMK', 'BSH'],
    webhookUrl: 'https://api.kampungcare.sg/carelink/webhooks',
    submittedAt: hoursAgo(2),
  },
  {
    id: 13,
    organisationName: 'FreshCart Errands',
    uen: '53412987C',
    contactName: 'Daniel Goh',
    contactEmail: 'daniel@freshcart.com.sg',
    contactPhone: null,
    services: ['Grocery assistance'],
    serviceArea: ['TPY'],
    webhookUrl: 'https://hooks.freshcart.com.sg/carelink',
    submittedAt: hoursAgo(30),
  },
]

let approved: Provider[] = [
  {
    id: 9,
    providerId: MOCK_PROVIDER_ID_BASE + 9,
    organisationName: 'BrightHome Services',
    uen: '201998765M',
    contactName: 'Ravi Kumar',
    contactEmail: 'partners@brighthome.sg',
    contactPhone: '+65 6789 2210',
    services: ['Light housekeeping'],
    serviceArea: ['AMK', 'TPY'],
    webhookUrl: 'https://brighthome.sg/api/carelink-jobs',
    submittedAt: hoursAgo(24 * 9),
    clientId: mockClientId(9),
    approvedAt: hoursAgo(24 * 8),
  },
]

/** A stand-in for the client id Cognito would issue. */
function mockClientId(applicationId: number): string {
  return `mock-client-${String(applicationId).padStart(4, '0')}`
}

function take(id: number): ProviderApplication {
  const application = pending.find((a) => a.id === id)
  if (!application) throw new Error('This application has already been answered.')
  pending = pending.filter((a) => a.id !== id)
  return application
}

/** Applications waiting for an answer, newest first. */
export async function fetchProviderApplications(): Promise<ProviderApplication[]> {
  return [...pending].sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))
}

/** Approved providers, most recently approved first. */
export async function fetchProviders(): Promise<Provider[]> {
  return [...approved].sort((a, b) => b.approvedAt.localeCompare(a.approvedAt))
}

/** Approves the application: the provider is registered and its Open API client created. */
export async function approveProviderApplication(id: number, _message: string | null): Promise<{ providerId: number }> {
  const application = take(id)
  const providerId = MOCK_PROVIDER_ID_BASE + application.id
  approved = [
    ...approved,
    { ...application, providerId, clientId: mockClientId(application.id), approvedAt: new Date().toISOString() },
  ]
  return { providerId }
}

/** Declines the application; `message` tells the provider why when they check their status. */
export async function declineProviderApplication(id: number, _message: string): Promise<void> {
  take(id)
}
