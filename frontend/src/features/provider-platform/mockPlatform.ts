import { ApiError } from './errors'
import { districtName } from './format'
import { MIN_PASSWORD_LENGTH, PROVIDER_SERVICES, WEBHOOK_EVENTS } from './types'
import type {
  AccessToken,
  Application,
  ApplicationDetails,
  CredentialsSummary,
  DeclineReason,
  Delivery,
  DeliveryAttempt,
  IntegrationHealth,
  IssuedCredentials,
  Job,
  JobPage,
  JobStatus,
  JobSummary,
  JobTab,
  TimelineStep,
  WebhookEvent,
  WebhookSettings,
} from './types'
import { ATTEMPT_TIMEOUT_SECONDS, MAX_ATTEMPTS, attemptOffsetMinutes } from './webhooks'

/**
 * A stand-in for the CareLink Open Platform until its backend exists: provider applications,
 * the credentials and tokens of approved providers, the jobs dispatched to them and the
 * webhooks those jobs sent. Both sides use it — the Partner Portal through api.ts (the
 * public provider API) and the manager console's Providers screen through the review functions
 * at the bottom (the internal API) — so an application approved in one is approved in the other.
 *
 * State lives in localStorage, so the Partner Portal in one tab and the manager console in another
 * see the same platform; without storage it lives in memory for the page's life. Secrets are
 * kept in plain text here only because this is a mock: the real platform keeps a hash.
 *
 * Webhooks are not really sent: each delivery records the attempts CareLink would have made and
 * their outcome, as the delivery table of Phase 2 will.
 *
 * Every function throws ApiError the way the real endpoint would answer.
 */

const STORAGE_KEY = 'carelink.mock.providerPlatform.v1'
const TOKEN_TTL_MS = 60 * 60 * 1000
const SCOPE = 'jobs:read jobs:write'
const TOKEN_ENDPOINT = 'POST /oauth2/token'
const MINUTE_MS = 60 * 1000
const HOUR_MS = 60 * 60 * 1000
const DAY_MS = 24 * HOUR_MS

/** Jobs per page. Small so the demo data spans two pages; the real API uses 20. */
export const JOBS_PAGE_SIZE = 5

/** How long each service runs, as report's catalogue sets it. */
const DURATION_MIN: Record<string, number> = {
  'Hospital escort': 180,
  'Grocery assistance': 90,
  Companionship: 120,
  'Light housekeeping': 120,
}

const INSTRUCTIONS: Record<string, string> = {
  'Hospital escort':
    'Appointment at Changi General Hospital, Clinic 3B, 45 minutes after the start time. Bring the referral letter from the side table. A wheelchair is waiting at the lift lobby.',
  'Grocery assistance':
    'Weekly shop at the FairPrice on the ground floor. The list is on the fridge and she pays by NETS. Low-sodium items only.',
  Companionship:
    'Enjoys talking about the old kampong days and a game of Chinese chess. Encourage a short walk to the void deck if the weather is fine.',
  'Light housekeeping':
    'Sweep and mop the living room and kitchen, and change the bed sheets. Mind the loose floor tile by the bathroom.',
}

/** Where the elders the mock's jobs are for live. */
const JOB_DISTRICTS = ['16', '19', '14', '18', '20', '12']

const ELDERS = [
  { elder: 'Mr L., 81 · uses a walking frame', language: 'Hokkien, English' },
  { elder: 'Mdm T., 84 · uses a wheelchair', language: 'Teochew, English' },
  { elder: 'Mr R., 77 · hard of hearing', language: 'Tamil, English' },
  { elder: 'Mdm S., 79 · mild dementia', language: 'Malay, English' },
]

type StoredApplication = Application & {
  seq: number
  /** Set on approval. `collected` once the provider has been shown them. */
  credentials: (IssuedCredentials & { collected: boolean; secretIssuedAt: string; webhookSecretIssuedAt: string }) | null
  /** The webhook events the provider subscribes to. */
  events: WebhookEvent[]
  providerId: number | null
  /** The manager's message with the decision. */
  message: string | null
}

type StoredJob = Job & {
  clientId: string
  /** The family cancels before the provider's acceptance lands: accepting answers 409. */
  cancelOnAccept: string | null
  decline: { reason: DeclineReason; note: string | null } | null
}

type StoredDelivery = Delivery & { clientId: string }

type PlatformState = {
  nextSeq: number
  nextJob: number
  nextDelivery: number
  applications: StoredApplication[]
  jobs: StoredJob[]
  deliveries: StoredDelivery[]
  tokens: { token: string; clientId: string; expiresAt: number }[]
  /** Portal accounts, one per contact email; the password is kept only as a salted hash. */
  accounts: { email: string; salt: string; passwordHash: string }[]
  /** Signed-in portal sessions. */
  sessions: { token: string; email: string; expiresAt: number }[]
  /** Idempotency-Key → the job it acted on, so a retried request isn't applied twice. */
  idempotency: Record<string, { jobId: string; op: string }>
}

let memory: PlatformState | null = null

