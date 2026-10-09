import { useMemo } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { FilterChips, MetaText, Pagination } from '../../../shared/components/ui'
import {
  assignValueAddedServiceCaregiver,
  cancelValueAddedServiceRequest,
} from '../../../features/value-added-services/api'
import { ExtraServicePanel } from '../components/ExtraServicePanel'
import { ExtraServiceTable } from '../components/ExtraServiceTable'
import { ManagerShell } from '../components/ManagerShell'
import {
  EXTRA_SERVICE_EMPTY_TEXT,
  EXTRA_SERVICE_FILTERS,
  EXTRA_SERVICE_PAGE_SIZE,
  isExtraServiceFilter,
  matchesFilter,
  selectedRow,
} from '../lib/extraServices'
import type { ExtraServiceFilter } from '../lib/extraServices'
import { useCaregivers } from '../lib/useCaregivers'
import { useElders } from '../lib/useElders'
import { useExtraServices } from '../lib/useExtraServices'
import styles from './ExtraServices.module.css'

/**
 * Extra services — every service an elder has asked for on top of their care plan, from the
 * family's answer to the visit that carries it out. Approval puts the elder's primary caregiver
 * on the visit when they are free; when they are not, the visit waits here for the manager to
 * staff it. The manager can also call a request off before its visit starts. Opens on the
 * visits needing a caregiver when there are any. The filter, page and open row live in the URL
 * (?filter=needs&page=2&id=41); a manager-bell link names the visit instead (?visit=88).
 */
export default function ExtraServices() {
  const [params, setParams] = useSearchParams()
  const queryClient = useQueryClient()
  const requests = useExtraServices()
  const elders = useElders()
  const caregivers = useCaregivers()

  const rows = useMemo(() => requests.data ?? [], [requests.data])
  const filterParam = params.get('filter')
  const visitParam = Number(params.get('visit')) || null
  // A bell link names one visit, which may not need a caregiver: show everything so its row is in the table.
  const filter: ExtraServiceFilter = isExtraServiceFilter(filterParam)
    ? filterParam
    : visitParam === null && rows.some((row) => row.needsCaregiver)
      ? 'needs'
      : 'all'
  const matching = rows.filter((row) => matchesFilter(row, filter))
  const page = Math.max(1, Number(params.get('page')) || 1)
  const pageCount = Math.max(1, Math.ceil(matching.length / EXTRA_SERVICE_PAGE_SIZE))
  const currentPage = Math.min(page, pageCount)
  const pageRows = matching.slice((currentPage - 1) * EXTRA_SERVICE_PAGE_SIZE, currentPage * EXTRA_SERVICE_PAGE_SIZE)
  const selected = selectedRow(rows, pageRows, Number(params.get('id')) || null, visitParam)

  const elderNames = useMemo(() => new Map((elders.data ?? []).map((e) => [Number(e.id), e.name])), [elders.data])
  const caregiverNames = useMemo(
    () => new Map((caregivers.data ?? []).map((c) => [c.id, c.fullName])),
    [caregivers.data],
  )
  const elderName = (id: number) => elderNames.get(id) ?? `Elder #${id}`
  const caregiverName = (id: number) => caregiverNames.get(id) ?? `Caregiver #${id}`

  const go = (next: { filter?: ExtraServiceFilter; page?: number; id?: number | null }) => {
    const id = next.id === undefined ? (selected?.id ?? null) : next.id
    setParams(
      {
        filter: next.filter ?? filter,
        page: String(next.page ?? currentPage),
        ...(id !== null && { id: String(id) }),
      },
      { replace: true },
    )
  }

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ['extra-services'] })
    // A covered or called-off visit changes the roster and the Today board too.
    await queryClient.invalidateQueries({ queryKey: ['roster'] })
  }

  return (
    <ManagerShell headerContext="Extra services">
      <div className={styles.layout}>
        <section className={styles.list} aria-label="Extra service requests">
          <div className={styles.toolbar}>
            <FilterChips
              label="Extra service filter"
              value={filter}
              onChange={(value) => go({ filter: value, page: 1, id: null })}
              options={EXTRA_SERVICE_FILTERS.map((option) => ({
                ...option,
                count: rows.filter((row) => matchesFilter(row, option.value)).length,
              }))}
            />
            <p className={styles.helper}>
              Services elders ask for on top of their care plan. No payment is taken; approval by the family creates a
              visit. Newest first.
            </p>
          </div>
          {requests.isError ? (
            <p className={styles.status}>Could not load extra services.</p>
          ) : requests.isPending ? (
            <p className={styles.status}>Loading extra services…</p>
          ) : (
            <>
              <ExtraServiceTable
                rows={pageRows}
                selectedId={selected?.id ?? null}
                onSelect={(row) => go({ id: row.id })}
                elderName={elderName}
                caregiverName={caregiverName}
                empty={EXTRA_SERVICE_EMPTY_TEXT[filter]}
              />
              {matching.length > EXTRA_SERVICE_PAGE_SIZE && (
                <div className={styles.pagination}>
                  <Pagination
                    page={currentPage}
                    pageSize={EXTRA_SERVICE_PAGE_SIZE}
                    total={matching.length}
                    noun="requests"
                    onPageChange={(next) => go({ page: next, id: null })}
                  />
                </div>
              )}
            </>
          )}
        </section>

        {selected ? (
          <ExtraServicePanel
            key={selected.id}
            request={selected}
            elderName={elderName(selected.elderId)}
            caregiverName={selected.caregiverId === null ? null : caregiverName(selected.caregiverId)}
            onAssign={async (caregiverId) => {
              await assignValueAddedServiceCaregiver(selected.id, caregiverId)
              await refresh()
            }}
            onCancel={async () => {
              await cancelValueAddedServiceRequest(selected.id)
              await refresh()
            }}
          />
        ) : (
          <aside className={styles.emptyPanel} aria-label="Extra service detail">
            {requests.isSuccess && <MetaText tone="faint">Nothing to show.</MetaText>}
          </aside>
        )}
      </div>
    </ManagerShell>
  )
}
