# Strict CSP candidate — PR review only

**Not enabled.** The website remains on the previously accepted `72d4800` release
and the compatibility policy `fd81c784-8d9c-429e-8ca5-285306987ee7`. This change does
not edit that policy file, attach/update any AWS policy, merge itself, change a
workflow or deployment flag, create demo, or schedule cleanup. A read-only comparison
confirmed the entire live CloudFront distribution configuration is unchanged.

## The two remaining findings

The accepted public release's controlled scan reported `10055-4` (wildcard media
sources) and `10055-6` (`style-src unsafe-inline`). Add a **separate candidate**,
`deploy/cloudfront/security-headers-strict.json`, with a distinct policy name so the
existing policy can remain available for rollback.

| Directive | Current compatibility policy | Strict candidate |
| --- | --- | --- |
| `style-src` | self, unsafe-inline, Google CSS | `'self'` |
| `style-src-attr` | inherits the inline exception | `'none'` |
| `font-src` | self, Google Fonts | `'self'` |
| `img-src` | self, data, blob, any HTTPS host | `'self' data: blob:` |
| `frame-src` | self, blob, any HTTPS host | `'self' blob:` |

Script and connection sources remain `'self'`; no nonce, hash exception, external
script source or eval permission is added. All other security headers, framing
protection and origin Server-removal settings match the current policy.

## Why this does not require rewriting the UI

The client-rendered React 19.2.8 runtime assigns individual CSS properties and
custom properties (`style[name] = value`, `style.setProperty(...)`). CSP blocks
HTML style attributes, `setAttribute('style', ...)`, `cssText`, and inline style
blocks, but it permits trusted JavaScript to set individual CSS properties. A
React `style={...}` prop in this SPA is therefore different from an HTML inline
style. This distinction was checked against the installed production React DOM
runtime and real browser rendering, rather than assuming the two behave alike.

Existing consumers include roster grid columns/custom properties, weekly load
bars, report bars, modal widths, table columns, provider step counts and elder
layout order. No application component, API adapter or dependency is changed.
This conclusion must be revisited if the application later introduces SSR,
HTML-injected styles, CSS-in-JS style blocks or a different rendering mechanism.

Current loaded pictures, icons and fonts are bundled on the same origin. Intake
CSS uses data SVG backgrounds, so `data:` is retained for images. Certificate
metadata currently supplies no real uploaded scan; `CertificateScan` supports
same-origin image/iframe navigation. `blob:` remains an explicit local preview
allowance. **No arbitrary external certificate host is approved by this PR.** A
future signed S3 URL or external document host requires a narrowly named origin
and a new review. The real PDF workflow is still deferred, not claimed as tested.
Ordinary external links (for example maps) are navigation, not `connect-src` fetches.

## Verification

Sanitized results and source hashes are in
`docs/platform/evidence/csp-strict-20261017.json`; raw local screenshots, role traces
and ZAP reports remain in the local review evidence folder.

- Production build passed; no application build inputs changed. Python offline
  guard tests passed, including refusal of foreign, missing or mixed policy IDs.
- Chrome Incognito passed the production-build fixture on `/`, `/apply`,
  `/provider/apply`, `/elder/register`: all seven font faces, local assets,
  individual CSS properties, data images, same-origin API/frame and blob frame
  navigation worked; unexpected CSP violations were zero. Inline scripts,
  HTML style attributes/style blocks, foreign fetch/image/frame probes were blocked.
- Four authenticated roles passed **13 page/interaction checks** under the exact
  strict headers in a loopback production-build fixture with a fixed, read-only
  staging API relay: manager home/day roster/week roster/providers/certifications/
  reports and a modal opened then cancelled; family home/weekly summary;
  caregiver schedule/work pack; elder home/confirmation read. The server rejects
  all non-GET business requests. Only login/logout POSTs are allowed; no approval,
  rejection, registration, check-in or other business submission was made.
- The relay adapts Secure/Domain cookie attributes only for local HTTP testing;
  it does not alter production cookies. This is local strict-policy compatibility
  evidence, **not proof that the live policy has already been tightened**.
- The pinned ZAP image scanned only the local unauthenticated static fixture.
  Neither `10055` finding was reported: 0 high, 0 medium, 0 low, 2 informational
  findings. No scan rules were suppressed. This is not a new production scan,
  authenticated security test or proof that all vulnerabilities are absent.
- The in-app automation browser did **not** pass the full fixture: it produced an
  empty-source `style-src-elem` event at line 135 and did not complete the blob
  frame probe. The same style event was reproduced on a minimal page containing
  no application code/CSS, so it is not evidence that React generated an inline
  style block. It is retained as an environment-specific limitation, not silently
  removed from the results. Chrome Incognito is the verified browser for this review;
  native PDF rendering and every supported browser still need their own acceptance.

Reproduce the fake-API fixture without AWS credentials:

```sh
(cd frontend && npm ci && npm run build)
python3 -m unittest discover -s scripts/tests -q
python3 scripts/tests/security_headers_fixture.py \
  --port 15176 --policy deploy/cloudfront/security-headers-strict.json
```

Open `http://127.0.0.1:15176/` in Chrome. The summary should show PASS. Negative
probes deliberately generate violations; unexpected application violations must
be zero. The normal fixture default continues to use the compatibility policy.
Use a production build: Vite dev mode injects style elements/HMR and is not an
acceptance environment for this production-only policy.

## Future activation and rollback — requires a separate approved action

1. Review/merge separately. This PR has no automatic header activation, including
   after merge. Keep the normal main CI/CD pipeline and both deployment flags.
2. Confirm the currently served frontend still uses local fonts and the reviewed
   client renderer. Re-run local authenticated compatibility checks against that
   version; do not reuse stale evidence after unrelated frontend changes.
3. Save a fresh distribution/ETag and rollback copy. Create the **new policy only**
   after approval; leave the existing compatibility policy unchanged. Verify its
   complete contents and that no other distribution uses it.
4. Prepare the two-reference transition offline. The candidate ID must be the
   real ID returned by the separately approved creation; the command below does
   not call AWS or submit an update:

   ```sh
   python3 scripts/prepare-security-headers.py \
     before-distribution.json "$REVIEWED_STRICT_POLICY_ID" proposed-update.json \
     --expected-current-policy-id fd81c784-8d9c-429e-8ca5-285306987ee7
   ```

   Both default frontend and `/assets/*` must have the exact reviewed old ID (or
   both already have the new ID for an idempotent recheck). A missing, foreign or
   mixed attachment fails closed. The original no-replacement default remains.
   API behaviors, origins, WAF, caching, SPA routing and domain are preserved.
5. Only after separate authorization, apply the reviewed ETag-bound change and
   record the references in authoritative public-site IaC. Wait for deployment;
   verify fonts, responsive layout, role APIs, grids, bars, modal open/cancel and
   deep links on the same public URL. Run one separately authorized public passive
   scan. Do not enable the repository scan flag or weaken fail-on-WARN behavior.
6. On regression, get the **current** distribution and ETag; switch only those
   two references back to the saved compatibility ID. Keep unrelated intervening
   changes and the environment intact. HSTS is unchanged by this transition.
   Reverting a Git commit alone does not revert a separately attached AWS policy.

## Sources

- [MDN: style-src-attr and direct CSS property assignments](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/style-src-attr)
- [W3C CSP Level 3](https://www.w3.org/TR/CSP3/)
- [ZAP CSP scan rule and alert variants](https://www.zaproxy.org/docs/alerts/10055/)