function storage(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

function load(): PlatformState {
  const store = storage()
  if (store) {
    try {
      const raw = store.getItem(STORAGE_KEY)
      if (raw) return JSON.parse(raw) as PlatformState
    } catch {
      // Unreadable or blocked: fall through to memory, then a fresh seed.
    }
  }
  if (!memory) memory = seed(Date.now())
  return memory
}

function save(state: PlatformState) {
  memory = state
  try {
    storage()?.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
    // Storage full or blocked: memory still holds it.
  }
}

/** Starts the mock over from its seed data. Tests call this before each case. */
export function resetMockPlatform(now = Date.now()) {
  memory = null
  try {
    storage()?.removeItem(STORAGE_KEY)
  } catch {
    // Nothing to clear.
  }
  save(seed(now))
}

const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'

function random(length: number): string {
  const bytes = new Uint8Array(length)
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) crypto.getRandomValues(bytes)
  else for (let i = 0; i < length; i++) bytes[i] = Math.floor(Math.random() * 256)
  return Array.from(bytes, (b) => ALPHABET[b % ALPHABET.length]).join('')
}

function requestId(): string {
  return `req_${random(6).toLowerCase()}`
}

function fail(status: number, code: string, message: string): never {
  throw new ApiError(status, code, message, requestId())
}

export function applicationRef(seq: number, year = 2026): string {
  return `APP-${year}-${String(seq).padStart(5, '0')}`
}

function iso(at: number): string {
  return new Date(at).toISOString()
}

/** "2026-10-16T10:00:00+08:00": times in a webhook payload are Singapore time, with the offset. */
function sgtIso(at: number): string {
  return `${new Date(at + 8 * HOUR_MS).toISOString().slice(0, 19)}+08:00`
}

/** A start time `days` from `now` at hh:mm Singapore time. */
function sgtAt(now: number, days: number, hour: number, minute = 0): string {
  const sgtMidnight = Math.floor((now + 8 * HOUR_MS) / DAY_MS) * DAY_MS - 8 * HOUR_MS
  return iso(sgtMidnight + days * DAY_MS + hour * HOUR_MS + minute * 60_000)
}

function application(state: PlatformState, now: number, details: ApplicationDetails, submittedAgo: number): StoredApplication {
  const seq = state.nextSeq++
  return {
    ...details,
    seq,
    id: applicationRef(seq),
    status: 'pending',
    submittedAt: iso(now - submittedAgo),
    reviewedAt: null,
    reason: null,
    suspended: false,
    credentials: null,
    events: [...WEBHOOK_EVENTS],
    providerId: null,
    message: null,
  }
}

function approve(app: StoredApplication, at: number, message: string | null) {
  app.status = 'approved'
  app.reviewedAt = iso(at)
  app.message = message
  app.providerId = 100 + app.seq
  app.credentials = {
    clientId: `cl_prov_${random(10)}`,
    clientSecret: `cs_live_${random(24)}`,
    webhookSecret: `whsec_${random(24)}`,
    collected: false,
    secretIssuedAt: iso(at),
    webhookSecretIssuedAt: iso(at),
  }
}

type JobSpec = {
  status: JobStatus
  /** Days from now to the scheduled start, and its time of day. */
  day: number
  hour: number
  minute?: number
  receivedAgo: number
  cancelOnAccept?: string
}

function dispatch(state: PlatformState, app: StoredApplication, now: number, spec: JobSpec): StoredJob {
  const n = state.nextJob++
  const service = app.services[n % app.services.length]
  const district = JOB_DISTRICTS[n % JOB_DISTRICTS.length]
  const person = ELDERS[n % ELDERS.length]
  const scheduledStart = sgtAt(now, spec.day, spec.hour, spec.minute ?? 0)
  const durationMin = DURATION_MIN[service] ?? 60
  const receivedAt = iso(now - spec.receivedAgo)
  const acceptedAt = iso(now - spec.receivedAgo + 11 * 60_000)
  const timeline: TimelineStep[] = [{ step: 'dispatched', at: receivedAt }]
  let completion: Job['completion'] = null
  let cancelReason: string | null = null
  let decline: StoredJob['decline'] = null
  if (spec.status === 'accepted' || spec.status === 'completed') timeline.push({ step: 'accepted', at: acceptedAt })
  if (spec.status === 'completed') {
    const start = Date.parse(scheduledStart) + 4 * 60_000
    const end = start + (durationMin - 7) * 60_000
    timeline.push({ step: 'completed', at: iso(end + 7 * 60_000) })
    completion = {
      actualStart: iso(start),
      actualEnd: iso(end),
      note: 'Completed as planned. No concerns; the family was told on the day.',
    }
  }
  if (spec.status === 'declined') {
    timeline.push({ step: 'declined', at: acceptedAt })
    decline = { reason: 'NO_PRACTITIONER', note: null }
  }
  if (spec.status === 'cancelled') {
    timeline.push({ step: 'cancelled', at: acceptedAt })
    cancelReason = 'The family cancelled the booking.'
  }
  const job: StoredJob = {
    id: `JOB-${n}`,
    clientId: app.credentials!.clientId,
    service,
    district,
    areaName: districtName(district),
    scheduledStart,
    durationMin,
    status: spec.status,
    receivedAt,
    elder: person.elder,
    language: person.language,
    bookedBy: 'Family, via CareLink',
    instructions: INSTRUCTIONS[service] ?? '',
    address: {
      line1: `Blk ${400 + (n % 90)} ${districtName(district)} North Ave 2, #0${2 + (n % 8)}-1${10 + (n % 80)}`,
      line2: `Singapore ${460000 + ((n * 37) % 9000)} · lift lobby B`,
    },
    timeline,
    completion,
    cancelReason,
    cancelOnAccept: spec.cancelOnAccept ?? null,
    decline,
  }
  state.jobs.push(job)
  deliver(state, app, job, 'job.dispatched', Date.parse(receivedAt) - 3000)
  if (spec.status === 'cancelled') deliver(state, app, job, 'job.cancelled', Date.parse(acceptedAt))
  return job
}

