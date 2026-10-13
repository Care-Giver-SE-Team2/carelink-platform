import { useEffect, useId, useRef, useState } from 'react'
import type { ButtonHTMLAttributes, InputHTMLAttributes, KeyboardEvent, ReactNode, TextareaHTMLAttributes } from 'react'
import { createPortal } from 'react-dom'
import { cx } from '../../../shared/components/ui/cx'
import type { DeliveryStatus, JobStatus } from '../../../features/provider-platform/types'
import styles from './ui.module.css'

/**
 * The portal's controls. They follow the "provider workbench" design handoff, which sizes everything
 * for a provider's ops desk (40px controls, 15px type) rather than the manager console's dense
 * shared components, and they stay in this folder: shared/ never imports from provider/.
 */

export function Spinner() {
  return (
    <svg className={styles.spinner} width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
      <path d="M7 1a6 6 0 1 1-6 6" />
    </svg>
  )
}

type ButtonProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'style'> & {
  variant?: 'primary' | 'secondary' | 'danger'
  size?: 'small' | 'medium' | 'large'
  block?: boolean
  /** Shows a spinner and this text, and disables the button. */
  busy?: string | false
}

export function Button({ variant = 'secondary', size = 'medium', block, busy, disabled, children, className, type = 'button', ...rest }: ButtonProps) {
  return (
    <button
      type={type}
      className={cx(styles.button, variant !== 'secondary' && styles[variant], size !== 'medium' && styles[size], block && styles.block, className)}
      disabled={disabled || Boolean(busy)}
      {...rest}
    >
      {busy ? (
        <>
          <Spinner />
          {busy}
        </>
      ) : (
        children
      )}
    </button>
  )
}

/** A text button that looks like a link. */
export function LinkButton({ className, type = 'button', ...rest }: Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'style'>) {
  return <button type={type} className={cx(styles.link, className)} {...rest} />
}

export function Field({
  label,
  htmlFor,
  optional,
  helper,
  error,
  errorId,
  children,
}: {
  label: ReactNode
  htmlFor?: string
  optional?: boolean
  helper?: ReactNode
  error?: string
  errorId?: string
  children: ReactNode
}) {
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={htmlFor}>
        {label}
        {optional && <span className={styles.optional}> (optional)</span>}
      </label>
      {children}
      {error ? (
        <div id={errorId} className={styles.error}>
          {error}
        </div>
      ) : (
        helper && <div className={styles.helper}>{helper}</div>
      )}
    </div>
  )
}

export function TextInput({
  mono,
  invalid,
  className,
  ...rest
}: Omit<InputHTMLAttributes<HTMLInputElement>, 'style'> & { mono?: boolean; invalid?: boolean }) {
  return (
    <input
      className={cx(styles.input, mono && styles.mono, invalid && styles.invalid, className)}
      aria-invalid={invalid || undefined}
      {...rest}
    />
  )
}

export function TextArea({ invalid, className, ...rest }: Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'style'> & { invalid?: boolean }) {
  return <textarea className={cx(styles.input, invalid && styles.invalid, className)} aria-invalid={invalid || undefined} {...rest} />
}

export function Checkbox({
  id,
  checked,
  onChange,
  invalid,
  describedBy,
  children,
}: {
  id?: string
  checked: boolean
  onChange: (checked: boolean) => void
  invalid?: boolean
  describedBy?: string
  children: ReactNode
}) {
  return (
    <label className={styles.checkbox}>
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        aria-invalid={invalid || undefined}
        aria-describedby={describedBy}
      />
      <span className={cx(styles.box, invalid && !checked && styles.boxInvalid)} aria-hidden="true">
        {checked && <CheckIcon color="#fff" />}
      </span>
      <span>{children}</span>
    </label>
  )
}

type TagKind = JobStatus | DeliveryStatus | 'approved' | 'submitted' | 'rejected' | 'pending' | 'suspended'

const TAG: Record<TagKind, { label: string; tone: string }> = {
  new: { label: 'NEW', tone: 'tag_new' },
  accepted: { label: 'ACCEPTED', tone: 'tag_accepted' },
  completed: { label: 'COMPLETED', tone: 'tag_good' },
  approved: { label: 'APPROVED', tone: 'tag_good' },
  submitted: { label: 'SUBMITTED', tone: 'tag_good' },
  cancelled: { label: 'CANCELLED', tone: 'tag_bad' },
  declined: { label: 'DECLINED', tone: 'tag_bad' },
  rejected: { label: 'REJECTED', tone: 'tag_bad' },
  pending: { label: 'PENDING REVIEW', tone: 'tag_pending' },
  suspended: { label: 'SUSPENDED', tone: 'tag_bad' },
  delivered: { label: 'DELIVERED', tone: 'tag_good' },
  retrying: { label: 'RETRYING', tone: 'tag_pending' },
  failed: { label: 'FAILED', tone: 'tag_bad' },
}

/** A job's, application's or webhook delivery's state, as a square mono tag. */
export function StateTag({ kind }: { kind: TagKind }) {
  const tag = TAG[kind]
  return <span className={cx(styles.tag, styles[tag.tone])}>{tag.label}</span>
}

