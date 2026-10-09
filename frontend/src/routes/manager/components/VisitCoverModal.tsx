import { useState } from 'react'
import { Avatar, Button, Callout, Eyebrow, ListRow, MetaText, Modal } from '../../../shared/components/ui'
import type { CaregiverCoverOption } from '../../../features/value-added-services/types'
import { useCaregiverCoverOptions } from '../lib/useExtraServices'
import styles from './VisitCoverModal.module.css'

/**
 * Picks who takes an extra service's visit. The list comes from the same search an absence's
 * re-rostering runs: who passes every hard rule, best first with why, then who does not and
 * the rule that stops them, dimmed and not assignable. The server checks again on save.
 */
export function VisitCoverModal({
  requestId,
  title,
  meta,
  assign,
  onClose,
}: {
  requestId: number
  title: string
  meta: string
  /** Saves the pick; rejects with the error to show. */
  assign: (caregiverId: number) => Promise<void>
  onClose: () => void
}) {
  const options = useCaregiverCoverOptions(requestId)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  async function handleAssign(option: CaregiverCoverOption) {
    setSaving(true)
    setSaveError(null)
    try {
      await assign(option.caregiverId)
    } catch (e) {
      setSaveError(`Could not assign ${option.name}: ${e instanceof Error ? e.message : 'something went wrong'}`)
      setSaving(false)
    }
  }

  let list
  if (options.isPending) {
    list = <MetaText>Finding who is free…</MetaText>
  } else if (options.isError) {
    list = <MetaText tone="danger">Could not load caregivers: {options.error.message}</MetaText>
  } else {
    const eligible = options.data.filter((option) => option.eligible)
    list = (
      <>
        {eligible.length === 0 && (
          <Callout tone="neutral">
            Nobody passes every rule at this time. Cancel the request, or ask the family for another time.
          </Callout>
        )}
        <div role="listbox" aria-label="Caregivers" className={styles.list}>
          {options.data.map((option) => (
            <ListRow
              key={option.caregiverId}
              dimmed={!option.eligible}
              leading={<Avatar size={32} />}
              title={option.name}
              meta={option.reason ?? undefined}
              trailing={
                option.eligible ? (
                  <>
                    {option.rank === 1 && <Eyebrow>Best match</Eyebrow>}
                    <Button disabled={saving} onClick={() => void handleAssign(option)}>
                      Assign
                    </Button>
                  </>
                ) : undefined
              }
            />
          ))}
        </div>
      </>
    )
  }

  return (
    <Modal
      eyebrow="Assign caregiver"
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