/** A stand-in signature. The real one is HMAC-SHA256 over `timestamp.body`, made by notification. */
function fakeSignature(text: string): string {
  let h = 0x811c9dc5
  let out = ''
  for (let round = 0; round < 8; round++) {
    for (let i = 0; i < text.length; i++) h = Math.imul(h ^ text.charCodeAt(i), 0x01000193) >>> 0
    out += h.toString(16).padStart(8, '0')
  }
  return out
}

type Outcome = 'delivered' | 'retried' | 'retrying' | 'failed'

/**
 * What became of a delivery's attempts, made on the retry schedule from `createdAt`:
 * delivered at once; delivered after two failures; still retrying after three; or failed
 * after every attempt.
 */
function attempts(createdAt: number, outcome: Outcome, seed: number): { attempts: DeliveryAttempt[]; status: Delivery['status']; nextRetryAt: string | null } {
  const at = (n: number) => iso(createdAt + attemptOffsetMinutes(n) * MINUTE_MS)
  const quick = 60 + ((seed * 17) % 70)
  const timeout = ATTEMPT_TIMEOUT_SECONDS * 1000
  switch (outcome) {
    case 'delivered':
      return { attempts: [{ at: at(0), status: 200, ms: quick }], status: 'delivered', nextRetryAt: null }
    case 'retried':
      return {
        attempts: [
          { at: at(0), status: 500, ms: 320 },
          { at: at(1), status: null, ms: timeout },
          { at: at(2), status: 200, ms: quick },
        ],
        status: 'delivered',
        nextRetryAt: null,
      }
    case 'retrying':
      return {
        attempts: [0, 1, 2].map((n) => ({ at: at(n), status: 503, ms: 40 + n * 5 })),
        status: 'retrying',
        nextRetryAt: at(3),
      }
    case 'failed':
      return {
        attempts: Array.from({ length: MAX_ATTEMPTS }, (_, n) => ({ at: at(n), status: n % 2 ? null : 404, ms: n % 2 ? timeout : 35 })),
        status: 'failed',
        nextRetryAt: null,
      }
  }
}

/**
 * Records a webhook to the provider's endpoint, with the headers and body every attempt
 * repeats. Nothing is recorded for an event the provider has not subscribed to.
 */
function deliver(
  state: PlatformState,
  app: StoredApplication,
  job: StoredJob | null,
  event: WebhookEvent,
  at: number,
  outcome: Outcome = 'delivered',
): StoredDelivery | null {
  if (event !== 'ping' && !app.events.includes(event)) return null
  const id = `dlv_${random(6)}`
  const url = new URL(app.webhookUrl)
  const body = JSON.stringify(
    {
      event,
      delivery_id: id,
      ...(job && {
        job: {
          id: job.id,
          service: job.service.toLowerCase().replace(/ /g, '_'),
          area: `D${job.district}`,
          scheduled_start: sgtIso(Date.parse(job.scheduledStart)),
          duration_min: job.durationMin,
        },
      }),
      sent_at: sgtIso(at),
    },
    null,
    2,
  )
  const timestamp = Math.floor(at / 1000)
  const delivery: StoredDelivery = {
    id,
    clientId: app.credentials!.clientId,
    event,
    createdAt: iso(at),
    ...attempts(at, outcome, state.nextDelivery++),
    url: app.webhookUrl,
    headers: [
      `POST ${url.pathname || '/'} HTTP/1.1`,
      `Host: ${url.host}`,
      'Content-Type: application/json',
      `X-CareLink-Delivery: ${id}`,
      `X-CareLink-Event: ${event}`,
      `X-CareLink-Timestamp: ${timestamp}`,
      `X-CareLink-Signature: v1=${fakeSignature(`${timestamp}.${body}`)}`,
    ].join('\n'),
    payload: body,
  }
  state.deliveries.push(delivery)
  return delivery
}

/** New jobs dispatched to a provider as soon as it is approved, so the demo has work to show. */
function firstJobs(state: PlatformState, app: StoredApplication, now: number) {
  dispatch(state, app, now, { status: 'new', day: 3, hour: 10, receivedAgo: 2 * 60_000 })
  dispatch(state, app, now, { status: 'new', day: 2, hour: 8, minute: 30, receivedAgo: 4 * 60_000 })
  dispatch(state, app, now, {
    status: 'new',
    day: 2,
    hour: 14,
    minute: 30,
    receivedAgo: 6 * 60_000,
    cancelOnAccept: 'The family cancelled the booking before your acceptance was recorded. No action needed.',
  })
  dispatch(state, app, now, { status: 'new', day: 4, hour: 9, receivedAgo: 8 * 60_000 })
}

