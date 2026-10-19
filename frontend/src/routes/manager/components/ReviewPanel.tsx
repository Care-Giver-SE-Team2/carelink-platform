import { useState, type ReactNode } from 'react'
import { Button, Callout, Eyebrow, MetaText } from '../../../shared/components/ui'
import type { CredentialDocument, CredentialRegisterRow } from '../../../shared/api/profile'
import { caregiverRef, formatDate, formatDateTime, nextReminder, reviewTitle } from '../lib/certifications'
import { useCredentialDocuments } from '../lib/useCertifications'
import { ScanThumbnail, ScanViewer, type Scan } from './CertificateScan'
import styles from './ReviewPanel.module.css'

/**
 * The right-hand panel of the Certifications screen. A submitted certificate shows its details
 * with Publish / Reject and what publishing would change; any other row
 * shows the same details read-only. `visitsAtRisk` is undefined while it loads, null past the
 * roster window.
 */
export function ReviewPanel({
  row,
  visitsAtRisk,
  busy,
  error,
  onPublish,
  onReject,
}: {
  row: CredentialRegisterRow
  visitsAtRisk: number | null | undefined
  busy: boolean
  error: string | null
  onPublish: () => void
  onReject: () => void
}) {
  const reviewable = row.state === 'SUBMITTED'
  const meta = [caregiverRef(row.caregiverId)]
  if (reviewable && row.submittedAt) meta.push(`submitted ${formatDateTime(row.submittedAt)} from caregiver app`)
  if (!reviewable && row.reviewedAt) meta.push(`reviewed ${formatDateTime(row.reviewedAt)}`)

  return (
    <aside className={styles.panel} aria-label="Certification detail">
      <header className={styles.header}>
        <Eyebrow wide>{reviewable ? 'Review submission' : 'Certification'}</Eyebrow>
        <h2 className={styles.title}>{reviewTitle(row)}</h2>
        <MetaText className={styles.meta}>{meta.join(' · ')}</MetaText>
      </header>

      <section className={styles.detail}>
        <div className={styles.detailRow}>
          <Scans key={row.id} row={row} />
          <dl className={styles.fields}>
            <Detail label="Issuing body">{row.issuingBody ?? '—'}</Detail>
            <Detail label="Certificate no." mono>
              {row.certificateNo ?? '—'}
            </Detail>
            <Detail label="Valid until" mono>
              {row.expiryDate ? formatDate(row.expiryDate) : 'No expiry'}
            </Detail>
            {row.replacesId !== null && (
              <Detail label="Replaces">
                {row.credentialTypeName.toLowerCase()}
                {row.replacesExpiryDate && ` · expires ${formatDate(row.replacesExpiryDate)}`}
              </Detail>
            )}
            {row.reviewNote && (
              <Detail label="Reason">{row.reviewNote}</Detail>
            )}
          </dl>
        </div>
        {reviewable && (
          <div className={styles.actions}>
            <Button variant="primary" block disabled={busy} onClick={onPublish}>
              {busy ? 'Saving…' : 'Publish'}
            </Button>
            <Button variant="dangerOutline" block disabled={busy} onClick={onReject}>
              Reject
            </Button>
          </div>
        )}
        {error && (
          <Callout tone="danger" role="alert" className={styles.error}>
            {error}
          </Callout>
        )}
      </section>

      {reviewable && <PublishEffect row={row} visitsAtRisk={visitsAtRisk} />}
    </aside>
  )
}

/**
 * Every file uploaded with the certificate, each opening full size; the striped stand-in while
 * they load or when none were uploaded.
 */
function Scans({ row }: { row: CredentialRegisterRow }) {
  const { data: documents } = useCredentialDocuments(row.id)
  const [viewing, setViewing] = useState<CredentialDocument | null>(null)
  if (!documents?.length) {
    return (
      <div className={styles.scan} role="img" aria-label="Certificate scan not available">
        <span>
          scan of
          <br />
          certificate
        </span>
      </div>
    )
  }
  return (
    <div className={styles.scans}>
      {documents.map((document) => (
        <ScanThumbnail key={document.id} scan={toScan(document)} onOpen={() => setViewing(document)} />
      ))}
      {viewing && (
        <ScanViewer
          title={reviewTitle(row)}
          scan={{ ...toScan(viewing), url: viewing.contentUrl }}
          onClose={() => setViewing(null)}
        />
      )}
    </div>
  )
}

function toScan(document: CredentialDocument): Scan {
  return {
    fileName: document.fileName,
    url: document.contentAvailable ? document.contentUrl : null,
    contentType: document.contentType,
  }
}

function Detail({ label, mono = false, children }: { label: string; mono?: boolean; children: ReactNode }) {
  return (
    <div>
      <dt className={styles.fieldLabel}>{label}</dt>
      <dd className={mono ? styles.monoValue : styles.value}>{children}</dd>
    </div>
  )
}

/** What publishing changes: the visits it clears, the caregiver's eligibility, and the next reminder. */
function PublishEffect({ row, visitsAtRisk }: { row: CredentialRegisterRow; visitsAtRisk: number | null | undefined }) {
  const atRisk = typeof visitsAtRisk === 'number' && visitsAtRisk > 0
  return (
    <section className={styles.effect}>
      <Eyebrow wide>Effect of publishing</Eyebrow>
      <dl className={styles.effectRows}>
        <EffectRow
          label={atRisk ? `${visitsAtRisk} visits at risk` : 'Visits at risk'}
          value={atRisk ? 'cleared' : visitsAtRisk === 0 ? 'none' : '—'}
          positive={atRisk}
        />
        <EffectRow label={`${row.credentialTypeName} eligibility`} value={row.renewal ? 'retained' : 'gained'} positive />
        <EffectRow label="Next reminder" value={row.expiryDate ? formatDate(nextReminder(row.expiryDate)) : 'none'} />
      </dl>
      <Callout tone="neutral" className={styles.note}>
        Publishing is recorded against your account and visible to the caregiver and to any family member whose
        relative that caregiver serves.
      </Callout>
    </section>
  )
}

function EffectRow({ label, value, positive = false }: { label: string; value: string; positive?: boolean }) {
  return (
    <div className={styles.effectRow}>
      <dt>{label}</dt>
      <dd className={positive ? styles.positive : undefined}>{value}</dd>
    </div>
  )
}
