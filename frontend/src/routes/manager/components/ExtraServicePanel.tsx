import { useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Button, Callout, ConfirmDialog, Eyebrow, MetaText } from '../../../shared/components/ui'
import type { ManagedValueAddedServiceRequest } from '../../../features/value-added-services/types'
import { canCancel, formatWhen, stateTag } from '../lib/extraServices'
import { VisitCoverModal } from './VisitCoverModal'
import styles from './ExtraServicePanel.module.css'

/**
 * The right-hand panel of the Extra services screen: what the elder asked for and when, the
 * family's answer, the work order's visit, and the two things a manager can do — put a
 * caregiver on a visit nobody holds, and call the request off. `onAssign`/`onCancel` do the
 * request and reject with the error to show. Key it by request id so dialogs reset between rows.
 */
export function ExtraServicePanel({
  request,
  elderName,
  caregiverName,
  onAssign,
  onCancel,
}: {
  request: ManagedValueAddedServiceRequest
  elderName: string
  caregiverName: string | null
  onAssign: (caregiverId: number) => Promise<void>
  onCancel: () => Promise<void>
}) {
  const [picking, setPicking] = useState(false)
  const [confirmingCancel, setConfirmingCancel] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const service = request.serviceName ?? 'Extra service'
  const when = formatWhen(request.requestedSchedule)
  const tag = stateTag(request)

  async function cancel() {
    setBusy(true)
    setError(null)
    try {
      await onCancel()
      setConfirmingCancel(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not cancel this request.')
      setConfirmingCancel(false)
    } finally {
      setBusy(false)
    }
  }

  return (
    <aside className={styles.panel} aria-label="Extra service detail">
      <header className={styles.header}>
        <div className={styles.headerRow}>
          <Eyebrow wide>Extra service · #{request.id}</Eyebrow>
          <span className={tag.tone === 'danger' ? styles.stateUrgent : styles.state}>{tag.label}</span>
        </div>
        <h2 className={styles.title}>{service}</h2>
        <MetaText className={styles.meta}>
          for {elderName} · {when}
        </MetaText>
      </header>

      <section className={styles.section} aria-label="Request">
        <Eyebrow wide>Request</Eyebrow>
        <dl className={styles.fields}>
          <Detail label="Asked">{formatWhen(request.createdAt)} by the elder</Detail>
          <Detail label="Wanted">{when}</Detail>
          <Detail label="Instructions">{request.specialInstructions ?? <NotGiven />}</Detail>
          <Detail label="Family">{familyAnswer(request)}</Detail>
        </dl>
      </section>

      <section className={styles.section} aria-label="Work order">
        <Eyebrow wide>Work order</Eyebrow>
        <dl className={styles.fields}>
          <Detail label="Visit">
            {request.visitId === null ? <NotGiven text="none until the family approves" /> : `#${request.visitId} · ${visitLabel(request.visitStatus)}`}
          </Detail>
          <Detail label="Caregiver">{caregiverName ?? <NotGiven text="nobody yet" />}</Detail>
        </dl>
      </section>

      <footer className={styles.footer}>
        <Guidance request={request} />
        {error && (
          <Callout tone="danger" role="alert">
            {error}
          </Callout>
        )}
        {(request.needsCaregiver || canCancel(request)) && (
          <div className={styles.actions}>
            {request.needsCaregiver && (
              <Button variant="primary" block disabled={busy} onClick={() => setPicking(true)}>
                Assign caregiver
              </Button>
            )}
            {canCancel(request) && (
              <Button variant="dangerOutline" block disabled={busy} onClick={() => setConfirmingCancel(true)}>
                Cancel request
              </Button>
            )}
          </div>
        )}
      </footer>

      {picking && (
        <VisitCoverModal
          requestId={request.id}
          title={`${service} · ${elderName}`}
          meta={when}
          assign={async (caregiverId) => {
            await onAssign(caregiverId)
            setPicking(false)
          }}
          onClose={() => setPicking(false)}
        />
      )}

      {confirmingCancel && (
        <ConfirmDialog
          tone="danger"
          eyebrow="Cancel request"
          title={`Cancel ${service} for ${elderName}?`}
          meta={when}
          confirmLabel="Cancel request"
          cancelLabel="Keep it"
          busy={busy}
          onConfirm={() => void cancel()}
          onCancel={() => setConfirmingCancel(false)}
        >
          <MetaText>
            {request.visitId === null
              ? 'The elder and family will see it as cancelled.'
              : 'Its visit is called off and leaves the caregiver’s schedule. The elder and family will see it as cancelled.'}
          </MetaText>
        </ConfirmDialog>
      )}
    </aside>
  )
}

/** What the manager should know about where the request stands, if anything. */
function Guidance({ request }: { request: ManagedValueAddedServiceRequest }) {
  if (request.needsCaregiver) {
    return (
      <MetaText className={styles.effect}>
        The elder’s primary caregiver was not free at this time, so nobody is on the visit. Assign somebody before it is
        due, or it becomes an exception when it starts.
      </MetaText>
    )
  }
  if (request.status === 'DISPATCHED' && request.visitStatus === 'EXCEPTION') {
    return (
      <MetaText className={styles.effect}>
        The visit is an exception and is handled in <Link to="/manager/exceptions">Exceptions</Link>.
      </MetaText>
    )
  }
  if (request.status === 'PENDING_APPROVAL') {
    return (
      <MetaText className={styles.effect}>
        Waiting for the family to approve in the family app. Approval creates the visit and puts the primary caregiver on
        it if they are free.
      </MetaText>
    )
  }
  return null
}

function familyAnswer(request: ManagedValueAddedServiceRequest): ReactNode {
  if (request.status === 'PENDING_APPROVAL') return <NotGiven text="not answered yet" />
  if (request.decidedAt === null) return <NotGiven text="cancelled before an answer" />
  const answer = request.status === 'REJECTED' ? 'Declined' : 'Approved'
  return `${answer} ${formatWhen(request.decidedAt)}`
}

const VISIT_LABELS: Record<string, string> = {
  SCHEDULED: 'scheduled',
  ARRIVED: 'caregiver arrived',
  IN_PROGRESS: 'in progress',
  COMPLETED: 'done, awaiting the elder',
  VERIFIED: 'done and confirmed',
  AUTO_CLOSED: 'done',
  EXCEPTION: 'exception',
  CANCELLED: 'called off',
}

function visitLabel(status: string | null): string {
  return status === null ? 'not found' : VISIT_LABELS[status] ?? status.toLowerCase()
}

function Detail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className={styles.field}>
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  )
}

function NotGiven({ text = 'none' }: { text?: string }) {
  return <span className={styles.notGiven}>{text}</span>
}
