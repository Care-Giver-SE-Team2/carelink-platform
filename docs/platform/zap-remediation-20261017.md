# ZAP medium/low remediation — review only

Status: **prepared and locally tested, not deployed**. This PR does not attach a
CloudFront policy, change IAM, merge itself, enable scanning, create demo, or schedule
cleanup. The existing staging URL and automatic main deployment remain in place.

## Findings addressed

The original 2026-10-17 restricted passive scan reported 0 high, 3 medium and 3 low
alert types. The following are proposed fixes, **not a clean live rescan result**.

| Risk / ZAP ID | Observed finding | Fix prepared in this PR | When it becomes effective |
| --- | --- | --- | --- |
| Medium / 10038-1 | Missing CSP on HTML | Enforced CSP in `deploy/cloudfront/security-headers.json` | Separately reviewed policy attachment |
| Medium / 10020-1 | Missing anti-framing protection | `frame-ancestors 'none'` and `X-Frame-Options: DENY` | Policy attachment |
| Medium / 90003 | Google Fonts stylesheet has no SRI | Bundle the same Instrument Sans 400/500/600/700 and IBM Plex Mono 400/500/600 faces; remove both HTML link and landing CSS import | Normal frontend release after PR approval/merge |
| Low / 10036-2 | `Server: AmazonS3` | Remove `Server` via response headers policy | Policy attachment |
| Low / 10035-1 | Missing HSTS | `max-age=86400`, no subdomains or preload | Policy attachment; browsers must first receive HTTPS |
| Low / 10021 | Missing nosniff | `X-Content-Type-Options: nosniff`; publisher MIME regression test | Policy attachment |

`AmazonS3` identifies a product, not a numeric server version. Missing CSP alone is
not evidence that exploitable XSS was found. Informational alerts about bundled
comments, SPA coverage and cache review are not silently reclassified as fixed.

## CSP compatibility choices

- `script-src 'self'`: production Vite scripts are external hashed local assets.
  No inline script or `unsafe-eval` permission is introduced.
- `style-src 'self' 'unsafe-inline' https://fonts.googleapis.com`: existing React
  style attributes control grid columns, widths, modal sizes and layouts. This
  deliberate style exception avoids breaking those pages. It does not allow
  inline JavaScript. A stricter nonce/style refactor is outside this change.
- `font-src 'self' https://fonts.gstatic.com`: new releases use self-hosted files.
  The exact Google stylesheet/font origins remain allowed for the previous
  frontend during rollout/rollback. No third-party script host is allowed.
- `connect-src 'self'`: current session APIs use `/api`; the Partner Portal's
  production API base is empty/same-origin. The publication build does not set
  `VITE_OPEN_PLATFORM_API_URL`. A future external API base needs an explicit CSP
  review, not a wildcard added to this policy.
- `img-src 'self' data: blob: https:` and `frame-src 'self' blob: https:` retain
  CSS data images and the certificate viewer's URL-based images/PDF frames.
  Certificate URLs currently have no fixed external-host contract and mock scan
  URLs are null. HTTPS media/frame permissions are intentionally broader than
  scripts/connections; narrow them when the upload host contract is implemented.
- `frame-ancestors 'none'` prevents *other pages embedding the application*;
  `frame-src` separately controls previews *inside the application*. The API
  behaviors do not receive this policy, including any API-served certificate.
- `object-src 'none'` is compatible with the existing `<iframe>` viewer; the
  application does not use `<object>` or `<embed>`.
- HSTS starts at one day and does not opt the domain into preload. Removing the
  policy does not instantly undo a browser's cached HSTS. This header does not
  change the existing private CloudFront-to-ALB HTTP origin connection.

The font packages are exact dependencies with lockfile integrity hashes. Their
SIL Open Font License files are included under `frontend/public/licenses/` and in
the published static artifact. Google Fonts CSS responses can vary by client;
self-hosting avoids relying on a fragile hardcoded SRI hash for dynamic CSS.

## Local verification

Recorded results: 1,235 frontend tests and 66 Python tests passed; production
build and lint passed (lint retains five pre-existing warnings). AWS CLI offline
schema validation accepted the response headers JSON. Browser checks passed for
`/`, `/apply`, and `/provider/apply`: all seven font faces loaded, unexpected CSP
violations were zero, and both negative probes were blocked. The fake-login 401
appeared correctly in the landing page. Source hashes and per-route results are
in `docs/platform/evidence/zap-remediation-csp.json`. These are local results,
not evidence that live findings have already disappeared.

