import { useState } from 'react'
import type { ReactNode } from 'react'
import { Button, Callout, Eyebrow, MetaText, TextArea } from '../../../shared/components/ui'
import type { ProviderApplication } from '../data/providerApplications'
import { formatReceived } from '../lib/applications'
import { listLabel } from '../lib/providers'
import styles from './ProviderPanel.module.css'

const MESSAGE_MAX = 255

/**
 * The right-hand panel of the Providers screen for an application: who the provider is, what it
 * offers and where, the webhook it wants jobs sent to, an optional message to it, and Approve /
 * Decline. Approving creates its Open API client; the provider collects the credentials from the
 * workbench, so there is nothing here for the manager to pass on. Declining needs a message so
 * the provider knows why. `onApprove`/`onDecline` do the request and reject with the error to
 * show. Key it by application id so the message resets between rows.
 */
export function ProviderApplicationPanel({
  application,
  onApprove,
  onDecline,
}: {
  application: ProviderApplication
  onApprove: (message: string | null) => Promise<void>
  onDecline: (message: string) => Promise<void>
}) {
  const [message, setMessage] = useState('')
  const [messageMissing, setMessageMissing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const name = application.organisationName
  const services = listLabel(application.services)

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save this decision.')
      setBusy(false)
    }
  }

  function handleDecline() {
    if (!message.trim()) {
      setMessageMissing(true)
      return
    }
    void run(() => onDecline(message.trim()))
  }

  return (
    <aside className={styles.panel} aria-label="Application detail">
      <header className={styles.header}>
        <Eyebrow wide>Provider application · #{application.id}</Eyebrow>
        <h2 className={styles.title}>{name}</h2>
        <MetaText className={styles.meta}>
          submitted {formatReceived(application.submittedAt)} from the provider workbench
        </MetaText>
      </header>

      <section className={styles.section} aria-label="Organisation">
        <Eyebrow wide>Organisation</Eyebrow>
        <dl className={styles.fields}>
          <Detail label="Name">{name}</Detail>
          <Detail label="UEN" mono>
            {application.uen}
          </Detail>
          <Detail label="Contact">{application.contactName}</Detail>
          <Detail label="Email" mono>
            {application.contactEmail}
          </Detail>
          <Detail label="Phone">{application.contactPhone ?? <NotGiven />}</Detail>
        </dl>
      </section>

      <section className={styles.section} aria-label="Services">
        <Eyebrow wide>Services</Eyebrow>
        <dl className={styles.fields}>
          <Detail label="Offers">{services ?? <NotGiven />}</Detail>
          <Detail label="Area" mono>
            {listLabel(application.serviceArea) ?? <NotGiven />}
          </Detail>
        </dl>
      </section>

      <section className={styles.section} aria-label="Webhook">
        <Eyebrow wide>Webhook</Eyebrow>
        <dl className={styles.fields}>
          <Detail label="Endpoint" mono>
            {application.webhookUrl}
          </Detail>
        </dl>
        <MetaText tone="faint" className={styles.note}>
          Dispatched and cancelled jobs are posted here, signed with a secret only the provider holds.
        </MetaText>
      </section>

      <section className={styles.message}>
        <Eyebrow wide htmlFor="provider-application-message">
          Message to {name} · optional
        </Eyebrow>
        <TextArea
          id="provider-application-message"
          value={message}
          onChange={(value) => {
            setMessage(value)
            if (value.trim()) setMessageMissing(false)
          }}
          invalid={messageMissing}
          aria-describedby={messageMissing ? 'provider-application-message-error' : undefined}
          maxLength={MESSAGE_MAX}
          disabled={busy}
          placeholder={`Shown to ${name} with the decision when they check their status in the workbench.`}
        />
        {messageMissing && (
          <MetaText id="provider-application-message-error" tone="danger" className={styles.messageError}>
            Tell {name} why
          </MetaText>
        )}
      </section>

      <footer className={styles.footer}>
        <MetaText className={styles.effect}>
          {`Approving gives ${name} access to the Open API. They collect their credentials once from the workbench, and can then be dispatched ${services ?? 'jobs'}.`}
        </MetaText>
        {error && (
          <Callout tone="danger" role="alert">
            {error}
          </Callout>
        )}
        <div className={styles.actions}>
          <Button
            variant="primary"
            block
            disabled={busy}
            onClick={() => void run(() => onApprove(message.trim() || null))}
          >
            {busy ? 'Saving…' : 'Approve'}
          </Button>
          <Button variant="dangerOutline" block disabled={busy} onClick={handleDecline}>
            Decline
          </Button>
        </div>
      </footer>
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

function NotGiven() {
  return <span className={styles.notGiven}>not given</span>
}
