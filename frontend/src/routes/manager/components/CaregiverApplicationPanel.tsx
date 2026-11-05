import { useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { CaregiverApplication, CaregiverApplicationCertificate } from '../../../shared/api/profile'
import { Button, Callout, CertificationStateTag, Eyebrow, MetaText, TextArea } from '../../../shared/components/ui'
import { formatReceived } from '../lib/applications'
import { languagesLabel } from '../lib/caregivers'
import { expiryLabel, formatDate } from '../lib/certifications'
import { ScanThumbnail, ScanViewer } from './CertificateScan'
import styles from './CaregiverApplicationPanel.module.css'

const MESSAGE_MAX = 255

/** Stands in for the file name of a certificate uploaded without a scan. */
const NO_FILE = 'no file uploaded'

const DAY_MS = 24 * 60 * 60 * 1000

/**
 * The right-hand panel of the Caregivers screen for an application: what the applicant filled
 * in, each certificate they uploaded (its scan opens full size), an optional message to them,
 * and Approve / Decline.
 * Approving admits the caregiver to work; it does not publish their certificates, which are
 * reviewed one by one in Certifications (each links there). Declining needs a message so the
 * applicant knows why. `onApprove`/`onDecline` do the request and reject with the error to show.
 * Key it by application id so the message resets between rows.
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
  const [viewing, setViewing] = useState<CaregiverApplicationCertificate | null>(null)

  const name = application.fullName
  const count = application.certificates.length
  const toPublish = application.certificates.filter((cert) => cert.state === 'SUBMITTED').length

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
          {application.roundNumber > 1 ? 'resubmitted' : 'submitted'} {formatReceived(application.submittedAt)} from the
          caregiver app
        </MetaText>
      </header>

      <section className={styles.section} aria-label="Applicant">
        <Eyebrow wide>Applicant</Eyebrow>
        <dl className={styles.fields}>
          <Detail label="Name">{name}</Detail>
          <Detail label="Account">{application.username}</Detail>
          <Detail label="Mobile">{application.phone ?? <NotGiven />}</Detail>
          <Detail label="Sector asked for">{application.requestedSector ?? <NotGiven />}</Detail>
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
          Approving makes {name} available for rostering.{' '}
          {toPublish
            ? `It doesn't publish their certificates: review the ${toPublish === 1 ? 'one' : toPublish} waiting in Certifications.`
            : count
              ? null
              : "They haven't uploaded any certificates yet."}
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
          scan={{ fileName: viewing.fileName ?? NO_FILE, url: viewing.scanUrl, contentType: viewing.scanContentType }}
          onClose={() => setViewing(null)}
        />
      )}
    </aside>
  )
}

/**
 * One uploaded certificate: its scan, what the applicant typed in for it, and its review state,
 * linking to its row in Certifications.
 */
function Certificate({
  cert,
  now,
  onOpenScan,
}: {
  cert: CaregiverApplicationCertificate
  now: number
  onOpenScan: () => void
}) {
  const expiry = cert.expiryDate ? expiryLabel(daysFrom(now, cert.expiryDate)) : null
  const warn = expiry?.tone === 'danger' || expiry?.tone === 'ink'
  return (
    <li className={styles.certificate}>
      <ScanThumbnail
        scan={{ fileName: cert.fileName ?? NO_FILE, url: cert.scanUrl, contentType: cert.scanContentType }}
        onOpen={onOpenScan}
      />
      <div className={styles.certBody}>
        <div className={styles.certHead}>
          <Link className={styles.certName} to={`/manager/certifications?filter=all&id=${cert.id}`}>
            {cert.credentialTypeName}
          </Link>
          <CertificationStateTag state={cert.state} />
        </div>
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
