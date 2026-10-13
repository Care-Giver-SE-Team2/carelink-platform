/**
 * The CareLink Open Platform as a provider sees it: what the public provider API takes and
 * returns. Field names are camelCase here; the wire format is settled by the Open API contract.
 */

/** Value-added services a provider can offer, as report's catalogue names them. */
export const PROVIDER_SERVICES = ['Hospital escort', 'Grocery assistance', 'Companionship', 'Light housekeeping'] as const

/** Shortest portal password the platform accepts. */
export const MIN_PASSWORD_LENGTH = 8

export type ApplicationStatus = 'pending' | 'approved' | 'rejected'

/** What a provider fills in to apply. */
export type ApplicationDetails = {
  organisationName: string
  contactName: string
  contactEmail: string
  contactPhone: string
  services: string[]
  webhookUrl: string
}

export type Application = ApplicationDetails & {
  /** "APP-2026-00417": quoted with the contact email to look the application up. */
  id: string
  status: ApplicationStatus
  submittedAt: string
  reviewedAt: string | null
  /** Why CareLink rejected it; null otherwise. */
  reason: string | null
  /** An approved provider CareLink has stopped: no tokens, no jobs, no webhooks until reinstated. */
  suspended: boolean
}

/** Handed over once, when an approved provider first collects them. */
export type IssuedCredentials = {
  clientId: string
  clientSecret: string
  webhookSecret: string
}

export type AccessToken = {
  accessToken: string
  scope: string
  issuedAt: number
  expiresAt: number
}

export type JobStatus = 'new' | 'accepted' | 'completed' | 'declined' | 'cancelled'

/** The list tabs; `closed` is declined and cancelled together. */
export type JobTab = 'new' | 'accepted' | 'completed' | 'closed'

export type TimelineStep = {
  step: 'dispatched' | 'accepted' | 'completed' | 'declined' | 'cancelled'
  at: string
}

export type JobSummary = {
  id: string
  service: string
  district: string
  areaName: string
  scheduledStart: string
  durationMin: number
  status: JobStatus
  receivedAt: string
}

export type Job = JobSummary & {
  elder: string
  language: string
  bookedBy: string
  instructions: string
  /** Withheld until the provider accepts the job. */
  address: { line1: string; line2: string } | null
  timeline: TimelineStep[]
  completion: { actualStart: string; actualEnd: string; note: string } | null
  /** Why a cancelled job was cancelled, in CareLink's words. */
  cancelReason: string | null
}

export type JobPage = {
  items: JobSummary[]
  page: number
  pageSize: number
  total: number
  counts: Record<JobTab, number>
}

export type DeclineReason = 'OUTSIDE_HOURS' | 'NO_PRACTITIONER' | 'OUTSIDE_AREA' | 'OTHER'

/** Events a provider can subscribe to; `ping` is the test event, always sent when asked for. */
export const WEBHOOK_EVENTS = ['job.dispatched', 'job.cancelled'] as const
export type WebhookEvent = (typeof WEBHOOK_EVENTS)[number] | 'ping'

/**
 * Delivered: some attempt got a 2xx. Retrying: none has yet, and another is scheduled.
 * Failed: every attempt the retry schedule allows went unanswered or non-2xx; CareLink gave up.
 */
export type DeliveryStatus = 'delivered' | 'retrying' | 'failed'

export type DeliveryAttempt = {
  at: string
  /** The HTTP status the endpoint answered; null when it did not answer in time. */
  status: number | null
  ms: number
}

/** One webhook as CareLink sent it: every attempt, and the request it repeats each time. */
export type Delivery = {
  id: string
  event: WebhookEvent
  createdAt: string
  status: DeliveryStatus
  attempts: DeliveryAttempt[]
  nextRetryAt: string | null
  url: string
  headers: string
  payload: string
}

export type WebhookSettings = {
  url: string
  events: WebhookEvent[]
  /** The signing secret's last four characters; the secret itself is shown once. */
  secretHint: string
  secretIssuedAt: string
}

export type CredentialsSummary = {
  clientId: string
  /** The client secret's last four characters. */
  secretHint: string
  secretIssuedAt: string
  scope: string
  tokenEndpoint: string
}

/** How a provider's integration is doing, over the last seven days. */
export type IntegrationHealth = {
  delivered: number
  retrying: number
  failed: number
  lastDeliveredAt: string | null
  suspended: boolean
}