Commands (no AWS login required):

```sh
cd frontend
npm ci
npm run lint
npm run test:coverage
npm run build
cd ..
python3 -m unittest discover -s scripts/tests -q
python3 scripts/tests/security_headers_fixture.py
```

Open `http://127.0.0.1:15174/` in a browser. This is a loopback-only production-build
fixture with the actual proposed policy. It has **no proxy and no AWS calls**.
All `/api` responses are local fakes. Its extra probe script is not part of the
application build and no deployment workflow starts it.

The displayed JSON checks all seven font faces, local script/CSS/image/font
loading, a same-origin API response, dynamic inline styles, same-origin iframe
navigation, and blocking of inline script/foreign fetch. The negative probes
deliberately cause two CSP violations; unexpected application violations must be
zero. A local fake login returns 401 to confirm the request/error path without
authenticating against staging.

**Limits:** this is not an authenticated staging acceptance test. Native PDF
rendering, real uploaded certificate URLs, actual role-specific data, browser
extensions and every supported browser are not proven by the local fixture.
Keep them as explicit activation gates. Existing component/API tests complement
the browser check; jsdom tests alone do not enforce CSP.

## Activation plan — not executed by this PR

No existing CI/CD workflow or publisher gains CloudFront configuration writes.
Merging this PR would deploy bundled fonts through the existing frontend CD,
but would **not** attach the header policy. Header rollout needs a separate
approved change and authenticated role with the existing scoped permissions.
Do not expand IAM just to make a command succeed.

1. Review the PR, its tests and the policy. After separate approval to release,
   let the existing CI/CD publish the bundled fonts. Check the public version
   SHA and three-role login/read acceptance. No infrastructure recreation.
2. Before attaching CSP, test a real certificate image/PDF and authenticated
   manager/family/caregiver workflows under these exact headers in a controlled
   browser session. A report-only observation stage may help, but is not the
   final enforced remediation and must not be reported as fixing missing CSP.
3. Create the **policy only**, using the reviewed JSON. Save its ID. Export the
   existing distribution with `get-distribution`, including its ETag, and save
   an immutable rollback copy. These are future operator steps, not PR jobs:

   ```sh
   aws --profile carelink cloudfront create-response-headers-policy \
     --response-headers-policy-config file://deploy/cloudfront/security-headers.json
   aws --profile carelink cloudfront get-distribution --id "$DISTRIBUTION_ID" \
     > before-distribution.json
   python3 scripts/prepare-security-headers.py \
     before-distribution.json "$REVIEWED_POLICY_ID" proposed-update.json
   ```

   The Python script is **offline preparation only**. It checks the existing
   staging domain, exact S3 origin, HTTPS behavior and ETag; refuses replacement
   of a different attached policy; and changes only `ResponseHeadersPolicyId`
   on the default frontend and `/assets/*` behaviors. `/api`, `/api/*`, WAF,
   origins, viewer-request SPA function, routing, caching and domain are preserved.
4. Review the complete configuration diff. Record the two policy references in
   the authoritative existing public-site IaC before any subsequent Terraform
   apply, to prevent drift reverting the fix. That local IaC is not currently
   tracked on main; this PR deliberately does not copy an entire AWS stack into
   the application release or apply an unreviewed Terraform plan.
5. Only after approval, a separate operator may submit the prepared update using
   its ETag, wait for CloudFront deployment, and verify HTML, JS, CSS, fonts,
   SPA deep links, version SHA, authentication, reads and certificate previews.
   Do not substitute a stale distribution export if the ETag rejects the update.
6. Run the same narrowly scoped ZAP acceptance scan under separate authorization.
   New warnings (including CSP style/media tradeoffs) remain findings to review;
   do not suppress them or change the fail-on-WARN gate to produce green output.

Rollback: export the **current** distribution and current ETag; restore only the
two original policy references from the saved pre-change configuration, retaining
any unrelated intervening changes. Do not submit an old entire configuration.
Keep old hashed assets available as the existing publisher does. Font-only
rollback follows the existing release path. No cleanup of the environment is
part of this procedure.

## References

- [AWS response headers policies](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/understanding-response-headers-policies.html)
- [AWS: removing the Server header](https://repost.aws/knowledge-center/cloudfront-remove-response-headers)
- [Fontsource installation and fixed font weights](https://fontsource.org/docs/getting-started/install)
