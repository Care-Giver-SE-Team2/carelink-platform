import { useState } from 'react'
import { Avatar, Button, Callout, ListRow, MetaText, Modal } from '../../../shared/components/ui'
import type { ProviderOption } from '../../../features/value-added-services/types'
import { useProviderOptions } from '../lib/useExtraServices'
import styles from './VisitCoverModal.module.css'

/**
 * Picks the Open Platform provider an extra service's visit is offered to: the active providers that
 * offer the service and have not been offered this request before. The provider is sent the job at
 * once and has until its answer-by time; a decline or no answer brings the request back here.
 */
export function ProviderOfferModal({
  requestId,
  title,
  meta,
  offer,
  onClose,
}: {
  requestId: number
  title: string
  meta: string
  /** Sends the offer; rejects with the error to show. */
  offer: (providerId: number) => Promise<void>
  onClose: () => void
}) {
  const options = useProviderOptions(requestId)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  async function handleOffer(option: ProviderOption) {
    setSaving(true)
    setSaveError(null)
    try {
      await offer(option.providerId)
    } catch (e) {
      setSaveError(`Could not offer it to ${option.organisationName}: ${e instanceof Error ? e.message : 'something went wrong'}`)
      setSaving(false)
    }
  }

  let list
  if (options.isPending) {
    list = <MetaText>Finding providers…</MetaText>
  } else if (options.isError) {
    list = <MetaText tone="danger">Could not load providers: {options.error.message}</MetaText>
  } else if (options.data.length === 0) {
    list = (
      <Callout tone="neutral">
        No provider can take it: none that is active offers this service and hasn’t been offered it, or the start is under
        five hours away. Assign a caregiver instead.
      </Callout>
    )
  } else {
    list = (
      <div role="listbox" aria-label="Providers" className={styles.list}>
        {options.data.map((option) => (
          <ListRow
            key={option.providerId}
            leading={<Avatar size={32} />}
            title={option.organisationName}
            trailing={
              <Button disabled={saving} onClick={() => void handleOffer(option)}>
                Offer
              </Button>
            }
          />
        ))}
      </div>
    )
  }

  return (
    <Modal
      eyebrow="Offer to provider"
      eyebrowTone="accent"
      title={title}
      meta={meta}
      width={500}
      onClose={onClose}
      footer={
        <Button variant="ghost" size="md" onClick={onClose}>
          Cancel
        </Button>
      }
    >
      {saveError && (
        <Callout tone="danger" role="alert">
          {saveError}
        </Callout>
      )}
      {list}
    </Modal>
  )
}
