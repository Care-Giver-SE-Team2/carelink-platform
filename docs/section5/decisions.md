# Section 5 design decisions

Decisions taken for the CareLink Open Platform (WBS 2.3, 5.4, 6.1, 6.2), with the reasons, for the report.
Newest last. Owner: Section 5 (notification).

## D1. report serves the job endpoints; notification serves onboarding, the portal and webhooks

*2026-10-13. Needs Section 4's agreement.*

The API gateway routes `/open/v1/jobs/**` to report and every other `/open/**` path to notification.

- report already owns a value-added request's life (dispatched, completed, cancelled). Accepting, declining and
  completing a job are further steps in that life, so they change report's data in one transaction.
- The race between a family cancelling and a provider accepting is decided in one place: report refuses the
  acceptance with 409 in the same transaction that sees the cancellation. If notification kept the job, it would
  learn of the cancellation by event and could accept a job already cancelled.
- No job state is kept twice.

Rejected: notification serving the whole public API (job state in two services, an event each way to keep it in
step); a new fifth service (a database, chart, pipeline and SonarCloud project before the 10/21 rehearsal).

## D2. A new event, `ProviderJobUpdated`, tells notification what became of a job

*2026-10-13. Needs Section 4's agreement.*

report publishes one event per change to a provider job, carrying the job's state after it. notification sends
`job.dispatched` for a new job and `job.cancelled` for a cancelled one, and ignores the other states.

- It follows the catalogue's rule "state, not commands", and one event with a `version` keeps the order simple.
- `NotificationRequested` is addressed to a CareLink user (`recipientUserId`); sending it to a provider would change
  what it means, which the catalogue says needs a new event name anyway.
- It carries no elder's name or address, so notification never holds them. A provider reads those through the job
  API once it has accepted.

## D3. Provider registration lives in notification, as a module of its own

*2026-10-13.*

notification keeps applications, approved providers, their credential references, webhook endpoints and webhook
deliveries, in an `openplatform` module with `provider_` tables, apart from the inbox. It publishes
`ProviderUpdated`, from which report keeps the copy it dispatches from.

- Onboarding is Section 5's work (PT01); keeping it in notification keeps it out of Section 4's service.
- Webhook delivery needs the endpoint, secret reference and suspension locally, on every attempt.
- report needs only who the provider is, what it offers and whether it is suspended: a read copy, kept from an
  event, as report already keeps facts from visit's and core's events.

**Domain-driven design.** The inbox and the Open Platform are two bounded contexts deployed in one service, as core
hosts several modules. They share no model and no table. Each context owns its state (jobs stay in report), and
the contexts meet only through events and the API. "Provider" is the full aggregate in the Open Platform and a
small read copy in report. Partner systems integrate through an Open Host Service with a Published Language
(`open-platform.yaml`, `webhooks.md`). The only mismatch is the service's name; the module can move to a service of
its own without another context changing.

