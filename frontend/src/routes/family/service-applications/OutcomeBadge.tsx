import type { ServiceApplicationOutcome } from '../../../features/service-applications/api'
import styles from '../intake/FamilyIntake.module.css'

/** The intake badge's colours: waiting, planned (good news) and declined (urgent). */
const look: Record<ServiceApplicationOutcome, { status: string; label: string }> = {
  SUBMITTED: { status: 'SUBMITTED', label: 'Submitted' },
  PLANNED: { status: 'APPROVED', label: 'Planned' },
  DECLINED: { status: 'REJECTED', label: 'Declined' },
}

/** Where a service application stands: waiting, in the care plan, or declined by the care team. */
export function OutcomeBadge({ outcome }: { outcome: ServiceApplicationOutcome }) {
  return (
    <span className={styles.badge} data-status={look[outcome].status}>
      <span aria-hidden="true" />
      {look[outcome].label}
    </span>
  )
}