function seed(now: number): PlatformState {
  const state: PlatformState = {
    nextSeq: 409,
    nextJob: 24060,
    nextDelivery: 1,
    applications: [],
    jobs: [],
    deliveries: [],
    tokens: [],
    accounts: [],
    sessions: [],
    idempotency: {},
  }

  // An approved provider with history, whose demo credentials are in routes/provider/README.md.
  const bright = application(
    state,
    now,
    {
      organisationName: 'BrightHome Services',
      contactName: 'Ravi Kumar',
      contactEmail: 'partners@brighthome.sg',
      contactPhone: '+65 6789 2210',
      services: ['Light housekeeping', 'Companionship'],
      webhookUrl: 'https://brighthome.sg/api/carelink-jobs',
    },
    9 * DAY_MS,
  )
  approve(bright, now - 8 * DAY_MS, 'Welcome to the Open Platform.')
  bright.credentials = {
    clientId: 'cl_prov_BrightHome9',
    clientSecret: 'cs_demo_brighthome',
    webhookSecret: 'whsec_demo_brighthome',
    collected: true,
    secretIssuedAt: iso(now - 8 * DAY_MS),
    webhookSecretIssuedAt: iso(now - 8 * DAY_MS),
  }
  const specs: JobSpec[] = [
    { status: 'completed', day: -6, hour: 10, receivedAgo: 8 * DAY_MS },
    { status: 'completed', day: -4, hour: 14, receivedAgo: 6 * DAY_MS },
    { status: 'completed', day: -2, hour: 9, receivedAgo: 4 * DAY_MS },
    { status: 'declined', day: 1, hour: 7, receivedAgo: 3 * DAY_MS },
    { status: 'cancelled', day: 1, hour: 15, receivedAgo: 2 * DAY_MS },
    { status: 'accepted', day: 1, hour: 10, receivedAgo: 30 * HOUR_MS },
    { status: 'accepted', day: 2, hour: 13, receivedAgo: 26 * HOUR_MS },
    { status: 'new', day: 2, hour: 9, receivedAgo: 22 * HOUR_MS },
    { status: 'new', day: 2, hour: 11, receivedAgo: 20 * HOUR_MS },
    { status: 'new', day: 3, hour: 15, receivedAgo: 5 * HOUR_MS },
    {
      status: 'new',
      day: 3,
      hour: 14,
      minute: 30,
      receivedAgo: 3 * HOUR_MS,
      cancelOnAccept: 'The family cancelled the booking before your acceptance was recorded. No action needed.',
    },
    { status: 'new', day: 4, hour: 8, minute: 30, receivedAgo: 2 * HOUR_MS },
    { status: 'new', day: 4, hour: 11, receivedAgo: 50 * 60_000 },
    { status: 'new', day: 5, hour: 10, receivedAgo: 12 * 60_000 },
  ]
  for (const spec of specs) dispatch(state, bright, now, spec)
  // Not every webhook got through: one took retries, one never did, the newest is still trying.
  const dispatched = state.deliveries.filter((d) => d.event === 'job.dispatched')
  const settle = (delivery: StoredDelivery, outcome: Outcome) =>
    Object.assign(delivery, attempts(Date.parse(delivery.createdAt), outcome, 7))
  settle(dispatched[9], 'retried')
  settle(dispatched[7], 'failed')
  settle(dispatched[dispatched.length - 1], 'retrying')

  const rejected = application(
    state,
    now,
    {
      organisationName: 'QuickClean Home Care',
      contactName: 'Alvin Teo',
      contactEmail: 'alvin@quickclean.sg',
      contactPhone: '+65 6345 0090',
      services: ['Light housekeeping'],
      webhookUrl: 'https://quickclean.sg/hooks/carelink',
    },
    4 * DAY_MS,
  )
  rejected.status = 'rejected'
  rejected.reviewedAt = iso(now - 3 * DAY_MS)
  rejected.reason =
    'Your webhook URL returned 404 during our verification check. Make sure the endpoint is live and answers a test POST with a 2xx response, then apply again.'
  rejected.message = rejected.reason

  state.applications.push(bright, rejected)
  // Portal accounts for the seeded providers; their demo passwords are in routes/provider/README.md.
  for (const [email, password] of SEEDED_ACCOUNTS) addAccount(state, email, password)
  state.applications.push(
    application(
      state,
      now,
      {
        organisationName: 'FreshCart Errands',
        contactName: 'Daniel Goh',
        contactEmail: 'daniel@freshcart.com.sg',
        contactPhone: '+65 6222 8130',
        services: ['Grocery assistance'],
        webhookUrl: 'https://hooks.freshcart.com.sg/carelink',
      },
      30 * HOUR_MS,
    ),
    application(
      state,
      now,
      {
        organisationName: 'Kampung Care Escorts Pte Ltd',
        contactName: 'Siti Aminah',
        contactEmail: 'ops@kampungcare.sg',
        contactPhone: '+65 6123 4501',
        services: ['Hospital escort', 'Companionship'],
        webhookUrl: 'https://api.kampungcare.sg/carelink/webhooks',
      },
      2 * HOUR_MS,
    ),
  )
  return state
}

function publicView(app: StoredApplication): Application {
  return {
    id: app.id,
    organisationName: app.organisationName,
    contactName: app.contactName,
    contactEmail: app.contactEmail,
    contactPhone: app.contactPhone,
    services: [...app.services],
    webhookUrl: app.webhookUrl,
    status: app.status,
    submittedAt: app.submittedAt,
    reviewedAt: app.reviewedAt,
    reason: app.reason,
    suspended: app.suspended,
  }
}

