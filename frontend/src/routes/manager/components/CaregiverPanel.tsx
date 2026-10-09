import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { CertificationStateTag, Eyebrow, MetaText, Tag } from '../../../shared/components/ui'
import { formatReceived } from '../lib/applications'
import { STATUS_MEANING, STATUS_TAG, languagesLabel } from '../lib/caregivers'
import type { DirectoryCaregiver } from '../lib/caregivers'
import { caregiverRef, expiryLabel, formatDate } from '../lib/certifications'
import styles from './CaregiverPanel.module.css'

/**
 * The right-hand panel of the Caregivers screen for a caregiver: their status, profile, the
 * elders they're primary caregiver for, and their certificates. Read-only; each certificate
 * opens in Certifications, where reviews and expiries are handled. `primaryFor` is undefined
 * while the elders load.
 */
export function CaregiverPanel({
  caregiver,
  primaryFor,
}: {
  caregiver: DirectoryCaregiver
  primaryFor: string[] | undefined
}) {
  const tag = STATUS_TAG[caregiver.status]
  const languages = languagesLabel(caregiver.dialects)

  return (
    <aside className={styles.panel} aria-label="Caregiver detail">
      <header className={styles.header}>
        <Eyebrow wide>Caregiver · {caregiverRef(caregiver.id)}</Eyebrow>
        <h2 className={styles.title}>{caregiver.fullName}</h2>
        <div className={styles.status}>
          <Tag compact tone={tag.tone}>
            {tag.label}
          </Tag>
          <MetaText>{STATUS_MEANING[caregiver.status]}</MetaText>
        </div>
        {caregiver.approvedAt && (
          <MetaText tone="faint" className={styles.meta}>
            approved from an application {formatReceived(caregiver.approvedAt)}
          </MetaText>
        )}
      </header>

      <section className={styles.section} aria-label="Profile">
        <Eyebrow wide>Profile</Eyebrow>
        <dl className={styles.fields}>
          <Detail label="Sector">{caregiver.sector ?? <NotGiven />}</Detail>
          <Detail label="Languages">{languages ?? <NotGiven />}</Detail>
          <Detail label="Primary for">
            {primaryFor === undefined ? '…' : primaryFor.length ? primaryFor.join(', ') : <NotGiven text="no elders" />}
          </Detail>
        </dl>
      </section>

      <section className={styles.section} aria-label="Certificates">
        <Eyebrow wide>Certificates · {caregiver.certificates.length}</Eyebrow>
        {caregiver.certificates.length ? (
          <ul className={styles.certificates}>
            {caregiver.certificates.map((cert) => {
              const expiry = expiryLabel(cert.daysUntilExpiry)
              return (
                <li key={cert.key} className={styles.certificate}>
                  <div className={styles.certMain}>
                    {cert.registerId === null ? (
                      <span className={styles.certName}>{cert.name}</span>
                    ) : (
                      <Link className={styles.certLink} to={`/manager/certifications?filter=all&id=${cert.registerId}`}>
                        {cert.name}
                      </Link>
                    )}
                    <span className={styles.certMeta}>
                      {cert.expiryDate ? `valid until ${formatDate(cert.expiryDate)}` : 'no expiry'}
                      {cert.daysUntilExpiry !== null && (expiry.tone === 'danger' || expiry.tone === 'ink') && (
                        <span className={styles[expiry.tone]}>
                          {' '}
                          · {expiry.text === 'expired' ? 'expired' : `in ${expiry.text}`}
                        </span>
                      )}
                    </span>
                  </div>
                  <CertificationStateTag state={cert.state} />
                </li>
              )
            })}
          </ul>
        ) : (
          <MetaText tone="faint">None on record.</MetaText>
        )}
      </section>
    </aside>
  )
}

function Detail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className={styles.field}>
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  )
}

function NotGiven({ text = 'not given' }: { text?: string }) {
  return <span className={styles.notGiven}>{text}</span>
}
