# Manager API failures found during CSP acceptance

These failures reproduced against the existing public release before any header
policy was attached. They are application defects, not CSP rejections.

1. Provider reads and manager decisions passed `/api/...` to the shared HTTP
   client, which already prepends `/api`. Production therefore requested
   `/api/api/provider-applications` and received 500. Remove the duplicate prefix
   from all six calls in the same adapter. The regression test runs in production
   mode through the real shared client and verifies read and decision URLs;
   regular mock-mode UI tests previously hid this issue. Decision regressions
   use mocked HTTP responses and never approve/reject/suspend real providers.
2. `GET /api/credentials/visits-at-risk` failed when a booked standalone visit
   had no care plan. `VisitsAtRisk` queried its immutable requirements Map with
   a null key; the deployed stack trace identifies `MapN.get` and `count`.
   Ignore bookings without a care plan when matching plan-required credentials.
   The new regression contains both a standalone booking and an at-risk planned
   booking: the planned booking must still count as one, rather than masking the
   error by returning an empty/default result for the entire request.

Both tests were observed failing before the fixes. The frontend regression
reported six doubled prefixes; the domain regression threw the same null-pointer
exception as staging. After the fixes, the full frontend suite and the core test
reactor pass. Live public rechecks follow the normal, CI-verified deployment.
No database migration, data repair, endpoint permission change, demo creation or
infrastructure recreation is required. PDF upload/download remains deferred.