/** Seeded providers and their demo passwords. */
const SEEDED_ACCOUNTS: [string, string][] = [
  ['partners@brighthome.sg', 'brighthome-demo'],
  ['alvin@quickclean.sg', 'quickclean-demo'],
  ['daniel@freshcart.com.sg', 'freshcart-demo'],
  ['ops@kampungcare.sg', 'kampungcare-demo'],
]

const SESSION_TTL_MS = 8 * HOUR_MS

function normalise(email: string): string {
  return email.trim().toLowerCase()
}

/** A stand-in for a password hash. The real platform uses a slow one (argon2id or bcrypt). */
function hashPassword(password: string, salt: string): string {
  return fakeSignature(`${salt}:${password}`)
}

function addAccount(state: PlatformState, email: string, password: string) {
  const salt = random(12)
  state.accounts = state.accounts.filter((a) => a.email !== normalise(email))
  state.accounts.push({ email: normalise(email), salt, passwordHash: hashPassword(password, salt) })
}

/** The account's latest application: a provider turned down applies again with the same account. */
function latestApplication(state: PlatformState, email: string): StoredApplication | null {
  return (
    state.applications
      .filter((a) => normalise(a.contactEmail) === normalise(email))
      .sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))[0] ?? null
  )
}

function signedIn(state: PlatformState, session: string, now: number): StoredApplication {
  const found = state.sessions.find((s) => s.token === session)
  if (!found || found.expiresAt <= now) fail(401, 'not_signed_in', 'Sign in to the portal again.')
  const app = latestApplication(state, found.email)
  if (!app) fail(401, 'not_signed_in', 'Sign in to the portal again.')
  return app
}

// ── Public provider API ─────────────────────────────────────────────────────────────────────

/**
 * POST /v1/applications {details, password} — also creates the portal account the provider
 * signs in with. 409 when the email already has an account with an application that is pending
 * or approved; a provider turned down may apply again, and sets a new password doing so.
 */
export function submitApplication(details: ApplicationDetails, password: string, now = Date.now()): { applicationId: string } {
  const state = load()
  const current = latestApplication(state, details.contactEmail)
  if (current && current.status !== 'rejected') {
    fail(409, 'account_exists', 'This email already has a CareLink provider account. Sign in instead.')
  }
  if (password.length < MIN_PASSWORD_LENGTH) fail(422, 'weak_password', `Use at least ${MIN_PASSWORD_LENGTH} characters.`)
  const app = application(state, now, details, 0)
  state.applications.push(app)
  addAccount(state, details.contactEmail, password)
  save(state)
  return { applicationId: app.id }
}

/** POST /v1/portal/sessions {email, password} → a session for this portal. */
export function signIn(email: string, password: string, now = Date.now()): { session: string; expiresAt: number } {
  const state = load()
  const account = state.accounts.find((a) => a.email === normalise(email))
  if (!account || account.passwordHash !== hashPassword(password, account.salt)) {
    fail(401, 'invalid_login', 'Email or password is wrong.')
  }
  const session = { token: `ps_${random(24)}`, email: account.email, expiresAt: now + SESSION_TTL_MS }
  state.sessions = [...state.sessions.filter((s) => s.expiresAt > now), session]
  save(state)
  return { session: session.token, expiresAt: session.expiresAt }
}

/** DELETE /v1/portal/sessions/current */
export function signOut(session: string) {
  const state = load()
  state.sessions = state.sessions.filter((s) => s.token !== session)
  save(state)
}

/** GET /v1/portal/application — the signed-in provider's application. */
export function getMyApplication(session: string, now = Date.now()): Application {
  return publicView(signedIn(load(), session, now))
}

/**
 * POST /v1/portal/credentials/collect — hands an approved provider its credentials, once.
 * 409 before approval; 410 once they have been collected.
 */
export function collectCredentials(session: string, now = Date.now()): IssuedCredentials {
  const state = load()
  const app = signedIn(state, session, now)
  if (app.status !== 'approved' || !app.credentials) fail(409, 'not_approved', 'This application is not approved.')
  if (app.credentials.collected) {
    fail(410, 'credentials_collected', 'These credentials were already shown. Rotate the secret to get a new one.')
  }
  app.credentials.collected = true
  save(state)
  const { clientId, clientSecret, webhookSecret } = app.credentials
  return { clientId, clientSecret, webhookSecret }
}

/** POST /oauth2/token, grant_type=client_credentials */
export function requestToken(clientId: string, clientSecret: string, now = Date.now()): AccessToken {
  const state = load()
  const app = state.applications.find((a) => a.credentials?.clientId === clientId.trim())
  if (!app || app.credentials!.clientSecret !== clientSecret.trim()) {
    fail(401, 'invalid_client', 'Client authentication failed.')
  }
  if (app.suspended) fail(403, 'provider_suspended', 'CareLink has suspended this provider.')
  const token = { token: `at_${random(32)}`, clientId: app.credentials!.clientId, expiresAt: now + TOKEN_TTL_MS }
  state.tokens = [...state.tokens.filter((t) => t.expiresAt > now), token]
  save(state)
  return { accessToken: token.token, scope: SCOPE, issuedAt: now, expiresAt: token.expiresAt }
}

/**
 * A token from the real token endpoint, for the job calls this mock still serves until report has the
 * job API. Its client is given the demo's first jobs the first time, as an approved provider was here.
 * Webhooks for them are not shown: the delivery log is notification's.
 */
