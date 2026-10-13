import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getCredentialsSummary, rotateClientSecret } from '../../../features/provider-platform/api'
import { formatLong } from '../../../features/provider-platform/format'
import type { IssuedCredentials } from '../../../features/provider-platform/types'
import { RevealSecret } from '../components/RevealSecret'
import { Banner, Button, Checkbox, CopyField, Dialog, Facts, SectionLabel, WarningIcon } from '../components/ui'
import { WorkbenchShell } from '../components/WorkbenchShell'
import { useWorkbench } from '../workbenchContext'
import styles from './Credentials.module.css'

const LEAVE_WARNING =
  "Your credentials are shown only once. If you leave before storing them, you'll have to rotate them, which revokes these."

/**
 * API credentials: straight after approval, the one-time view of the client ID, client secret
 * and webhook signing secret; afterwards, the client ID, when the secret was issued, how a
 * server uses them, and Rotate secret. The signing secret is managed on Webhooks.
 */
export default function Credentials() {
  const { credentials } = useWorkbench()
  return <WorkbenchShell>{credentials ? <FirstView credentials={credentials} /> : <Manage />}</WorkbenchShell>
}

function FirstView({ credentials }: { credentials: IssuedCredentials }) {
  const { setCredentials, setConnectPrefill, setLeaveGuard } = useWorkbench()
  const [stored, setStored] = useState(false)

  useEffect(() => {
    setLeaveGuard(stored ? null : LEAVE_WARNING)
  }, [stored, setLeaveGuard])

  function proceed() {
    setConnectPrefill({ clientId: credentials.clientId, clientSecret: credentials.clientSecret })
    setLeaveGuard(null)
    setCredentials(null)
  }

  return (
    <div className={styles.first}>
      <h1 className={styles.title}>Your credentials</h1>
      <div className={styles.warning} role="alert">
        <WarningIcon />
        <div className={styles.warningText}>
          <div className={styles.warningTitle}>This is shown only once. Store it securely now.</div>
          <div className={styles.warningBody}>
            CareLink keeps only a hash of your secrets. If you lose them, you'll have to rotate them, which revokes these.
          </div>
        </div>
      </div>
      <div className={styles.values}>
        <CopyField label="Client ID" value={credentials.clientId} />
        <CopyField label="Client secret" value={credentials.clientSecret} />
        <CopyField
          label="Webhook signing secret"
          value={credentials.webhookSecret}
          helper="Use this to verify the X-CareLink-Signature header (HMAC-SHA256) on every webhook."
        />
      </div>
      <div className={styles.confirm}>
        <Checkbox checked={stored} onChange={setStored}>
          I've stored all three values somewhere safe.
        </Checkbox>
        <div>
          <Button variant="primary" disabled={!stored} onClick={proceed}>
            Continue
          </Button>
        </div>
      </div>
    </div>
  )
}

function Manage() {
  const { portal } = useWorkbench()
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const [rotating, setRotating] = useState(false)
  const [rotated, setRotated] = useState<string | null>(null)
  const [failure, setFailure] = useState<string | null>(null)

  const summary = useQuery({
    queryKey: ['provider', 'credentials', portal?.token],
    queryFn: () => getCredentialsSummary(portal!),
    enabled: portal !== null,
    retry: false,
  })

  async function rotate() {
    setRotating(true)
    setFailure(null)
    try {
      const { clientSecret } = await rotateClientSecret(portal!)
      setRotated(clientSecret)
      await queryClient.invalidateQueries({ queryKey: ['provider', 'credentials'] })
    } catch (err) {
      setFailure(err instanceof Error ? err.message : "Couldn't reach CareLink.")
    } finally {
      setRotating(false)
      setConfirming(false)
    }
  }

  const data = summary.data

  return (
    <div className={styles.manage}>
      <div className={styles.intro}>
        <h1 className={styles.title}>API credentials</h1>
        <p className={styles.sub}>
          Your server exchanges these for access tokens to the job API. Keep the secret on your server: never in a browser or mobile app.
        </p>
      </div>

      {failure && (
        <Banner tone="danger" title="Couldn't rotate the secret" role="alert">
          {failure}
        </Banner>
      )}

      {rotated && <RevealSecret label="Client secret" value={rotated} onDone={() => setRotated(null)} />}

      {summary.isError ? (
        <Banner tone="danger" title="Couldn't load your credentials" role="alert">
          {summary.error.message}
        </Banner>
      ) : !data ? (
        <p className={styles.loading}>Loading your credentials…</p>
      ) : (
        <>
          <section className={styles.card} aria-labelledby="credentials-heading">
            <div className={styles.cardHead}>
              <SectionLabel id="credentials-heading">Client credentials</SectionLabel>
            </div>
            <div className={styles.cardBody}>
              <CopyField label="Client ID" value={data.clientId} />
              <div className={styles.secretRow}>
                <div className={styles.secretText}>
                  <span className={styles.secretLabel}>Client secret</span>
                  <span className={styles.secretValue}>cs_live_••••••••••••{data.secretHint}</span>
                  <span className={styles.secretMeta}>Issued {formatLong(data.secretIssuedAt)}</span>
                </div>
                <Button onClick={() => setConfirming(true)} disabled={rotated !== null}>
                  Rotate secret
                </Button>
              </div>
            </div>
          </section>

          <section className={styles.card} aria-labelledby="token-heading">
            <div className={styles.cardHead}>
              <SectionLabel id="token-heading">Getting a token</SectionLabel>
            </div>
            <div className={styles.cardBody}>
              <Facts
                className={styles.facts}
                rows={[
                  { label: 'Endpoint', value: data.tokenEndpoint, mono: true },
                  { label: 'Grant type', value: 'client_credentials', mono: true },
                  { label: 'Scope', value: data.scope, mono: true },
                  { label: 'Lifetime', value: '60 minutes; ask for a new token when it runs out' },
                ]}
              />
              <pre className={styles.example}>{`curl -X POST https://api.carelink.sg/oauth2/token \\
  -u "${data.clientId}:$CARELINK_CLIENT_SECRET" \\
  -d "grant_type=client_credentials&scope=${data.scope.replace(' ', '%20')}"`}</pre>
              <p className={styles.note}>
                Try it without writing code in the <Link to="/provider/sandbox">Sandbox</Link>. The webhook signing secret is on{' '}
                <Link to="/provider/webhooks">Webhooks</Link>.
              </p>
            </div>
          </section>
        </>
      )}

      {confirming && (
        <Dialog
          title="Rotate client secret?"
          onClose={() => setConfirming(false)}
          footer={
            <>
              <Button onClick={() => setConfirming(false)} disabled={rotating}>
                Cancel
              </Button>
              <Button variant="danger" busy={rotating && 'Rotating…'} onClick={() => void rotate()}>
                Rotate secret
              </Button>
            </>
          }
        >
          <p className={styles.dialogText}>
            Your current secret stops working immediately, so update your server with the new one straight away. Access tokens already
            issued keep working until they expire.
          </p>
        </Dialog>
      )}
    </div>
  )
}
