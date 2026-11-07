import { useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { Button, MetaText } from '../../../shared/components/ui'
import {
  addValueAddedService,
  removeValueAddedService,
  updateValueAddedService,
} from '../../../features/value-added-services/api'
import { useServiceCatalogue } from '../lib/useExtraServices'
import { ServiceCataloguePanel } from './ServiceCataloguePanel'
import { ServiceCatalogueTable } from './ServiceCatalogueTable'
import styles from '../pages/ExtraServices.module.css'

/**
 * The Catalogue view of the Extra services screen (docs/section5/decisions.md, D21): every service elders and
 * families may ask for, and those withdrawn. A manager adds one, changes what it says and how long it lasts,
 * withdraws it or offers it again, and removes one nobody asked for; a service is never renamed. The open row lives in the URL
 * (?view=catalogue&service=4, or service=new while adding).
 */
export function ServiceCatalogue() {
  const [params, setParams] = useSearchParams()
  const queryClient = useQueryClient()
  const catalogue = useServiceCatalogue()
  const rows = catalogue.data ?? []

  const serviceParam = params.get('service')
  const adding = serviceParam === 'new'
  const selected = adding ? null : (rows.find((row) => row.id === Number(serviceParam)) ?? null)

  const open = (service: string | null) =>
    setParams({ view: 'catalogue', ...(service !== null && { service }) }, { replace: true })

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ['value-added-catalogue'] })
    // The request list names each service from the catalogue.
    await queryClient.invalidateQueries({ queryKey: ['extra-services'] })
  }

  return (
    <div className={styles.layout}>
      <section className={styles.list} aria-label="Extra service catalogue">
        <div className={styles.toolbar}>
          <div className={styles.toolbarRow}>
            <p className={styles.helper}>
              What elders and families can ask for on top of the care plan. A service you add is offered at once; providers
              tick it in their portal to receive it.
            </p>
            <Button variant="primary" onClick={() => open('new')} disabled={adding}>
              + Add service
            </Button>
          </div>
        </div>
        {catalogue.isError ? (
          <p className={styles.status}>Could not load the catalogue.</p>
        ) : catalogue.isPending ? (
          <p className={styles.status}>Loading the catalogue…</p>
        ) : (
          <ServiceCatalogueTable rows={rows} selectedId={selected?.id ?? null} onSelect={(row) => open(String(row.id))} />
        )}
      </section>

      {adding || selected ? (
        <ServiceCataloguePanel
          key={selected?.id ?? 'new'}
          service={selected}
          catalogue={rows}
          onAdd={async (create) => {
            const added = await addValueAddedService(create)
            await refresh()
            open(String(added.id))
          }}
          onSave={async (id, update) => {
            await updateValueAddedService(id, update)
            await refresh()
          }}
          onRemove={async (id) => {
            await removeValueAddedService(id)
            open(null)
            await refresh()
          }}
          onCancel={() => open(null)}
        />
      ) : (
        <aside className={styles.emptyPanel} aria-label="Catalogue detail">
          {catalogue.isSuccess && <MetaText tone="faint">Choose a service to change it, or add one.</MetaText>}
        </aside>
      )}
    </div>
  )
}
