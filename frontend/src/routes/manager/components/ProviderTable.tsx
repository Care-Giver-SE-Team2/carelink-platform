import { DataTable } from '../../../shared/components/ui'
import type { DataTableColumn } from '../../../shared/components/ui'
import type { Provider } from '../data/providerApplications'
import { formatReceived } from '../lib/applications'
import { listLabel, providerRef } from '../lib/providers'
import styles from './ProviderApplicationTable.module.css'

/**
 * Every approved provider: name and reference, the services it can be dispatched, where it
 * works and when it was approved. A row opens in the provider panel.
 */
export function ProviderTable({
  rows,
  selectedId,
  onSelect,
}: {
  rows: Provider[]
  selectedId: number | null
  onSelect: (row: Provider) => void
}) {
  const columns: DataTableColumn<Provider>[] = [
    {
      key: 'provider',
      label: 'Provider',
      width: 'minmax(0, 1.3fr)',
      render: (row) => (
        <div className={styles.stack}>
          <span className={styles.name}>{row.organisationName}</span>
          <span className={styles.sub}>{providerRef(row.providerId)}</span>
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
      key: 'approved',
      label: 'Approved',
      width: '88px',
      align: 'right',
      render: (row) => <span className={styles.received}>{formatReceived(row.approvedAt)}</span>,
    },
  ]

  return (
    <DataTable
      label="Providers"
      columns={columns}
      rows={rows}
      rowKey={(row) => String(row.providerId)}
      onRowClick={onSelect}
      selectedKey={selectedId === null ? null : String(selectedId)}
      empty="No providers yet. Approved applications appear here."
    />
  )
}
