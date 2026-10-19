import type { ReactNode } from 'react'
import { cx } from './cx'
import styles from './Badge.module.css'

/** `scheduled` = published with a start date still in the future; `none` = the elder has no plan yet. */
export type BadgeStatus = 'published' | 'draft' | 'scheduled' | 'archived' | 'stopped' | 'none'

const LABELS: Record<BadgeStatus, string> = {
  published: 'PUBLISHED',
  draft: 'DRAFT',
  scheduled: 'SCHEDULED',
  archived: 'ARCHIVED',
  stopped: 'STOPPED',
  none: 'NO PLAN YET',
}

/** Plan status, optionally with its version ("PUBLISHED v4"). */
export function Badge({
  status,
  version,
  compact = false,
  className,
}: {
  status: BadgeStatus
  version?: number | null
  /** Tighter padding for badges inside a list row. */
  compact?: boolean
  className?: string
}) {
  const suffix = status !== 'none' && version != null ? ` v${version}` : ''
  return (
    <span className={cx(styles.badge, styles[status], compact && styles.compact, className)}>
      {LABELS[status]}
      {suffix}
    </span>
  )
}

export type TagTone = 'ink' | 'danger' | 'muted' | 'accent'

/**
 * Outlined label, e.g. a required certification ("FIRST AID"). `solid` fills it in the
 * tone's colour ("SEV 1", "MODEL"); `compact` is the tighter, tracked size used for
 * severity, role and source labels.
 */
export function Tag({
  children,
  tone = 'ink',
  solid = false,
  compact = false,
  className,
}: {
  children: ReactNode
  tone?: TagTone
  solid?: boolean
  compact?: boolean
  className?: string
}) {
  return (
    <span className={cx(styles.tag, styles[`tag_${tone}`], solid && styles.solid, compact && styles.tagCompact, className)}>
      {children}
    </span>
  )
}

export type VisitState = 'closed' | 'verified' | 'in_visit' | 'exception' | 'scheduled' | 'not_checked_in' | 'needs_cover'

const VISIT_LABELS: Record<VisitState, string> = {
  closed: 'COMPLETED',
  verified: 'VERIFIED',
  in_visit: 'IN VISIT',
  exception: 'EXCEPTION',
  scheduled: 'SCHEDULED',
  not_checked_in: 'NOT CHECKED IN',
  needs_cover: 'NEEDS COVER',
}

/** Where one visit stands today — the Badge look, keyed by visit state instead of plan status. */
export function VisitStateBadge({ state, className }: { state: VisitState; className?: string }) {
  return <span className={cx(styles.badge, styles.visit, styles[`visit_${state}`], className)}>{VISIT_LABELS[state]}</span>
}

export type CertificationTagState =
  | 'SUBMITTED'
  | 'REJECTED'
  | 'REMINDED'
  | 'PUBLISHED'
  | 'EXPIRED'
  | 'REVOKED'

const CERTIFICATION_LABELS: Record<CertificationTagState, string> = {
  SUBMITTED: 'SUBMITTED',
  REJECTED: 'REJECTED',
  REMINDED: 'PENDING',
  PUBLISHED: 'PUBLISHED',
  EXPIRED: 'EXPIRED',
  REVOKED: 'REVOKED',
}

/**
 * Where a caregiver's certificate stands: waiting for review (accent), pending the caregiver's
 * renewal after a reminder (muted), published (green), or rejected, expired or revoked (red).
 */
export function CertificationStateTag({ state, className }: { state: CertificationTagState; className?: string }) {
  return (
    <span className={cx(styles.badge, styles.certification, styles[`cert_${state}`], className)}>
      {CERTIFICATION_LABELS[state]}
    </span>
  )
}