export function SectionLabel({ children, id }: { children: ReactNode; id?: string }) {
  return (
    <div id={id} className={styles.sectionLabel}>
      {children}
    </div>
  )
}

export function Banner({
  tone,
  title,
  children,
  action,
  role,
}: {
  tone: 'danger' | 'warning' | 'success'
  title: ReactNode
  children?: ReactNode
  action?: ReactNode
  role?: 'alert' | 'status'
}) {
  return (
    <div className={cx(styles.banner, styles[`banner_${tone}`])} role={role}>
      <div className={styles.bannerText}>
        <div className={styles.bannerTitle}>{title}</div>
        {children && <div className={styles.bannerBody}>{children}</div>}
      </div>
      {action && <div className={styles.bannerAction}>{action}</div>}
    </div>
  )
}

/** A read-only value joined to a Copy button that confirms for two seconds. */
export function CopyField({ label, value, helper }: { label: string; value: string; helper?: string }) {
  const [copied, setCopied] = useState(false)
  const id = useId()

  useEffect(() => {
    if (!copied) return
    const timer = setTimeout(() => setCopied(false), 2000)
    return () => clearTimeout(timer)
  }, [copied])

  async function copy() {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
    } catch {
      // Clipboard blocked: the value is still there to select by hand.
    }
  }

  return (
    <div className={styles.field}>
      <span className={styles.label} id={id}>
        {label}
      </span>
      <div className={styles.copyField}>
        <output className={styles.copyValue} aria-labelledby={id}>
          {value}
        </output>
        <Button size="small" className={copied ? styles.success : undefined} onClick={() => void copy()} aria-label={`Copy ${label.toLowerCase()}`}>
          {copied ? (
            <>
              <CheckIcon /> Copied
            </>
          ) : (
            'Copy'
          )}
        </Button>
      </div>
      {helper && <div className={styles.helper}>{helper}</div>}
    </div>
  )
}

/** Label/value rows, 150px labels; on a phone the page stacks them itself. */
export function Facts({ rows, className }: { rows: { label: string; value: ReactNode; mono?: boolean }[]; className?: string }) {
  return (
    <dl className={cx(styles.facts, className)}>
      {rows.map((row) => (
        <div key={row.label} style={{ display: 'contents' }}>
          <dt>{row.label}</dt>
          <dd className={row.mono ? styles.mono : undefined}>{row.value}</dd>
        </div>
      ))}
    </dl>
  )
}

const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/**
 * A 480px dialog over a dark scrim: Esc and × close it, focus is trapped inside and returns to
 * the opener afterwards. `footer` holds the actions, the confirming one last.
 */
export function Dialog({ title, children, footer, onClose }: { title: ReactNode; children: ReactNode; footer: ReactNode; onClose: () => void }) {
  const ref = useRef<HTMLDivElement>(null)
  const titleId = useId()
  const [returnTo] = useState(() => document.activeElement as HTMLElement | null)

  useEffect(() => {
    const first = ref.current?.querySelector<HTMLElement>(FOCUSABLE)
    ;(first ?? ref.current)?.focus()
    return () => returnTo?.focus?.()
  }, [returnTo])

  function onKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    if (e.key === 'Escape') {
      e.stopPropagation()
      onClose()
      return
    }
    if (e.key !== 'Tab') return
    const items = Array.from(ref.current?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])
    if (items.length === 0) return
    const first = items[0]
    const last = items[items.length - 1]
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault()
      last.focus()
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault()
      first.focus()
    }
  }

  return createPortal(
    <div
      className={styles.backdrop}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div ref={ref} className={styles.dialog} role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1} onKeyDown={onKeyDown}>
        <div className={styles.dialogHead}>
          <h2 id={titleId} className={styles.dialogTitle}>
            {title}
          </h2>
          <button type="button" className={styles.close} onClick={onClose} aria-label="Close">
            ×
          </button>
        </div>
        <div className={styles.dialogBody}>{children}</div>
        <div className={styles.dialogFoot}>{footer}</div>
      </div>
    </div>,
    document.body,
  )
}

export function CheckIcon({ color = 'currentColor' }: { color?: string }) {
  return (
    <svg width="12" height="10" viewBox="0 0 12 10" fill="none" stroke={color} strokeWidth="2" aria-hidden="true">
      <path d="M1 5l3.5 3.5L11 1" />
    </svg>
  )
}

export function LockIcon({ color = 'currentColor' }: { color?: string }) {
  return (
    <svg width="10" height="11" viewBox="0 0 11 12" fill="none" stroke={color} strokeWidth="1.2" aria-hidden="true">
      <rect x="1" y="5" width="9" height="6.4" />
      <path d="M3 5V3.4a2.5 2.5 0 0 1 5 0V5" />
    </svg>
  )
}

export function RefreshIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
      <path d="M12 7a5 5 0 1 1-1.5-3.6M12 1.5V4H9.5" />
    </svg>
  )
}

export function WarningIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="var(--danger)" strokeWidth="1.8" aria-hidden="true">
      <path d="M10 2l8.5 15h-17z" />
      <path d="M10 8v4M10 14.5v.5" />
    </svg>
  )
}