Rejected: report (Section 5's code in Section 4's service); core (Section 1's service, the heaviest change).

## D4. Providers sign in to the portal through a Cognito user pool

*Replaced by D15.*

*2026-10-13.*

Applying creates the provider's portal user in Cognito, with the password typed on the form; CareLink never stores
it. The portal signs in with Cognito and calls `/open/v1/portal/**` with the user's token. Integrations use a
Cognito app client per provider (client credentials).

- Phase 5 builds Cognito anyway, and no service checks a password, which `platform-security` forbids.
- Locally, where LocalStack's free tier may not have Cognito, one small token stub stands in for both kinds of
  token.

Fallback, if the stub costs too much before the rehearsal: portal accounts kept by notification, as the mock does.

## D5. Public paths are under `/open/v1`

*Changed by D15: `/api/open/v1`.*

*2026-10-13. Needs Section 1's review.*

Three prefixes, three audiences: `/api` (the CareLink app, a core session), `/internal` (services, in the cluster
only) and `/open` (partners, a Cognito token). One rule in `platform-security` can then check tokens on `/open/**`
for both report and notification.

## D6. Wire conventions follow the internal API

*2026-10-13.*

camelCase fields, UPPER enum values, RFC 9457 `ProblemDetail` with a `code` (400 with `fields` for validation, 409
for a business rule), `page` from 0 and `size`, Singapore business times with their offset, and visit's
`Idempotency-Key` rule. The workbench's mock used snake_case in webhook bodies and lowercase codes; Phase 4 aligns
it.

## D7. When report dispatches to a provider, and how long the provider has

*2026-10-13. Section 4 implements it.*

- **Providers only when no caregiver is free.** When the family approves a request, report picks a free caregiver
  as it does today. Only when it finds nobody does it offer the job to a provider that offers the service, before
  asking a manager. Caregivers stay first; providers add capacity.
- **A provider job is report's, not a visit.** Provider staff are not CareLink caregivers and never check in or
  out, so no visit is created in visit. report keeps the job beside the request and settles the request from the
  job: COMPLETED when the provider completes it, CANCELLED when it is called off.
- **The provider has until `respondBy`:** 24 hours after dispatch, but no later than 4 hours before the start, so
  that a fallback still has time. A request less than 5 hours from its start is not offered to providers, since that
  would leave a provider less than an hour; it goes to a manager as today.
- **If the provider declines or lets `respondBy` pass, a manager takes it over**, as today when nobody is free:
  the manager is alerted and either staffs it with a caregiver or cancels the request, which tells the family
  (report's existing cancellation, `BY_MANAGER`). A job whose `respondBy` passes becomes CANCELLED with that reason,
  and the provider is sent `job.cancelled`.
  - Not cancelling at once: who is free changes in the up to 24 hours a provider has (leave withdrawn, visits
    called off), and a manager can still find someone. Cancelling would leave the family worse off than today,
    where a manager always gets the chance.
  - If the manager staffs nobody, report's existing settlement still closes the request and tells the family when
    the visit goes uncovered at its start (`NOT_PROVIDED`).
- **Where a job's details come from.** report already reaches them through `CoreApi`, with no change to core:
  `elderCaregiverView` for the service address, postal sector, languages and access notes, and
  `elderReportProfile` for the gender, age and mobility the job's `elderSummary` is made from. The district and
  area name are looked up from the postal sector in Singapore's fixed table of postal districts.

## D8. How webhooks are delivered

*2026-10-14.*

- **A scheduled poll, not a send in the request.** A delivery is created in the transaction of its cause and
  attempted by a job every 10 seconds, on one replica (ShedLock). The cause's transaction never waits for a
  provider's server, and a delivery whose first attempt fails is already where its retries are.
- **The HTTP call outside any transaction, the outcome written conditionally.** An endpoint taking ten seconds
  holds no database connection, and an outcome is kept only if the delivery is still as it was read, so two writers
  never both move it on.
- **The body kept, the signature made per attempt.** Every attempt sends the same bytes, which the provider
  deduplicates by delivery ID; the timestamp, and so the signature, is new each time, as the 5-minute replay window
  needs.
- **Server-side request forgery refused.** A provider chooses its URL, so CareLink sends only HTTPS to public
  addresses, refusing loopback, private, link-local (the cloud metadata service) and reserved ones. The cluster's
  egress rules are the second line, against a name that changes between the check and the connection.
- **Secrets encrypted in notification's own database, not in Secrets Manager.** AES-256-GCM, with one key from the
  service's settings, kept where the database password is (`.env` locally, the service's Kubernetes Secret in the
  cloud). A copy of the database cannot sign a webhook, which is what keeping the secret out of plain text is for.
  Secrets Manager was the first choice and was built; it was replaced because this is a course project with no
  live customers, and the encrypted column costs nothing and needs no extra AWS service, IAM policy or LocalStack
  service. The trade-off: the key is one more setting to keep safe, and changing it would need the stored secrets
  re-encrypted (the `v1:` prefix leaves room for that).

## D9. Email (Phase 3) is not built

*2026-10-14.*

CareLink keeps no email address for anyone: `app_user` has a username and display name, `family_member` a phone,
and `CoreApi` returns neither an address nor a way to find one. `NotificationRequested` names its recipient by
account id only, and no sender sets `channel` to `EMAIL`. Building the SES sender would have meant storing
addresses first (in core, which Section 1 reviews, or a second copy in notification) for messages nobody sends.
The inbox stays the only channel; the `channel` column keeps room for another.

## D10. The Partner Portal and the manager's screen stay in `frontend/`

*2026-10-14.*

The workbench (PT04) is the Partner Portal at `/provider`, built in Phase 4's first step beside the app, and the
manager's approval screen is under `/manager`. The portal calls only the public Open API (`publicApiOnly.test.ts`
fails the build otherwise); the manager's screen calls notification's internal `/api/provider-applications` and
`/api/providers` with the manager's session.

