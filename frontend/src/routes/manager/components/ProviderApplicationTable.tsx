import { DataTable } from '../../../shared/components/ui'
import type { DataTableColumn } from '../../../shared/components/ui'
import type { ProviderApplication } from '../data/providerApplications'
import { formatReceived } from '../lib/applications'
import { listLabel } from '../lib/providers'
import styles from './ProviderApplicationTable.module.css'

/**
 * Provider applications waiting for an answer: the organisation and who to contact, the
 * services it offers, where it works and when it came in. A row opens in the application panel.
 */
export function ProviderApplicationTable({
  rows,
  selectedId,
  onSelect,
}: {
  rows: ProviderApplication[]
  selectedId: number | null
  onSelect: (row: ProviderApplication) => void
}) {
  const columns: DataTableColumn<ProviderApplication>[] = [
    {
      key: 'organisation',
      label: 'Organisation',
      width: 'minmax(0, 1.3fr)',
      render: (row) => (
        <div className={styles.stack}>
          <span className={styles.name}>{row.organisationName}</span>
          <span className={styles.sub}>{row.contactEmail}</span>
        </div>
      ),
    },
    {
      key: 'services',
      label: 'Services',
      width: 'minmax(0, 1fr)',
      render: (row) => <span className={styles.services}>{listLabel(row.services) ?? '—'}</span>,
    },
    {
      key: 'area',
      label: 'Area',
      width: '88px',
      render: (row) => <span className={styles.mono}>{listLabel(row.serviceArea) ?? '—'}</span>,
    },
    {
      key: 'received',
      label: 'Received',
      width: '88px',
      align: 'right',
      render: (row) => <span className={styles.received}>{formatReceived(row.submittedAt)}</span>,
    },
  ]

  return (
    <DataTable
      label="Provider applications"
      columns={columns}
      rows={rows}
      rowKey={(row) => String(row.id)}
      onRowClick={onSelect}
      selectedKey={selectedId === null ? null : String(selectedId)}
      empty="No applications waiting. New ones from the provider workbench appear here."
    />
  )
}
