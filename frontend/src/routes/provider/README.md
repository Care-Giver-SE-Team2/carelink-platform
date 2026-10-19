# CareLink Partner Portal　(PT01 / PT04)

**Owner:** Section 5 (notification)

This is the **PT04 provider workbench** of the brief and WBS, named the Partner Portal in the UI.

Where third-party providers join the CareLink Open Platform, at `/provider`. It is public (no
CareLink login) and talks **only** to the public provider API in
`features/provider-platform/api.ts`; `publicApiOnly.test.ts` fails the build if a file here
imports `shared/api`, the login or `RequireRole`.

In production an integrated provider works in **its own system**: CareLink sends it jobs by
webhook and its server answers through the job API. So this portal is CareLink's side of that
relationship, as developer portals are elsewhere:

| Page | What it's for |
|---|---|
| Apply | Joining (PT01); also creates the portal account: the contact email and a password |
| Status | Sign in; then the application and CareLink's decision |
| API credentials | The client ID and secret, shown once; afterwards a hint, how a server uses them, and Rotate secret |
| Webhooks | The endpoint and its events, the signing secret (rotate), Send test event, and CareLink's delivery log: every attempt, retries, failures, Resend |
| Sandbox | The job API tried by hand: get a token, accept, decline, complete. What the provider's server would do; it keeps PT04's onboard → accept → complete demonstrable without a partner system |
| Docs | Token, webhook verification and job calls in one page (a draft until the Open API contract) |

The header shows the provider and how its webhooks are doing; the manager console's Providers
screen shows the same health and can suspend a provider.

Design: the "provider workbench" handoff (v3, manager-console style), extended in the same
language for the portal pages. The controls are in `components/ui.tsx`, because the shared
components are sized for the manager console.

## What is real and what is mocked

`features/provider-platform/api.ts` is the portal's only way to CareLink. Run with the backend
(`scripts/run.sh all`, then `npm run dev`), it calls the Open API that notification serves
(`liveApi.ts`, docs/api/open-platform.yaml): applying, the portal, credentials and webhooks are
real, and real webhooks are sent and logged. Signing in and getting tokens are notification's
too: it issues the tokens itself, the same locally and in the cloud (docs/section5/decisions.md,
D15). The job calls (the Sandbox) are report's, which serves the job API with the same tokens
(D16). Every call is under `/api/open/v1`, which CloudFront sends to the services as it does all of
`/api`; locally the Vite proxy sends `/api/open/v1/jobs` to report on 8082 and the rest of
`/api/open` to notification on 8083.

Under Vitest, and with `VITE_OPEN_PLATFORM_MOCK=true`, every call is the mock's, in
localStorage, with the seeded providers below.

Providers sign in with the contact email and the password set when applying; notification keeps
only a bcrypt hash of it, and five wrong passwords in a row lock the sign-in for 15 minutes. Not built, and to settle with the contract: MFA,
password reset, team members, and approval emails (no email channel, docs/section5/decisions.md, D9).

## Demo

1. `/provider/apply`: submit an application, setting a password. You're signed in to the
   portal; Status shows it pending.
2. `/manager/providers` (signed in as a manager, in another tab): approve it.
3. `/provider/status`: **Check again**, then **View credentials →** (shown once) →
   **Continue**.
4. `/provider/webhooks`: **Send test event**; open the delivery. With the endpoint the
   provider gave, the attempts show what it answered; on the mock, the jobs' deliveries too.
5. Make a job: the provider must offer the service, and the elder's primary caregiver must be
   busy then. As a family member, book that service at least 5 hours ahead. report books the
   visit for a manager and offers the job to the provider at once (`job.dispatched` appears
   under Webhooks); the manager's Extra services screen shows "Offered to …" beside it. The
   first to act has it (docs/section5/decisions.md, D17).
6. `/provider/sandbox`: **Get access token** (filled in from step 3), open the job, **Accept
   job** (the address appears; the manager's screen shows **PROVIDER ACCEPTED**), then **Mark
   complete**; the family's request is completed. To show the 409 path, assign a caregiver to
   another offered request as the manager first, then accept it. On
   the mock, one new job is cancelled by the family as you accept it.
7. Back on `/manager/providers`: the provider's health, and **Suspend provider**: its
   webhooks stop, Status shows it, and the Sandbox gets 403 `provider_suspended`.

On the mock, a provider approved in the seed data, with a failed and a retrying webhook, for
skipping steps 1–3 (sign in on Status). These are mock values, valid only against the mock:

| | |
|---|---|
| Portal sign-in | `partners@brighthome.sg` / `brighthome-demo` |
| Client ID | `cl_prov_BrightHome9` |
| Client secret | `cs_demo_brighthome` (until you rotate it) |

Other seeded accounts: `alvin@quickclean.sg` / `quickclean-demo` (rejected),
`ops@kampungcare.sg` / `kampungcare-demo` and `daniel@freshcart.com.sg` / `freshcart-demo`
(pending).

To start the mock over, run `localStorage.clear()` in the browser console and reload.
