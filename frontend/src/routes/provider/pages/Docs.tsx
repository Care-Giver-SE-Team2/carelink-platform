import { ATTEMPT_TIMEOUT_SECONDS, MAX_ATTEMPTS, SIGNATURE_TOLERANCE_SECONDS, retryScheduleLabel } from '../../../features/provider-platform/webhooks'
import { Banner, SectionLabel } from '../components/ui'
import { WorkbenchShell } from '../components/WorkbenchShell'
import styles from './Docs.module.css'

const VERIFY = `import crypto from 'node:crypto'

// Express: keep the raw body, the signature is over the exact bytes CareLink sent.
app.post('/carelink', express.raw({ type: 'application/json' }), (req, res) => {
  const timestamp = req.get('X-CareLink-Timestamp')
  const signature = req.get('X-CareLink-Signature')          // "v1=<hex>"
  const expected = 'v1=' + crypto
    .createHmac('sha256', process.env.CARELINK_WEBHOOK_SECRET)
    .update(\`\${timestamp}.\${req.body}\`)
    .digest('hex')

  const fresh = Math.abs(Date.now() / 1000 - Number(timestamp)) <= ${SIGNATURE_TOLERANCE_SECONDS}
  const valid = signature?.length === expected.length &&
    crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expected))
  if (!fresh || !valid) return res.sendStatus(401)

  const deliveryId = req.get('X-CareLink-Delivery')
  if (alreadyProcessed(deliveryId)) return res.sendStatus(200)  // a retry: acknowledge, don't redo
  queueForProcessing(deliveryId, JSON.parse(req.body))
  res.sendStatus(200)                                           // answer fast, work later
})`

const TOKEN = `curl -X POST https://api.carelink.sg/api/open/v1/oauth2/token \\
  -u "$CARELINK_CLIENT_ID:$CARELINK_CLIENT_SECRET" \\
  -d "grant_type=client_credentials"

# → { "access_token": "…", "token_type": "Bearer", "expires_in": 3600,
#     "scope": "open-platform/jobs.read open-platform/jobs.write" }`

const ACCEPT = `curl -X POST https://api.carelink.sg/api/open/v1/jobs/JOB-24081/accept \\
  -H "Authorization: Bearer $TOKEN" \\
  -H "Idempotency-Key: $(uuidgen)"   # a new key per action; reuse it only to retry`

const ENDPOINTS: [string, string, string][] = [
  ['GET', '/api/open/v1/jobs?status=NEW&page=0', 'Jobs sent to you, newest first, with every status’s count'],
  ['GET', '/api/open/v1/jobs/{id}', 'One job; the address only once you’ve accepted it'],
  ['POST', '/api/open/v1/jobs/{id}/accept', 'Take the job, before its respondBy'],
  ['POST', '/api/open/v1/jobs/{id}/decline', '{ reason, note } — CareLink makes other arrangements'],
  ['POST', '/api/open/v1/jobs/{id}/complete', '{ actualStart, actualEnd, note } — when the work really started and ended'],
]

const ERRORS: [string, string][] = [
  ['401', 'Token missing, wrong or expired: get a new one'],
  ['403', 'Your provider account is suspended'],
  ['404', 'No such job, or it wasn’t sent to you'],
  ['400', 'The request is invalid, or the Idempotency-Key header is missing'],
  ['409', 'The job can no longer change, e.g. CareLink cancelled it first, or respondBy has passed'],
  ['422', 'The Idempotency-Key was used for another job or another action'],
]

/**
 * Docs: the integration in one page — get a token, receive and verify webhooks, act on jobs —
 * stating the same rules the platform follows (docs/api/open-platform.yaml, webhooks.md).
 */
export default function Docs() {
  return (
    <WorkbenchShell>
      <article className={styles.page}>
        <div className={styles.intro}>
          <h1 className={styles.title}>Integrating with CareLink</h1>
          <p className={styles.sub}>
            Your server connects to CareLink with its client credentials, receives jobs by webhook and answers through the job API. Your
            staff keep working in your own system.
          </p>
        </div>

        <Banner tone="warning" title="Draft">
          The full contract will be published as an OpenAPI spec and a webhook guide. Until then, this page is the reference.
        </Banner>

        <section className={styles.section} aria-labelledby="docs-token">
          <SectionLabel>Step 1</SectionLabel>
          <h2 id="docs-token" className={styles.heading}>
            Get an access token
          </h2>
          <p>
            Exchange your client ID and secret (on <em>API credentials</em>) for a token with the OAuth2 client-credentials grant. Tokens
            last an hour; ask for a new one when yours runs out. Keep the secret on your server.
          </p>
          <pre className={styles.code}>{TOKEN}</pre>
        </section>

        <section className={styles.section} aria-labelledby="docs-webhooks">
          <SectionLabel>Step 2</SectionLabel>
          <h2 id="docs-webhooks" className={styles.heading}>
            Receive webhooks
          </h2>
          <p>
            CareLink POSTs <code>job.dispatched</code> when it sends you a job and <code>job.cancelled</code> when one is called off. Every
            request carries:
          </p>
          <dl className={styles.headersList}>
            <dt>X-CareLink-Delivery</dt>
            <dd>The delivery ID. The same on every retry: process each ID once.</dd>
            <dt>X-CareLink-Event</dt>
            <dd>The event name.</dd>
            <dt>X-CareLink-Timestamp</dt>
            <dd>When it was signed, in Unix seconds. Refuse anything older than {SIGNATURE_TOLERANCE_SECONDS / 60} minutes.</dd>
            <dt>X-CareLink-Signature</dt>
            <dd>
              <code>v1=</code> and the hex HMAC-SHA256 of <code>timestamp.body</code>, keyed with your signing secret.
            </dd>
          </dl>
          <p>
            Answer with any 2xx within {ATTEMPT_TIMEOUT_SECONDS} seconds. Anything else, or no answer, is retried after {retryScheduleLabel()}
            : {MAX_ATTEMPTS} attempts in all. After that the delivery is marked failed; you can resend it from <em>Webhooks</em>. Your
            receiver must be idempotent.
          </p>
          <pre className={styles.code}>{VERIFY}</pre>
        </section>

        <section className={styles.section} aria-labelledby="docs-jobs">
          <SectionLabel>Step 3</SectionLabel>
          <h2 id="docs-jobs" className={styles.heading}>
            Act on jobs
          </h2>
          <p>
            Send the token as <code>Authorization: Bearer</code>. Every POST takes an <code>Idempotency-Key</code>: if a request fails
            in transit, retry it with the same key and CareLink applies it once.
          </p>
          <table className={styles.table}>
            <tbody>
              {ENDPOINTS.map(([method, path, what]) => (
                <tr key={path}>
                  <td className={styles.method}>{method}</td>
                  <td className={styles.path}>{path}</td>
                  <td>{what}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <pre className={styles.code}>{ACCEPT}</pre>
        </section>

        <section className={styles.section} aria-labelledby="docs-errors">
          <SectionLabel>Reference</SectionLabel>
          <h2 id="docs-errors" className={styles.heading}>
            Errors
          </h2>
          <table className={styles.table}>
            <tbody>
              {ERRORS.map(([status, meaning]) => (
                <tr key={status}>
                  <td className={styles.method}>{status}</td>
                  <td>{meaning}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </article>
    </WorkbenchShell>
  )
}
