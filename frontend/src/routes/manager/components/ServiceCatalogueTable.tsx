import { DataTable, Tag } from '../../../shared/components/ui'
import type { DataTableColumn } from '../../../shared/components/ui'
import type { ValueAddedService } from '../../../features/value-added-services/types'
import { formatDuration } from '../lib/serviceCatalogue'
import styles from './ExtraServiceTable.module.css'

/** The catalogue of extra services: what each is, how long its visit lasts, and whether it can be asked for. */
export function ServiceCatalogueTable({
  rows,
  selectedId,
  onSelect,
}: {
  rows: ValueAddedService[]
  selectedId: number | null
  onSelect: (row: ValueAddedService) => void
}) {
  const columns: DataTableColumn<ValueAddedService>[] = [
    {
      key: 'service',
      label: 'Service',
      width: 'minmax(0, 2fr)',
      render: (row) => (
        <div className={styles.stack}>
          <span className={styles.name}>{row.name}</span>
          {row.description && <span className={styles.person}>{row.description}</span>}
        </div>
      ),
    },
    {
      key: 'duration',
      label: 'Lasts',
      width: '96px',
      render: (row) => <span className={styles.when}>{formatDuration(row.durationMinutes)}</span>,
    },
    {
      key: 'status',
      label: 'State',
      width: '136px',
      align: 'right',
      render: (row) => (
        <Tag compact tone={row.status === 'AVAILABLE' ? 'success' : 'muted'} className={styles.state}>
          {row.status === 'AVAILABLE' ? 'AVAILABLE' : 'UNAVAILABLE'}
        </Tag>
      ),
    },
  ]

  return (
    <DataTable
      label="Extra service catalogue"
      columns={columns}
      rows={rows}
      rowKey={(row) => String(row.id)}
      onRowClick={onSelect}
      selectedKey={selectedId === null ? null : String(selectedId)}
      empty="No services yet. Add one to let elders and families ask for it."
    />
  )
}
