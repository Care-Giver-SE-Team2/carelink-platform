import { useState } from 'react'
import type { ReactNode } from 'react'
import { Button, Callout, ConfirmDialog, Eyebrow, MetaText, Tag } from '../../../shared/components/ui'
import type { Provider } from '../data/providerApplications'
import { formatReceived } from '../lib/applications'
import { healthTag, listLabel, providerRef } from '../lib/providers'
import styles from './ProviderPanel.module.css'

/**
 * The right-hand panel of the Providers screen for an approved provider: who it is, what it
 * offers, how it reaches the Open API, and how its integration is doing — the last
 * seven days' webhook deliveries and its jobs by state. Suspend stops the provider (its tokens
 * are refused, so it can't see or act on jobs) until Reinstate. `onSuspend`/`onReinstate` do
 * the request and reject with the error to show.
 */
export function ProviderPanel({
  provider,
  onSuspend,
  onReinstate,
}: {
  provider: Provider
  onSuspend: () => Promise<void>
  onReinstate: () => Promise<void>
}) {
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const tag = healthTag(provider)
  const { health, jobs } = provider

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
      setConfirming(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save this change.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <aside className={styles.panel} aria-label="Provider detail">
      <header className={styles.header}>
        <Eyebrow wide>Provider · {providerRef(provider.providerId)}</Eyebrow>
        <h2 className={styles.title}>{provider.organisationName}</h2>
        <div className={styles.status}>
          <Tag compact tone={tag.tone} solid={provider.suspended}>
            {tag.label}
          </Tag>
          <MetaText>
            {provider.suspended
              ? 'Tokens refused; no jobs or webhooks until reinstated.'
              : health.failed > 0
                ? 'Some webhooks never reached its endpoint.'
                : 'Can be dispatched jobs through the Open API.'}
          </MetaText>
        </div>
        <MetaText tone="faint" className={styles.meta}>
          approved {formatReceived(provider.approvedAt)} from application {provider.reference}
        </MetaText>
      </header>

      <section className={styles.section} aria-label="Integration">
        <Eyebrow wide>Integration · last 7 days</Eyebrow>
        <dl className={styles.fields}>
          <Detail label="Webhooks">
            {health.delivered} delivered
            {health.retrying > 0 && <span className={styles.warn}> · {health.retrying} retrying</span>}
            {health.failed > 0 && <span className={styles.danger}> · {health.failed} failed</span>}
          </Detail>
          <Detail label="Last delivered" mono>
            {health.lastDeliveredAt ? formatReceived(health.lastDeliveredAt) : <NotGiven text="never" />}
          </Detail>
          <Detail label="Jobs">
            {jobs.new} new · {jobs.accepted} accepted · {jobs.completed} completed · {jobs.closed} declined or cancelled
          </Detail>
        </dl>
      </section>

      <section className={styles.section} aria-label="Organisation">
        <Eyebrow wide>Organisation</Eyebrow>
        <dl className={styles.fields}>
          <Detail label="Contact">{provider.contactName}</Detail>
          <Detail label="Email" mono>
            {provider.contactEmail}
          </Detail>
          <Detail label="Phone">{provider.contactPhone ?? <NotGiven />}</Detail>
        </dl>
      </section>

      <section className={styles.section} aria-label="Services">
        <Eyebrow wide>Services</Eyebrow>
        <dl className={styles.fields}>
          <Detail label="Offers">{listLabel(provider.services) ?? <NotGiven />}</Detail>
        </dl>
      </section>

      <section className={styles.section} aria-label="API access">
        <Eyebrow wide>API access</Eyebrow>
        <dl className={styles.fields}>
          <Detail label="Client id" mono>
            {provider.clientId}
          </Detail>
          <Detail label="Webhook" mono>
            {provider.webhookUrl}
          </Detail>
        </dl>
        <MetaText tone="faint" className={styles.note}>
          The client secret and webhook signing secret are shown only to the provider.
        </MetaText>
      </section>

      <footer className={styles.footer}>
        {error && !confirming && (
          <Callout tone="danger" role="alert">
            {error}
          </Callout>
        )}
        {provider.suspended ? (
          <Button variant="primary" block disabled={busy} onClick={() => void run(onReinstate)}>
            {busy ? 'Saving…' : 'Reinstate provider'}
          </Button>
        ) : (
          <Button variant="dangerOutline" block onClick={() => setConfirming(true)}>
            Suspend provider
          </Button>
        )}
      </footer>

      {confirming && (
        <ConfirmDialog
          title={`Suspend ${provider.organisationName}?`}
          tone="danger"
          confirmLabel="Suspend provider"
          busy={busy}
          onConfirm={() => void run(onSuspend)}
          onCancel={() => setConfirming(false)}
        >
          <MetaText>
            Its access tokens stop working at once, so it can't see or act on jobs, and no new jobs or webhooks are sent until you
            reinstate it. Jobs it has already accepted stay with it.
          </MetaText>
          {error && (
            <Callout tone="danger" role="alert">
              {error}
            </Callout>
          )}
        </ConfirmDialog>
      )}
    </aside>
  )
}

function Detail({ label, mono = false, children }: { label: string; mono?: boolean; children: ReactNode }) {
  return (
    <div className={styles.field}>
      <dt>{label}</dt>
      <dd className={mono ? styles.mono : undefined}>{children}</dd>
    </div>
  )
}

function NotGiven({ text = 'not given' }: { text?: string }) {
  return <span className={styles.notGiven}>{text}</span>
}
