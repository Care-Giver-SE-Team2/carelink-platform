import type { ReactNode } from 'react'
import { DataTable, VisitStateBadge } from '../../../shared/components/ui'
import type { DataTableColumn } from '../../../shared/components/ui'
import type { Visit } from '../data/today'
import styles from './VisitRosterTable.module.css'

const COLUMNS: DataTableColumn<Visit>[] = [
  { key: 'time', label: 'Time', width: '74px', render: (v) => <span className={styles.time}>{v.time}</span> },
  {
    key: 'elder',
    label: 'Elder',
    width: '1.3fr',
    render: (v) => (
      <span className={styles.elder}>{v.elder.name}</span>
    ),
  },
  {
    key: 'caregiver',
    label: 'Caregiver',
    width: '1.2fr',
    render: (v) =>
      v.caregiver ? (
        <span className={styles.caregiver}>{v.caregiver.name}</span>
      ) : (
        <span className={styles.unassigned}>Unassigned</span>
      ),
  },
  { key: 'service', label: 'Service', width: '1fr', render: (v) => <span className={styles.service}>{v.service}</span> },
  {
    key: 'attendance',
    label: 'Check-in',
    width: '120px',
    render: ({ attendance }) =>
      attendance ? (
        <span className={styles.attendance}>
          <span>
            in {attendance.checkedIn}
            {attendance.lateMinutes > 0 && <span className={styles.late}> +{attendance.lateMinutes}m</span>}
          </span>
          {attendance.checkedOut && <span className={styles.out}>out {attendance.checkedOut}</span>}
        </span>
      ) : (
        <span className={styles.noAttendance}>—</span>
      ),
  },
  { key: 'state', label: 'State', width: '150px', render: (v) => <VisitStateBadge state={v.state} /> },
]

/**
 * Today's visits with when the caregiver checked in and out, an exception tinted danger and an
 * unassigned visit tinted for the model.
 */
export function VisitRosterTable({ visits, footer, empty }: { visits: Visit[]; footer?: ReactNode; empty?: ReactNode }) {
  return (
    <DataTable
      label="Visit roster"
      columns={COLUMNS}
      rows={visits}
      rowKey={(v) => v.id}
      rowTone={(v) => (v.state === 'exception' ? 'danger' : v.caregiver ? null : 'info')}
      footer={footer}
      empty={empty}
    />
  )
}