export function adoptAccessToken(accessToken: string, clientId: string, expiresAt: number, now = Date.now()) {
  const state = load()
  state.tokens = [...state.tokens.filter((t) => t.expiresAt > now && t.token !== accessToken), { token: accessToken, clientId, expiresAt }]
  if (!state.jobs.some((j) => j.clientId === clientId)) {
    const details = {
      organisationName: '',
      contactName: '',
      contactEmail: '',
      contactPhone: '',
      services: [...PROVIDER_SERVICES],
      webhookUrl: 'https://sandbox.invalid/',
    }
    const provider: StoredApplication = {
      ...application(state, now, details, 0),
      status: 'approved',
      events: [],
      credentials: { clientId, clientSecret: '', webhookSecret: '', collected: true, secretIssuedAt: iso(now), webhookSecretIssuedAt: iso(now) },
    }
    firstJobs(state, provider, now)
  }
  save(state)
}

function authorise(state: PlatformState, accessToken: string, now: number): string {
  const token = state.tokens.find((t) => t.token === accessToken)
  if (!token || token.expiresAt <= now) fail(401, 'invalid_token', 'The access token is missing or expired.')
  if (state.applications.find((a) => a.credentials?.clientId === token.clientId)?.suspended) {
    fail(403, 'provider_suspended', 'CareLink has suspended this provider.')
  }
  return token.clientId
}

const TAB_STATUSES: Record<JobTab, JobStatus[]> = {
  new: ['new'],
  accepted: ['accepted'],
  completed: ['completed'],
  closed: ['declined', 'cancelled'],
}

function summary(job: StoredJob): JobSummary {
  const { id, service, district, areaName, scheduledStart, durationMin, status, receivedAt } = job
  return { id, service, district, areaName, scheduledStart, durationMin, status, receivedAt }
}

function detail(job: StoredJob): Job {
  const visible = job.status === 'accepted' || job.status === 'completed'
  return {
    ...summary(job),
    elder: job.elder,
    language: job.language,
    bookedBy: job.bookedBy,
    instructions: job.instructions,
    address: visible ? job.address : null,
    timeline: job.timeline.map((t) => ({ ...t })),
    completion: job.completion,
    cancelReason: job.cancelReason,
  }
}

/** GET /v1/jobs?status=&page= — newest first. Also returns every tab's count. */
export function listJobs(accessToken: string, tab: JobTab, page: number, now = Date.now()): JobPage {
  const state = load()
  const clientId = authorise(state, accessToken, now)
  const mine = state.jobs.filter((j) => j.clientId === clientId)
  const counts = {} as Record<JobTab, number>
  for (const t of Object.keys(TAB_STATUSES) as JobTab[]) {
    counts[t] = mine.filter((j) => TAB_STATUSES[t].includes(j.status)).length
  }
  const rows = mine
    .filter((j) => TAB_STATUSES[tab].includes(j.status))
    .sort((a, b) => b.receivedAt.localeCompare(a.receivedAt))
  const current = Math.max(1, Math.min(page, Math.ceil(rows.length / JOBS_PAGE_SIZE) || 1))
  return {
    items: rows.slice((current - 1) * JOBS_PAGE_SIZE, current * JOBS_PAGE_SIZE).map(summary),
    page: current,
    pageSize: JOBS_PAGE_SIZE,
    total: rows.length,
    counts,
  }
}

function myJob(state: PlatformState, clientId: string, jobId: string): StoredJob {
  const job = state.jobs.find((j) => j.id === jobId && j.clientId === clientId)
  if (!job) fail(404, 'job_not_found', 'No job with that ID was dispatched to you.')
  return job
}

/** GET /v1/jobs/{id} — the address only once the job is accepted. */
export function getJob(accessToken: string, jobId: string, now = Date.now()): Job {
  const state = load()
  return detail(myJob(state, authorise(state, accessToken, now), jobId))
}

/**
 * Runs a state-changing job call once per Idempotency-Key: a repeat of a key that already
 * succeeded returns the job as it is now instead of acting again.
 */
function once(accessToken: string, jobId: string, op: string, key: string, now: number, act: (job: StoredJob, state: PlatformState) => void): Job {
  const state = load()
  const clientId = authorise(state, accessToken, now)
  const job = myJob(state, clientId, jobId)
  const seen = state.idempotency[key]
  if (seen) {
    if (seen.jobId !== jobId || seen.op !== op) fail(422, 'idempotency_key_reused', 'This Idempotency-Key was used for another request.')
    return detail(job)
  }
  act(job, state)
  state.idempotency[key] = { jobId, op }
  save(state)
  return detail(job)
}

function notActionable(job: StoredJob): never {
  fail(409, 'job_not_actionable', `This job is ${job.status} and can no longer be changed.`)
}

/** POST /v1/jobs/{id}/accept */
export function acceptJob(accessToken: string, jobId: string, idempotencyKey: string, now = Date.now()): Job {
  return once(accessToken, jobId, 'accept', idempotencyKey, now, (job, state) => {
    if (job.status === 'new' && job.cancelOnAccept) {
      job.status = 'cancelled'
      job.cancelReason = job.cancelOnAccept
      job.cancelOnAccept = null
      job.timeline.push({ step: 'cancelled', at: iso(now) })
      const app = state.applications.find((a) => a.credentials?.clientId === job.clientId)!
      deliver(state, app, job, 'job.cancelled', now)
      save(state)
      fail(409, 'job_cancelled', job.cancelReason)
    }
    if (job.status !== 'new') notActionable(job)
    job.status = 'accepted'
    job.timeline.push({ step: 'accepted', at: iso(now) })
  })
}

