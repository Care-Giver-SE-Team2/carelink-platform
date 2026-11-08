import { useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Button, Callout, ConfirmDialog, Eyebrow, MetaText } from '../../../shared/components/ui'
import type { ManagedValueAddedServiceRequest } from '../../../features/value-added-services/types'
import { canCancel, formatWhen, stateTag } from '../lib/extraServices'
import { ProviderOfferModal } from './ProviderOfferModal'
import { VisitCoverModal } from './VisitCoverModal'
import styles from './ExtraServicePanel.module.css'

/**
 * The right-hand panel of the Extra services screen: what the elder asked for and when, the
 * family's answer, the work order's visit, and what a manager can do — choose who carries out a
 * visit nobody holds, a caregiver or an Open Platform provider they pick, and call the request off.
 * While a provider has not answered, the manager may still assign a caregiver, which withdraws the
 * offer. `onAssign`/`onOffer`/`onCancel` do the request and reject with the error to show. Key it
 * by request id so dialogs reset between rows.
 */
export function ExtraServicePanel({
  request,
  elderName,
  caregiverName,
  onAssign,
  onOffer,
  onCancel,
}: {
  request: ManagedValueAddedServiceRequest
  elderName: string
  caregiverName: string | null
  onAssign: (caregiverId: number) => Promise<void>
  onOffer: (providerId: number) => Promise<void>
  onCancel: () => Promise<void>
}) {
  const [picking, setPicking] = useState<'caregiver' | 'provider' | null>(null)
  const [confirmingCancel, setConfirmingCancel] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const service = request.serviceName ?? 'Extra service'
  const when = formatWhen(request.requestedSchedule)
  const tag = stateTag(request)
  const awaitingProvider = awaitsProvider(request)
  const canAssign = request.needsCaregiver || awaitingProvider

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
          <Detail label="Asked">{formatWhen(request.createdAt)} by {request.requestedByFamily ? 'a family member' : 'the elder'}</Detail>
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
          {request.providerName && (
            <Detail label="Provider">
              {request.providerName} · {providerLabel(request.providerJobStatus)}
            </Detail>
          )}
        </dl>
      </section>

      <footer className={styles.footer}>
        <Guidance request={request} />
        {error && (
          <Callout tone="danger" role="alert">
            {error}
          </Callout>
        )}
        {(canAssign || canCancel(request)) && (
          <div className={styles.actions}>
            {canAssign && (
              <Button variant="primary" block disabled={busy} onClick={() => setPicking('caregiver')}>
                {awaitingProvider ? 'Assign caregiver instead' : 'Assign caregiver'}
              </Button>
            )}
            {request.needsCaregiver && (
              <Button block disabled={busy} onClick={() => setPicking('provider')}>
                Offer to provider
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

      {picking === 'caregiver' && (
        <VisitCoverModal
          requestId={request.id}
          title={`${service} · ${elderName}`}
          meta={when}
          assign={async (caregiverId) => {
            await onAssign(caregiverId)
            setPicking(null)
          }}
          onClose={() => setPicking(null)}
        />
      )}

      {picking === 'provider' && (
        <ProviderOfferModal
          requestId={request.id}
          title={`${service} · ${elderName}`}
          meta={when}
          offer={async (providerId) => {
            await onOffer(providerId)
            setPicking(null)
          }}
          onClose={() => setPicking(null)}
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
    const declined = request.providerJobStatus === 'DECLINED' || request.providerJobStatus === 'CANCELLED'
    return (
      <MetaText className={styles.effect}>
        {declined && request.providerName ? `${request.providerName} didn’t take it. ` : ''}
        Nobody is on the visit. Assign a caregiver, or offer it to a provider, before it is due, or it becomes an exception
        when it starts.
      </MetaText>
    )
  }
  if (awaitsProvider(request)) {
    return (
      <MetaText className={styles.effect}>
        Offered to {request.providerName}; waiting for its answer. If it declines or doesn’t answer in time, the request
        comes back here. Assigning a caregiver now withdraws the offer.
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
        Waiting for the family to approve in the family app. Approval creates the visit, with nobody on it, for you to
        assign.
      </MetaText>
    )
  }
  return null
}

/** Offered to a provider that hasn't answered, and the visit still waits for it. */
function awaitsProvider(request: ManagedValueAddedServiceRequest): boolean {
  return (
    request.status === 'DISPATCHED' &&
    request.providerJobStatus === 'NEW' &&
    request.caregiverId === null &&
    request.visitStatus === 'SCHEDULED'
  )
}

const PROVIDER_LABELS: Record<string, string> = {
  NEW: 'offered, no answer yet',
  ACCEPTED: 'accepted',
  COMPLETED: 'completed',
  DECLINED: 'declined',
  CANCELLED: 'offer called off',
}

function providerLabel(status: string | null | undefined): string {
  return status ? (PROVIDER_LABELS[status] ?? status.toLowerCase()) : 'offered'
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