## D11. `platform-security` checks the Open Platform's tokens on `/open/**`

*Changed by D15: on `/api/open/**`, with notification as the issuer.*

*2026-10-14. Changes `libs/platform-security`: needs Section 1's review.*

A second security chain, ahead of the session chain and only for `/open/**`, turned on by
`carelink.open-platform.jwk-set-uri`. It validates Cognito-shaped access tokens (signature against the pool's
keys, expiry, issuer, `token_use` = `access`), tells a portal user (`username` claim) from a provider's server
(none), and checks the job scopes: `jobs.read` for GET, `jobs.write` for the rest. Refusals are the contract's
401 `INVALID_TOKEN` and 403 `INSUFFICIENT_SCOPE`. Only `POST /open/v1/applications` and the local stand-in for
Cognito are open. Services without the setting (core, visit) are unchanged.

- One rule for both services that serve `/open` (notification now, report for the job API), as D5 planned.
- Rejected: each service with its own chains (the session chain copied, and report doing it again).
- Found while building it: the services' component scan covers the library's package, so the chain's class is
  imported by the auto-configuration and deliberately not a `@Configuration`; a test now covers that case.

## D12. Locally, notification stands in for Cognito

*Replaced by D15: the stand-in became the implementation.*

*2026-10-14. Implements D4.*

`/open/dev-auth` answers what a client asks the user pool for, in the pool's own shapes: `InitiateAuth` with
`USER_PASSWORD_AUTH` (the portal's sign-in), `/oauth2/token` with the client-credentials grant (a provider's
server), and `/.well-known/jwks.json` (report will fetch it). The services check its tokens exactly as they will
check the pool's, and the portal makes the same calls, only to another address.

- Enabled by `carelink.open-platform.dev-auth.enabled` in `docker-compose.yml` only; never in the cloud.
- Its signing key is made at start-up and never written down, so a restart ends every token.
- It keeps users and clients in `dev_identity_user` and `dev_identity_client`: a bcrypt password hash and a
  SHA-256 client-secret hash, the secrets being random. Empty in the cloud.
- With neither Cognito nor the stand-in configured, applying and approving answer 503 and the rest of the
  service runs as before. The Cognito adapter comes with Phase 5's user pool.

## D13. Credentials are made at approval and shown once

*2026-10-14.*

- **At approval**, not at collection: the app client and the webhook signing secret are made in the approval's
  transaction, so `ProviderUpdated` carries the `clientId` from the start and the endpoint can be sent to.