/** POST /v1/jobs/{id}/decline {reason, note} */
export function declineJob(
  accessToken: string,
  jobId: string,
  body: { reason: DeclineReason; note: string | null },
  idempotencyKey: string,
  now = Date.now(),
): Job {
  return once(accessToken, jobId, 'decline', idempotencyKey, now, (job) => {
    if (job.status !== 'new') notActionable(job)
    job.status = 'declined'
    job.decline = body
    job.timeline.push({ step: 'declined', at: iso(now) })
  })
}

/** POST /v1/jobs/{id}/complete {actual_start, actual_end, note} */
export function completeJob(
  accessToken: string,
  jobId: string,
  body: { actualStart: string; actualEnd: string; note: string },
  idempotencyKey: string,
  now = Date.now(),
): Job {
  return once(accessToken, jobId, 'complete', idempotencyKey, now, (job) => {
    if (job.status !== 'accepted') notActionable(job)
    if (!(Date.parse(body.actualEnd) > Date.parse(body.actualStart))) {
      fail(422, 'invalid_times', 'The actual end must be after the actual start.')
    }
    job.status = 'completed'
    job.completion = { ...body }
    job.timeline.push({ step: 'completed', at: iso(now) })
  })
}

// ── Developer portal ─────────────────────────────────────────────────────────────────────
// The signed-in provider's own settings, open once its application is approved.

function approvedApp(state: PlatformState, session: string, now: number): StoredApplication & { credentials: NonNullable<StoredApplication['credentials']> } {
  const app = signedIn(state, session, now)
  if (app.status !== 'approved' || !app.credentials) fail(403, 'not_approved', 'This application is not approved.')
  return app as StoredApplication & { credentials: NonNullable<StoredApplication['credentials']> }
}

function hint(secret: string): string {
  return secret.slice(-4)
}

function storedDelivery(state: PlatformState, clientId: string, deliveryId: string): StoredDelivery {
  const delivery = state.deliveries.find((d) => d.id === deliveryId && d.clientId === clientId)
  if (!delivery) fail(404, 'delivery_not_found', 'No delivery with that ID.')
  return delivery
}

function publicDelivery({ clientId: _clientId, ...delivery }: StoredDelivery): Delivery {
  return { ...delivery, attempts: delivery.attempts.map((a) => ({ ...a })) }
}

/** GET /v1/portal/credentials */
export function getCredentialsSummary(session: string, now = Date.now()): CredentialsSummary {
  const { credentials } = approvedApp(load(), session, now)
  return {
    clientId: credentials.clientId,
    secretHint: hint(credentials.clientSecret),
    secretIssuedAt: credentials.secretIssuedAt,
    scope: SCOPE,
    tokenEndpoint: TOKEN_ENDPOINT,
  }
}

/** POST /v1/portal/credentials/rotate — the old secret stops working at once; tokens already issued run out as usual. */
export function rotateClientSecret(session: string, now = Date.now()): { clientSecret: string } {
  const state = load()
  const { credentials } = approvedApp(state, session, now)
  credentials.clientSecret = `cs_live_${random(24)}`
  credentials.secretIssuedAt = iso(now)
  save(state)
  return { clientSecret: credentials.clientSecret }
}

function settingsOf(app: StoredApplication & { credentials: NonNullable<StoredApplication['credentials']> }): WebhookSettings {
  return {
    url: app.webhookUrl,
    events: [...app.events],
    secretHint: hint(app.credentials.webhookSecret),
    secretIssuedAt: app.credentials.webhookSecretIssuedAt,
  }
}

/** GET /v1/portal/webhook */
export function getWebhookSettings(session: string, now = Date.now()): WebhookSettings {
  return settingsOf(approvedApp(load(), session, now))
}

/** PUT /v1/portal/webhook {url, events} — the URL must be HTTPS. */
export function updateWebhookSettings(session: string, body: { url: string; events: WebhookEvent[] }, now = Date.now()): WebhookSettings {
  const state = load()
  const app = approvedApp(state, session, now)
  let parsed: URL | null = null
  try {
    parsed = new URL(body.url.trim())
  } catch {
    parsed = null
  }
  if (!parsed || parsed.protocol !== 'https:' || !parsed.hostname.includes('.')) {
    fail(422, 'invalid_url', 'The endpoint must be a full HTTPS URL.')
  }
  app.webhookUrl = body.url.trim()
  app.events = WEBHOOK_EVENTS.filter((e) => body.events.includes(e))
  save(state)
  return settingsOf(app)
}

/** POST /v1/portal/webhook/rotate-secret — later webhooks are signed with the new secret. */
export function rotateWebhookSecret(session: string, now = Date.now()): { webhookSecret: string } {
  const state = load()
  const { credentials } = approvedApp(state, session, now)
  credentials.webhookSecret = `whsec_${random(24)}`
  credentials.webhookSecretIssuedAt = iso(now)
  save(state)
  return { webhookSecret: credentials.webhookSecret }
}

