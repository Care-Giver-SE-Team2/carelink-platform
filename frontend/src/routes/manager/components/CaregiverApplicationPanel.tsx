import { useState } from 'react'
import type { ReactNode } from 'react'
import { Button, Callout, Eyebrow, MetaText, TextArea } from '../../../shared/components/ui'
import type { ApplicationCertificate, CaregiverApplication } from '../data/caregiverApplications'
import { formatReceived } from '../lib/applications'
import { languagesLabel } from '../lib/caregivers'
import { expiryLabel, formatDate } from '../lib/certifications'
import { ScanThumbnail, ScanViewer } from './CertificateScan'
import styles from './CaregiverApplicationPanel.module.css'

const MESSAGE_MAX = 255

const DAY_MS = 24 * 60 * 60 * 1000

/**
 * The right-hand panel of the Caregivers screen for an application: what the applicant filled
 * in, each certificate they uploaded (its scan opens full size), an optional message to them,
 * and Approve / Decline.
 * Approving enables their login and publishes every certificate, so there is no separate
 * certificate review afterwards. Declining needs a message so the applicant knows why.
 * `onApprove`/`onDecline` do the request and reject with the error to show. Key it by
 * application id so the message resets between rows.
 */
export function CaregiverApplicationPanel({
  application,
  onApprove,
  onDecline,
}: {
  application: CaregiverApplication
  onApprove: (message: string | null) => Promise<void>
  onDecline: (message: string) => Promise<void>
}) {
  const [message, setMessage] = useState('')
  const [messageMissing, setMessageMissing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [now] = useState(() => Date.now())
  const [viewing, setViewing] = useState<ApplicationCertificate | null>(null)

  const name = application.fullName
  const count = application.certificates.length

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
        <Eyebrow wide>Caregiver application · #{application.id}</Eyebrow>
        <h2 className={styles.title}>{name}</h2>
        <MetaText className={styles.meta}>
          submitted {formatReceived(application.submittedAt)} from the caregiver app
        </MetaText>
      </header>

      <section className={styles.section} aria-label="Applicant">
        <Eyebrow wide>Applicant</Eyebrow>
        <dl className={styles.fields}>
          <Detail label="Name">{name}</Detail>
          <Detail label="Account">{application.username}</Detail>
          <Detail label="Mobile">{application.phone ?? <NotGiven />}</Detail>
          <Detail label="Sector">{application.sector ?? <NotGiven />}</Detail>
          <Detail label="Languages">{languagesLabel(application.dialects) ?? <NotGiven />}</Detail>
        </dl>
      </section>

      <section className={styles.section} aria-label="Certificates">
        <Eyebrow wide>Certificates · {count}</Eyebrow>
        {count ? (
          <ul className={styles.certificates}>
            {application.certificates.map((cert) => (
              <Certificate key={cert.id} cert={cert} now={now} onOpenScan={() => setViewing(cert)} />
            ))}
          </ul>
        ) : (
          <MetaText tone="faint">None uploaded.</MetaText>
        )}
      </section>

      <section className={styles.message}>
        <Eyebrow wide htmlFor="caregiver-application-message">
          Message to {name} · optional
        </Eyebrow>
        <TextArea
          id="caregiver-application-message"
          value={message}
          onChange={(value) => {
            setMessage(value)
            if (value.trim()) setMessageMissing(false)
          }}
          invalid={messageMissing}
          aria-describedby={messageMissing ? 'caregiver-application-message-error' : undefined}
          maxLength={MESSAGE_MAX}
          disabled={busy}
          placeholder={`Shown to ${name} with the decision in the caregiver app.`}
        />
        {messageMissing && (
          <MetaText id="caregiver-application-message-error" tone="danger" className={styles.messageError}>
            Tell {name} why
          </MetaText>
        )}
      </section>

      <footer className={styles.footer}>
        <MetaText className={styles.effect}>
          {count
            ? `Approving activates ${name}'s login and publishes the ${count === 1 ? 'certificate' : `${count} certificates`} above, so they can be rostered straight away.`
            : `Approving activates ${name}'s login. With no certificate they stay onboarding, and can't be rostered, until one is published.`}
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

      {viewing?.scanUrl && (
        <ScanViewer
          title={`${name} — ${viewing.credentialTypeName.toLowerCase()}`}
          scan={{ fileName: viewing.fileName, url: viewing.scanUrl, contentType: viewing.scanContentType }}
          onClose={() => setViewing(null)}
        />
      )}
    </aside>
  )
}

/** One uploaded certificate: its scan and what the applicant typed in for it. */
function Certificate({ cert, now, onOpenScan }: { cert: ApplicationCertificate; now: number; onOpenScan: () => void }) {
  const expiry = cert.expiryDate ? expiryLabel(daysFrom(now, cert.expiryDate)) : null
  const warn = expiry?.tone === 'danger' || expiry?.tone === 'ink'
  return (
    <li className={styles.certificate}>
      <ScanThumbnail
        scan={{ fileName: cert.fileName, url: cert.scanUrl, contentType: cert.scanContentType }}
        onOpen={onOpenScan}
      />
      <div className={styles.certBody}>
        <span className={styles.certName}>{cert.credentialTypeName}</span>
        <dl className={styles.certFields}>
          <Detail label="Issued by">{cert.issuingBody ?? <NotGiven />}</Detail>
          <Detail label="Cert no." mono>
            {cert.certificateNo ?? <NotGiven />}
          </Detail>
          <Detail label="Valid until" mono>
            {cert.expiryDate ? formatDate(cert.expiryDate) : 'No expiry'}
            {expiry && warn && (
              <span className={styles[expiry.tone]}> · {expiry.text === 'expired' ? 'expired' : `in ${expiry.text}`}</span>
            )}
          </Detail>
        </dl>
      </div>
    </li>
  )
}

/** Whole days from `now` to the "yyyy-MM-dd" date, by Singapore calendar date. */
function daysFrom(now: number, date: string): number {
  const today = new Date(now + 8 * 60 * 60 * 1000).toISOString().slice(0, 10)
  return Math.round((Date.parse(`${date}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`)) / DAY_MS)
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