- **Held sealed until collected.** Both secrets are kept encrypted (D8's key) in `provider` until the provider
  collects them from the portal, once (410 `CREDENTIALS_COLLECTED` after); then the columns are emptied. Only the
  client secret's last four characters stay. The signing secret stays sealed on the endpoint, which signs with it.
- **Rotating the client secret replaces the client**, as Cognito issues a secret only with a client; report
  hears the new `clientId` (`ProviderUpdated`, version up by one). Rotating the signing secret keeps the old
  one signing for 24 hours (`webhooks.md`, section 5) and tells report nothing.
- **A provider takes its application's id**: one application makes one provider, and the id is known before
  anything is kept.
- Suspending disables the provider's webhook endpoint as well as telling report; reinstating enables it.

## D14. The job API waits for Section 4

*Replaced by D16: Section 5 built the job API in report.*

*2026-10-14.*

report has no job endpoints or `ProviderJobUpdated` yet. Phase 4 builds notification's side only; the Sandbox's
job screens stay on the mock, which accepts a real token from the token endpoint and gives its client the demo's
jobs. The portal's `api.ts` uses the real Open API for everything else, and the mock under Vitest (or with
`VITE_OPEN_PLATFORM_MOCK=true`), so the page tests keep running against it. The manager's panel shows no job
counts until report serves them.

## D15. notification issues the Open Platform's tokens itself, under `/api/open/v1`

*2026-10-14. Replaces D4 and D12; changes D5 and D11. The `libs/shared` and `libs/platform-security` changes need
Section 1's review.*

Section 5 has no access to the cloud account, so Cognito (D4) could not be built. D12's stand-in already did what
Cognito would have, in code; it is now the implementation, everywhere:

- **Portal users and clients** are kept in `provider_portal_user` (bcrypt password hash) and `provider_client`
  (SHA-256 of a random 51-character secret). Signing in is `POST /api/open/v1/portal/sessions` `{email,
  password}`; a server's token is the standard client-credentials grant at `POST /api/open/v1/oauth2/token`; the
  public keys are at `GET /api/open/v1/.well-known/jwks.json`, for report.
- **The signing key is kept in the database**, in `provider_token_key`, encrypted with the webhook key D8
  already deploys (`carelink.webhooks.encryption-key`). The first replica to start makes it and every replica
  reads the same one, so tokens survive restarts and work on any replica. No new setting or cloud resource.
- **Five wrong passwords in a row lock the sign-in for 15 minutes** (429 `TOO_MANY_ATTEMPTS`), the guessing
  protection Cognito would have given. Client secrets are too long to guess, so the token endpoint does not lock.
- **The issuer is a fixed name**, `carelink-open-platform`, and the token URL a path on CareLink's own address,
  so no environment needs a URL set.
- **Paths are under `/api/open/v1`, not `/open/v1`.** CloudFront sends only `/api/*` to the services; anything
  else gets the app's `index.html`. Its behaviours are in the public-site Terraform, outside the repository, so
  `/open` could not be added without cloud access. `/api/open` needs only the ingress route (in the chart) and
  shared's request interceptor to leave the four public calls alone, as it does family sign-up.

Trade-offs, for the report: CareLink now holds password hashes and is answerable for them; there is no MFA,
password reset or email verification, which Cognito would have offered; and Phase 5 no longer needs a user pool
or an API Gateway authorizer. Rejected: Spring Authorization Server (a second framework for one grant and one
sign-in), and opaque portal sessions in Redis (two kinds of token for the services to check instead of one).

## D16. Section 5 builds the job API in report

*Changed by D17: the offer and the manager's visit are open together.*

