import { useEffect, useState } from 'react'
import { useWorkbench } from '../workbenchContext'
import { Button, Checkbox, CopyField, WarningIcon } from './ui'
import styles from './RevealSecret.module.css'

const LEAVE_WARNING = "This secret is shown only once. If you leave before storing it, you'll have to rotate it again."

/**
 * A secret just issued, shown this once: copy it, confirm it's stored, and it's gone. Leaving
 * the page before confirming asks first.
 */
export function RevealSecret({ label, value, helper, onDone }: { label: string; value: string; helper?: string; onDone: () => void }) {
  const { setLeaveGuard } = useWorkbench()
  const [stored, setStored] = useState(false)

  useEffect(() => {
    setLeaveGuard(stored ? null : LEAVE_WARNING)
    return () => setLeaveGuard(null)
  }, [stored, setLeaveGuard])

  return (
    <div className={styles.reveal} role="region" aria-label={`New ${label.toLowerCase()}`}>
      <div className={styles.warning}>
        <WarningIcon />
        <div>
          <div className={styles.title}>Your new {label.toLowerCase()} is shown only once.</div>
          <div className={styles.body}>Store it now. The previous one has stopped working.</div>
        </div>
      </div>
      <CopyField label={label} value={value} helper={helper} />
      <Checkbox checked={stored} onChange={setStored}>
        I've stored the new {label.toLowerCase()} somewhere safe.
      </Checkbox>
      <div>
        <Button variant="primary" disabled={!stored} onClick={onDone}>
          Done
        </Button>
      </div>
    </div>
  )
}
