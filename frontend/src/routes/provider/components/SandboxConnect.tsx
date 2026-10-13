import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../../features/provider-platform/errors'
import { minutesLeft } from '../../../features/provider-platform/format'
import { useWorkbench } from '../workbenchContext'
import { Button, Field, LinkButton, TextInput } from './ui'
import styles from './SandboxConnect.module.css'

/**
 * The Sandbox's token step: client ID and secret in, access token out (OAuth2 client
 * credentials), as the provider's server would do it. Connected, it shrinks to one line with
 * Refresh token and Disconnect. Starts filled in from the credentials just collected, if any.
 */
export function SandboxConnect() {
  const { token, connection, connect, disconnect, now, connectPrefill, expired, lastClientId } = useWorkbench()
  const [clientId, setClientId] = useState(connectPrefill?.clientId ?? lastClientId ?? '')
  const [clientSecret, setClientSecret] = useState(connectPrefill?.clientSecret ?? '')
  const [showSecret, setShowSecret] = useState(false)
  const [requesting, setRequesting] = useState(false)
  const [failure, setFailure] = useState<Error | null>(null)

  async function submit(e?: FormEvent) {
    e?.preventDefault()
    setRequesting(true)
    setFailure(null)
    try {
      await connect(clientId, clientSecret)
    } catch (err) {
      setFailure(err instanceof Error ? err : new Error(String(err)))
    } finally {
      setRequesting(false)
    }
  }

  if (connection === 'connected' && token) {
    return (
      <section className={styles.bar} aria-label="Sandbox connection">
        <span className={styles.state}>
          <span className={styles.on} aria-hidden="true" />
          Connected
        </span>
        <span className={styles.detail}>
          {token.clientId} · {token.scope} · expires in {minutesLeft(token.expiresAt, now)} min
        </span>
        <span className={styles.actions}>
          {clientSecret && (
            <LinkButton onClick={() => void submit()} disabled={requesting}>
              {requesting ? 'Refreshing…' : 'Refresh token'}
            </LinkButton>
          )}
          <LinkButton onClick={disconnect}>Disconnect</LinkButton>
        </span>
      </section>
    )
  }

  const invalid = failure instanceof ApiError && failure.status === 401
  const suspended = failure instanceof ApiError && failure.status === 403

  return (
    <section className={styles.connect} aria-label="Sandbox connection">
      <div className={styles.head}>
        <div className={styles.title}>{expired ? 'Access token expired' : 'Get an access token'}</div>
        <div className={styles.sub}>
          {expired
            ? 'Get a new one to carry on.'
            : 'Exchange your client ID and secret for a token (OAuth2 client credentials), as your server will.'}
        </div>
      </div>
      <form className={styles.form} onSubmit={submit}>
        <Field label="Client ID" htmlFor="sandbox-client-id">
          <TextInput
            id="sandbox-client-id"
            mono
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            disabled={requesting}
            placeholder="Paste client ID"
            autoComplete="off"
          />
        </Field>
        <Field label="Client secret" htmlFor="sandbox-client-secret">
          <div className={styles.secret}>
            <TextInput
              id="sandbox-client-secret"
              mono
              type={showSecret ? 'text' : 'password'}
              value={clientSecret}
              onChange={(e) => setClientSecret(e.target.value)}
              disabled={requesting}
              invalid={invalid}
              placeholder="Paste client secret"
              autoComplete="off"
            />
            <LinkButton className={styles.show} onClick={() => setShowSecret((s) => !s)} aria-pressed={showSecret}>
              {showSecret ? 'Hide' : 'Show'}
            </LinkButton>
          </div>
        </Field>
        <Button type="submit" variant="primary" busy={requesting && 'Requesting token…'} disabled={!clientId.trim() || !clientSecret.trim()}>
          Get access token
        </Button>
      </form>
      {failure && (
        <div className={styles.error} role="alert">
          <div className={styles.errorTitle}>
            <span className={styles.errorDot} aria-hidden="true" />
            {invalid ? 'Error: invalid credentials' : suspended ? 'Your provider account is suspended' : "Couldn't reach CareLink"}
          </div>
          <div className={styles.errorBody}>
            {invalid ? (
              <>
                CareLink returned <code className={styles.code}>401 invalid_client</code>. Check the ID and secret on API credentials.
              </>
            ) : suspended ? (
              'CareLink refuses tokens while your account is suspended. Contact CareLink.'
            ) : (
              failure.message
            )}
          </div>
        </div>
      )}
      {connection === 'error' && !failure && <p className={styles.sub}>The last token request failed.</p>}
    </section>
  )
}