*2026-10-16. Implements D1, D2 and D7. Changes report (Section 4's service): needs Section 4's review.*

Phase 4 has to finish onboard → accept job → report completion by 10/20, and report had no job endpoints. Section 5
built them in report, as D1 places them, rather than wait:

- **Dispatch.** When the family approves a request and the primary caregiver is not free, report offers it to the
  active provider that offers the service and was offered a job least recently (one never offered any first), if
  the start is at least 5 hours away. The request is DISPATCHED with no visit. Otherwise it gets the visit nobody
  holds, as before.
- **A declined or unanswered job goes to a manager by getting that visit then**: report schedules the same
  unstaffed visit it would have made at approval and alerts the managers, so the Extra services screen, the
  caregiver picker and cancellation work unchanged. A job whose `respondBy` passes is called off by a scheduled
  check every minute, or at once when the provider answers late (409 `RESPONSE_DEADLINE_PASSED`, the cancellation
  kept).
- **One job per request.** A declined request is not offered to a second provider; the manager has it.
- **Cancel against accept** is decided on the locked job row in report: whichever transaction is second sees the
  other (409 `JOB_CANCELLED`, with CareLink's reason).
- **report keeps a copy of providers** (`report_provider`, from `ProviderUpdated`) and finds the caller by the
  token's `client_id`. A client that is no provider's any more (its secret was rotated) is 401 `INVALID_TOKEN`; a
  suspended provider is 403 `PROVIDER_SUSPENDED`.
- **What the provider is told** comes from `CoreApi` with no change to core. The district is read from the postal
  code at the end of the elder's address: CoreApi's `postalSector` turned out to be core's own locality band
  (`TPY`, `North`), not a postal sector, and core does not return the postal code. Without one, `district` is
  empty and `areaName` is the band. The elder summary is gender, age and mobility, never a name or initial.
  `bookedBy` is "Family member" or "The elder": core does not return the relationship.
- **`Idempotency-Key`** is kept per provider for 24 hours in `report_provider_job_key`.

Not done: telling the family when a provider accepts or completes (the request's existing messages still say what
happens to it), suspending a provider does not hand its open jobs over at once (they run out at `respondBy`), and
rate limits (429) are the gateway's, which Phase 5 no longer builds.

## D17. The provider and the manager are offered the request together; the first to act has it

*Changed by D18: a manager approves the request first, and the primary caregiver and every matching provider are
offered it.*

*2026-10-17. Changes D7 and D16. Changes report (Section 4's service): needs Section 4's review.*

D7 and D16 offered a request to a provider first and gave it to a manager only once the provider declined or
let `respondBy` pass, up to a day later. Now, when the family approves and the primary caregiver is not free,
report books the unstaffed visit and alerts the managers as it always did, and offers the job to a provider at
the same time:

- **The provider accepts first:** report calls the visit off in the same request, and the manager's screen
  shows **PROVIDER ACCEPTED**. Settlement leaves the request alone though its visit is CANCELLED; the job
  completes it.
- **A manager puts a caregiver on the visit first:** the offer is withdrawn ("CareLink has staffed this job
  itself."), and the provider is sent `job.cancelled`. A later acceptance is 409 `JOB_CANCELLED`.
- **Who was first** is decided on the job's row: accepting, the manager's caregiver assignment and cancelling
  each lock it, so the second to arrive sees the first. Assigning after the provider accepted is 409
  `VALUE_ADDED_TAKEN_BY_PROVIDER`; the manager can still cancel the request, which tells the provider.
- **A visit staffed another way** (not through the Extra services screen) is found when the provider accepts:
  report reads the visit first, and withdraws the offer instead of accepting.
- **Declined or unanswered:** the job just closes. The manager has had the visit from the start, so nothing is
  handed over.
- **Offered at approval, not when the elder asks.** Until the family approves there is no booking; a provider
  could accept something the family then rejects. A family member's own request is approved as it is made.

Why: the family is served by whoever is ready first, and a provider's silence never costs the manager a day.
The trade-offs: a manager may start on a request a provider then takes (the screen shows the offer beside the
caregiver column), and accepting now calls visit, inside the transaction that accepts; if visit refuses, the
acceptance is refused and can be sent again with the same key.

## D18. A manager approves the request; then the primary caregiver, the providers and the manager may take it

*2026-10-18. Planned, not built: after the 10/21 rehearsal, which runs D17. Changes D7, D16 and D17. Changes report
(Section 4's service) and the caregiver app: needs Section 4's review and the caregiver app owner's.*

Today the family's approval books the visit at once: the primary caregiver gets it if free, and only otherwise is it
offered to one provider while a manager may staff it (D17). Now CareLink approves every request before anyone is
offered it, and the approved request is open to everyone who could do it, the first to take it having it:

1. **The family approves** (or a family member asks, which is their approval). The request becomes `APPROVED`, the
   state the enum has and nothing uses yet. No visit is booked and nobody is offered it. The managers are alerted
   that it awaits their approval.
2. **A manager approves it** (new: `POST /api/value-added-service-requests/{id}/approval`). report books the visit
   with nobody on it and the request becomes `DISPATCHED`. A manager who will not approve cancels it, through the
   existing cancellation (`BY_MANAGER`), which tells the family. Approving is refused once the request is too near
   its start to arrange (`VALUE_ADDED_SERVICE_TOO_LATE`, as for the family).
3. **It is open to, together:**
   - **the elder's primary caregiver**, if free for the whole of it (the check `chooseCaregiver` makes today). They
     are told by an inbox notification and see it in the caregiver app under open jobs, where they take it (new:
     `GET /api/caregiver/value-added-jobs`, `POST /api/caregiver/value-added-jobs/{requestId}/claim`). Taking it
     checks again that they are free, then puts them on the visit as a manager's assignment would. No caregiver is
     put on it without taking it.
   - **every active provider offering the service**, if the start is at least 5 hours away (D7's notice). Each gets
     its own job and `job.dispatched`.
   - **the manager**, who puts any free caregiver on it with the existing picker
     (`POST /api/value-added-service-requests/{id}/caregiver`).
4. **The first to take it has it.** Every other open offer is called off, and each provider is sent
   `job.cancelled`: "Another provider has taken this job." or "CareLink has staffed this job itself." A later
   accept, claim or assignment is refused with 409 (`JOB_CANCELLED`, `VALUE_ADDED_ALREADY_TAKEN`,
   `VALUE_ADDED_TAKEN_BY_PROVIDER`).
5. **Nobody takes it:** a provider's offer closes at its `respondBy` as now; the caregiver's and the manager's chance
   lasts until the start, when report's existing settlement closes the request and tells the family
   (`NOT_PROVIDED`).

**Who was first** is decided on the request's row, not the job's: there are now several jobs for one request.
Accepting, claiming, assigning and cancelling each lock the `value_added_service_request` row first, so the second
to arrive sees the first.

Why: CareLink decides what it commits to before anyone is sent out, and the family is served by whoever is ready
first, with no one waiting on another's silence. The trade-offs: every request waits for a manager, so a manager's
delay costs the providers time to answer, and a request near its start may reach no provider; a free primary
caregiver is no longer put on at once, so a request can go unstaffed that today would not have; and every matching
provider is sent every job, most of which they cannot win.

**To build** (in this order, each with its tests):

- **report, schema** (`V4`): `report_provider_job` unique on (request, provider) instead of request alone.
- **report, domain:** `ValueAddedServiceRequest.approveForManager` (`PENDING_APPROVAL` to `APPROVED`, family) and
  `dispatchByManager` (`APPROVED` to `DISPATCHED`); `cancelled()` also allowed from `APPROVED`. `ProviderJobs`
  answers a request's jobs as a list; `Providers.allFor(service)` replaces `nextFor`.
- **report, application:** `decideForFamily` and `requestForFamily` stop at `APPROVED`; a manager approval that
  books the visit, notifies the primary caregiver if free, and offers every provider; `ProviderJobDispatch` locks the
  request and withdraws every other offer when one is taken; caregiver open jobs and claim; `ProviderJobService
  .accept` locks the request and withdraws the other providers' jobs.
- **report, API:** the manager's approval, the caregiver's open jobs and claim, and the manager's request view
  showing `APPROVED` and every provider offered.
- **Open Platform docs:** `webhooks.md`, the new `job.cancelled` reason; `open-platform.yaml`, that a job may be
  offered to other providers too.
- **Frontend:** the manager's Extra services screen (awaiting approval, Approve, the providers offered), the
  family's request status ("Awaiting CareLink's approval"), the caregiver app's open jobs, and the portal's mock and
  README demo.
