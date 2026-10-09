import { DataTable, Tag } from '../../../shared/components/ui'
import type { DataTableColumn } from '../../../shared/components/ui'
import type { CaregiverApplication } from '../data/caregiverApplications'
import { formatReceived } from '../lib/applications'
import { languagesLabel } from '../lib/caregivers'
import styles from './CaregiverApplicationTable.module.css'

/**
 * Caregiver applications waiting for an answer: who applied and with which login, the sector
 * they'll work in, their languages, when it came in and how many certificates they uploaded.
 * A row opens in the application panel.
 */
export function CaregiverApplicationTable({
  rows,
  selectedId,
  onSelect,
}: {
  rows: CaregiverApplication[]
  selectedId: number | null
  onSelect: (row: CaregiverApplication) => void
}) {
  const columns: DataTableColumn<CaregiverApplication>[] = [
    {
      key: 'applicant',
      label: 'Applicant',
      width: 'minmax(0, 1.3fr)',
      render: (row) => (
        <div className={styles.stack}>
          <span className={styles.name}>{row.fullName}</span>
          <span className={styles.sub}>{row.username}</span>
        </div>
      ),
    },
    {
      key: 'languages',
      label: 'Languages',
      width: 'minmax(0, 1fr)',
      render: (row) => <span className={styles.languages}>{languagesLabel(row.dialects) ?? '—'}</span>,
    },
    { key: 'sector', label: 'Sector', width: '68px', render: (row) => <span className={styles.mono}>{row.sector ?? '—'}</span> },
    {
      key: 'received',
      label: 'Received',
      width: '88px',
      render: (row) => <span className={styles.received}>{formatReceived(row.submittedAt)}</span>,
    },
    {
      key: 'certificates',
      label: 'Certificates',
      width: '104px',
      align: 'right',
      render: (row) =>
        row.certificates.length ? (
          <span className={styles.mono}>{row.certificates.length}</span>
        ) : (
          <Tag compact tone="muted">
            NONE
          </Tag>
        ),
    },
  ]

  return (
    <DataTable
      label="Caregiver applications"
      columns={columns}
      rows={rows}
      rowKey={(row) => String(row.id)}
      onRowClick={onSelect}
      selectedKey={selectedId === null ? null : String(selectedId)}
      empty="No applications waiting. New ones from the caregiver app appear here."
    />
  )
}
