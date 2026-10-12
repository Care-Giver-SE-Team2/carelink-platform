import type { ReactNode } from 'react'
import { Eyebrow, MetaText, Tag } from '../../../shared/components/ui'
import type { Provider } from '../data/providerApplications'
import { formatReceived } from '../lib/applications'
import { listLabel, providerRef } from '../lib/providers'
import styles from './ProviderPanel.module.css'

/**
 * The right-hand panel of the Providers screen for an approved provider: who it is, what it
 * offers and where, and how it reaches the Open API — its client id and webhook endpoint.
 * Read-only.
 */
export function ProviderPanel({ provider }: { provider: Provider }) {
  return (
    <aside className={styles.panel} aria-label="Provider detail">
      <header className={styles.header}>
        <Eyebrow wide>Provider · {providerRef(provider.providerId)}</Eyebrow>
        <h2 className={styles.title}>{provider.organisationName}</h2>
        <div className={styles.status}>
          <Tag compact tone="accent">
            ACTIVE
          </Tag>
          <MetaText>Can be dispatched jobs through the Open API.</MetaText>
        </div>
        <MetaText tone="faint" className={styles.meta}>
          approved {formatReceived(provider.approvedAt)} from application #{provider.id}
        </MetaText>
      </header>

      <section className={styles.section} aria-label="Organisation">
        <Eyebrow wide>Organisation</Eyebrow>
        <dl className={styles.fields}>
          <Detail label="UEN" mono>
            {provider.uen}
          </Detail>
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
          <Detail label="Area" mono>
            {listLabel(provider.serviceArea) ?? <NotGiven />}
          </Detail>
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
          The client secret and webhook signing secret were shown only to the provider.
        </MetaText>
      </section>
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