/** GET /v1/portal/webhook/deliveries — newest first. */
export function listWebhookDeliveries(session: string, now = Date.now()): Delivery[] {
  const state = load()
  const { credentials } = approvedApp(state, session, now)
  return state.deliveries
    .filter((d) => d.clientId === credentials.clientId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .map(publicDelivery)
}

/**
 * POST /v1/portal/webhook/deliveries/{id}/resend — one more attempt now, with the same delivery
 * ID, so a receiver that already processed it ignores it. The mock's endpoint always answers 200.
 */
export function resendDelivery(session: string, deliveryId: string, now = Date.now()): Delivery {
  const state = load()
  const { credentials } = approvedApp(state, session, now)
  const delivery = storedDelivery(state, credentials.clientId, deliveryId)
  delivery.attempts.push({ at: iso(now), status: 200, ms: 70 + (now % 60) })
  delivery.status = 'delivered'
  delivery.nextRetryAt = null
  save(state)
  return publicDelivery(delivery)
}

/** POST /v1/portal/webhook/test — a `ping` to the endpoint, whatever it subscribes to. */
export function sendTestEvent(session: string, now = Date.now()): Delivery {
  const state = load()
  const app = approvedApp(state, session, now)
  const delivery = deliver(state, app, null, 'ping', now)!
  save(state)
  return publicDelivery(delivery)
}

function health(state: PlatformState, app: StoredApplication, now: number): IntegrationHealth {
  const since = iso(now - 7 * DAY_MS)
  const recent = state.deliveries.filter((d) => d.clientId === app.credentials?.clientId && d.createdAt >= since)
  const ok = recent.flatMap((d) => d.attempts.filter((a) => a.status !== null && a.status < 300).map((a) => a.at)).sort()
  return {
    delivered: recent.filter((d) => d.status === 'delivered').length,
    retrying: recent.filter((d) => d.status === 'retrying').length,
    failed: recent.filter((d) => d.status === 'failed').length,
    lastDeliveredAt: ok[ok.length - 1] ?? null,
    suspended: app.suspended,
  }
}

/** GET /v1/portal/health */
export function getIntegrationHealth(session: string, now = Date.now()): IntegrationHealth {
  const state = load()
  return health(state, approvedApp(state, session, now), now)
}

// ── Internal review API, for the manager console ───────────────────────────────────────────

export type ReviewApplication = ApplicationDetails & { seq: number; id: string; submittedAt: string }

export type ApprovedProvider = ReviewApplication & {
  providerId: number
  clientId: string
  approvedAt: string
  suspended: boolean
  health: IntegrationHealth
  jobs: Record<JobTab, number>
}

function reviewView(app: StoredApplication): ReviewApplication {
  const { seq, id, submittedAt } = app
  return { ...publicView(app), seq, id, submittedAt }
}

/** Applications waiting for a decision, newest first. */
export function listApplicationsForReview(): ReviewApplication[] {
  return load()
    .applications.filter((a) => a.status === 'pending')
    .sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))
    .map(reviewView)
}

/** Approved providers, most recently approved first, with how their integration is doing. */
export function listApprovedProviders(now = Date.now()): ApprovedProvider[] {
  const state = load()
  return state.applications
    .filter((a) => a.status === 'approved')
    .sort((a, b) => (b.reviewedAt ?? '').localeCompare(a.reviewedAt ?? ''))
    .map((a) => {
      const mine = state.jobs.filter((j) => j.clientId === a.credentials!.clientId)
      const jobs = {} as Record<JobTab, number>
      for (const t of Object.keys(TAB_STATUSES) as JobTab[]) jobs[t] = mine.filter((j) => TAB_STATUSES[t].includes(j.status)).length
      return {
        ...reviewView(a),
        providerId: a.providerId!,
        clientId: a.credentials!.clientId,
        approvedAt: a.reviewedAt!,
        suspended: a.suspended,
        health: health(state, a, now),
        jobs,
      }
    })
}

function approvedBySeq(state: PlatformState, seq: number): StoredApplication {
  const app = state.applications.find((a) => a.seq === seq && a.status === 'approved')
  if (!app) fail(404, 'provider_not_found', 'No approved provider with that ID.')
  return app
}

/**
 * Suspends an approved provider: its tokens stop working and new ones are refused, so it can
 * no longer see or act on jobs. Reinstating undoes it.
 */
export function suspendProvider(seq: number) {
  const state = load()
  approvedBySeq(state, seq).suspended = true
  save(state)
}

export function reinstateProvider(seq: number) {
  const state = load()
  approvedBySeq(state, seq).suspended = false
  save(state)
}

function pending(state: PlatformState, seq: number): StoredApplication {
  const app = state.applications.find((a) => a.seq === seq)
  if (!app || app.status !== 'pending') fail(409, 'already_decided', 'This application has already been answered.')
  return app
}

/**
 * Approves the application: registers the provider, issues its credentials and — standing in
 * for report's dispatch — sends it its first jobs.
 */
export function approveApplication(seq: number, message: string | null, now = Date.now()): { providerId: number } {
  const state = load()
  const app = pending(state, seq)
  approve(app, now, message)
  firstJobs(state, app, now)
  save(state)
  return { providerId: app.providerId! }
}

/** Rejects the application; `reason` is shown to the provider when they check their status. */
export function rejectApplication(seq: number, reason: string, now = Date.now()) {
  const state = load()
  const app = pending(state, seq)
  app.status = 'rejected'
  app.reviewedAt = iso(now)
  app.reason = reason
  app.message = reason
  save(state)
}
