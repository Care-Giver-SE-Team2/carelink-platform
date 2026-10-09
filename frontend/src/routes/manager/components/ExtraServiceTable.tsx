import { DataTable, Tag } from '../../../shared/components/ui'
import type { DataTableColumn } from '../../../shared/components/ui'
import type { ManagedValueAddedServiceRequest } from '../../../features/value-added-services/types'
import { formatWhen, stateTag } from '../lib/extraServices'
import styles from './ExtraServiceTable.module.css'

/**
 * Extra-service requests: the service and who it is for, when it is wanted, who is on its
 * visit, and where it stands. A visit nobody holds is tinted danger. A row opens in the panel.
 */
export function ExtraServiceTable({
  rows,
  selectedId,
  onSelect,
  elderName,
  caregiverName,
  empty,
}: {
  rows: ManagedValueAddedServiceRequest[]
  selectedId: number | null
  onSelect: (row: ManagedValueAddedServiceRequest) => void
  elderName: (id: number) => string
  caregiverName: (id: number) => string
  empty: string
}) {
  const columns: DataTableColumn<ManagedValueAddedServiceRequest>[] = [
    {
      key: 'service',
      label: 'Service',
      width: 'minmax(0, 1.2fr)',
      render: (row) => (
        <div className={styles.stack}>
          <span className={styles.name}>{row.serviceName ?? 'Service #' + row.valueAddedServiceId}</span>
          <span className={styles.sub}>#{row.id}</span>
        </div>
      ),
    },
    {
      key: 'elder',
      label: 'Elder',
      width: 'minmax(0, 1.1fr)',
      render: (row) => <span className={styles.person}>{elderName(row.elderId)}</span>,
    },
    {
      key: 'when',
      label: 'Wanted',
      width: '128px',
      render: (row) => <span className={styles.when}>{formatWhen(row.requestedSchedule)}</span>,
    },
    {
      key: 'caregiver',
      label: 'Caregiver',
      width: 'minmax(0, 1fr)',
      render: (row) =>
        row.caregiverId === null ? (
          <span className={styles.none}>—</span>
        ) : (
          <span className={styles.person}>{caregiverName(row.caregiverId)}</span>
        ),
    },
    {
      key: 'state',
      label: 'State',
      width: '136px',
      align: 'right',
      render: (row) => {
        const tag = stateTag(row)
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
      label="Extra service requests"
      columns={columns}
      rows={rows}
      rowKey={(row) => String(row.id)}
      rowTone={(row) => (row.needsCaregiver ? 'danger' : null)}
      onRowClick={onSelect}
      selectedKey={selectedId === null ? null : String(selectedId)}
      empty={empty}
    />
  )
}
