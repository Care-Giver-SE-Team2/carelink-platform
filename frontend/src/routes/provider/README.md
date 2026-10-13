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

## Until the Open API exists

`features/provider-platform/mockPlatform.ts` stands in for the platform, in localStorage, so
the manager console and the portal share it across tabs. Webhooks are not really sent: each
delivery records the attempts CareLink would make on the retry schedule in `webhooks.ts`.
Replace the bodies in `api.ts` with real calls once the Phase 1 contract is settled.

Providers sign in with the contact email and the password set when applying; everything after
Apply needs a session (8 hours, kept in sessionStorage). The mock keeps a salted stand-in hash,
never the password. Not built, and to settle with the contract: MFA, password reset, team
members, and approval emails (which notification would send).

## Demo

1. `/provider/apply`: submit an application, setting a password. You're signed in to the
   portal; Status shows it pending.
2. `/manager/providers` (signed in as a manager, in another tab): approve it. Approving also
   dispatches four jobs to it, standing in for report's dispatch.
3. `/provider/status`: **Check again**, then **View credentials →** (shown once) →
   **Continue**.
4. `/provider/webhooks`: the deliveries those jobs sent. **Send test event**; open a delivery.
5. `/provider/sandbox`: **Get access token** (filled in from step 3), open a job, **Accept
   job**, then **Mark complete**. One new job is cancelled by the family as you accept it, to
   show the 409 path.
6. Back on `/manager/providers`: the provider's health, and **Suspend provider**; the Sandbox
   then gets 403.

A provider approved in the seed data, with a failed and a retrying webhook, for skipping steps
1–3 (sign in on Status). These are mock values, valid only against the mock:

| | |
|---|---|
| Portal sign-in | `partners@brighthome.sg` / `brighthome-demo` |
| Client ID | `cl_prov_BrightHome9` |
| Client secret | `cs_demo_brighthome` (until you rotate it) |

Other seeded accounts: `alvin@quickclean.sg` / `quickclean-demo` (rejected),
`ops@kampungcare.sg` / `kampungcare-demo` and `daniel@freshcart.com.sg` / `freshcart-demo`
(pending).

To start the mock over, run `localStorage.clear()` in the browser console and reload.
