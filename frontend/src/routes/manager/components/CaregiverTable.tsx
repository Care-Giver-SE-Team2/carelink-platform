import { DataTable, Tag } from '../../../shared/components/ui'
import type { DataTableColumn } from '../../../shared/components/ui'
import { caregiverRef } from '../lib/certifications'
import { STATUS_TAG, certificateSummary, languagesLabel } from '../lib/caregivers'
import type { DirectoryCaregiver } from '../lib/caregivers'
import styles from './CaregiverTable.module.css'

/**
 * Every caregiver: name and reference, sector, languages, a one-line count of their
 * certificates, and their status. A row opens in the caregiver panel.
 */
export function CaregiverTable({
  rows,
  selectedId,
  onSelect,
}: {
  rows: DirectoryCaregiver[]
  selectedId: number | null
  onSelect: (row: DirectoryCaregiver) => void
}) {
  const columns: DataTableColumn<DirectoryCaregiver>[] = [
    {
      key: 'caregiver',
      label: 'Caregiver',
      width: 'minmax(0, 1.3fr)',
      render: (row) => (
        <div className={styles.stack}>
          <span className={styles.name}>{row.fullName}</span>
          <span className={styles.sub}>{caregiverRef(row.id)}</span>
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
      key: 'certificates',
      label: 'Certificates',
      width: 'minmax(0, 1.1fr)',
      render: (row) => <span className={styles.certificates}>{certificateSummary(row.certificates)}</span>,
    },
    {
      key: 'status',
      label: 'Status',
      width: '112px',
      align: 'right',
      render: (row) => {
        const tag = STATUS_TAG[row.status]
        return (
          <Tag compact tone={tag.tone}>
            {tag.label}
          </Tag>
        )
      },
    },
  ]

  return (
    <DataTable
      label="Caregivers"
      columns={columns}
      rows={rows}
      rowKey={(row) => String(row.id)}
      onRowClick={onSelect}
      selectedKey={selectedId === null ? null : String(selectedId)}
      empty="No caregivers yet. Approved applications appear here."
    />
  )
}
