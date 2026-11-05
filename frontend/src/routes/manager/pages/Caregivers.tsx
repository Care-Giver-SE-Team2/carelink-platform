import { useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { approveCaregiverApplication, rejectCaregiverApplication } from '../../../shared/api/profile'
import type { CaregiverApplication } from '../../../shared/api/profile'
import { Callout, FilterChips, MetaText, Pagination, SearchField } from '../../../shared/components/ui'
import { CaregiverApplicationPanel } from '../components/CaregiverApplicationPanel'
import { CaregiverApplicationTable } from '../components/CaregiverApplicationTable'
import { CaregiverPanel } from '../components/CaregiverPanel'
import { CaregiverTable } from '../components/CaregiverTable'
import { ManagerShell } from '../components/ManagerShell'
import { CAREGIVER_PAGE_SIZE, directoryRows, matchesCaregiverQuery, nextApplicationId } from '../lib/caregivers'
import { useCaregiverApplications } from '../lib/useCaregiverApplications'
import { useCaregivers } from '../lib/useCaregivers'
import { useCredentialRegister } from '../lib/useCertifications'
import { useElders } from '../lib/useElders'
import styles from './Caregivers.module.css'

type View = 'applications' | 'caregivers'

/**
 * Caregivers — MG02. Two lists behind a view switch, each paged on its own. Applications:
 * caregivers apply from the caregiver app with their details and certificates, and wait until
 * the manager approves (which makes them available for rostering; their certificates are still
 * reviewed in Certifications) or declines with a reason. Caregivers: everyone on record, with their certificates and status. A row
 * opens in the panel on the right, and the caregivers can be searched by name or id. The view,
 * the open row, the page and the search live in the URL (?view=applications&application=7,
 * ?view=caregivers&caregiver=12&page=2&q=devi). Without a view it opens on caregivers, unless
 * the URL names an application.
 */
export default function Caregivers() {
  const [params, setParams] = useSearchParams()
  const queryClient = useQueryClient()
  const applications = useCaregiverApplications()
  const caregivers = useCaregivers()
  const register = useCredentialRegister()
  const elders = useElders()
  const [notice, setNotice] = useState<string | null>(null)
  // The application being answered stays open until the URL names the row to open next (the
  // router applies that a render later), so the panel never flashes whatever row the refreshed
  // lists would fall back to in between. `params` is the URL it was answered from.
  const [deciding, setDeciding] = useState<{ row: CaregiverApplication; params: string } | null>(null)

  const pending = useMemo(() => applications.data ?? [], [applications.data])
  const directory = useMemo(
    () => directoryRows(caregivers.data ?? [], register.data ?? []),
    [caregivers.data, register.data],
  )

  const applicationParam = Number(params.get('application')) || null
  const caregiverParam = Number(params.get('caregiver')) || null
  const query = params.get('q') ?? ''
  const viewParam = params.get('view')
  const view: View =
    viewParam === 'applications' || viewParam === 'caregivers'
      ? viewParam
      : applicationParam
        ? 'applications'
        : 'caregivers'

  const matching = useMemo(() => directory.filter((c) => matchesCaregiverQuery(c, query)), [directory, query])

  // The page holding the open row, so a row opened by id is always on screen; else ?page.
  const listRows: { id: number }[] = view === 'applications' ? pending : matching
  const chosenId = view === 'applications' ? applicationParam : caregiverParam
  const chosenIndex = chosenId ? listRows.findIndex((row) => row.id === chosenId) : -1
  const pageCount = Math.max(1, Math.ceil(listRows.length / CAREGIVER_PAGE_SIZE))
  const currentPage =
    chosenIndex >= 0
      ? Math.floor(chosenIndex / CAREGIVER_PAGE_SIZE) + 1
      : Math.min(Math.max(1, Number(params.get('page')) || 1), pageCount)
  const pageStart = (currentPage - 1) * CAREGIVER_PAGE_SIZE
  const applicationRows = pending.slice(pageStart, pageStart + CAREGIVER_PAGE_SIZE)
  const caregiverRows = matching.slice(pageStart, pageStart + CAREGIVER_PAGE_SIZE)

  // An explicit choice wins; otherwise the first row on the page shown.
  const chosenApplication = pending.find((a) => a.id === applicationParam) ?? null
  const chosenCaregiver = matching.find((c) => c.id === caregiverParam) ?? null
  const stillDeciding = deciding !== null && deciding.params === params.toString() ? deciding.row : null
  const selectedApplication =
    view === 'applications' ? (stillDeciding ?? chosenApplication ?? applicationRows[0] ?? null) : null
  const selectedCaregiver = view === 'caregivers' ? (chosenCaregiver ?? caregiverRows[0] ?? null) : null

  type Target = { view?: View; page?: number; application?: number | null; caregiver?: number | null; query?: string }

  /** Another view starts on its first page. The search is kept across views. */
  const go = (next: Target) => {
    const nextView = next.view ?? view
    const page = next.page ?? (nextView === view ? currentPage : 1)
    const q = next.query ?? query
    const search: Record<string, string> = { view: nextView, page: String(page) }
    if (next.application) search.application = String(next.application)
    if (next.caregiver) search.caregiver = String(next.caregiver)
    if (q) search.q = q
    setParams(search, { replace: true })
  }

  /** Opens a row the manager clicked; the last decision's notice goes with the row it was about. */
  const open = (next: Target) => {
    setNotice(null)
    setDeciding(null)
    go(next)
  }

  /**
   * Answers the application, then opens the next one waiting — or, once none are left, the
   * caregiver an approval just created (`action` resolves to its id; null for a decline, which
   * leaves the emptied applications list on screen).
   */
  async function decide(row: CaregiverApplication, action: () => Promise<number | null>, done: string) {
    const nextId = nextApplicationId(pending, row.id)
    setDeciding({ row, params: params.toString() })
    let caregiverId: number | null
    try {
      caregiverId = await action()
    } catch (err) {
      setDeciding(null)
      throw err
    }
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['caregiverApplications'] }),
      queryClient.invalidateQueries({ queryKey: ['caregivers'] }),
      queryClient.invalidateQueries({ queryKey: ['credentials'] }),
    ])
    setNotice(done)
    go(
      nextId !== null || caregiverId === null
        ? { view: 'applications', application: nextId }
        : { view: 'caregivers', caregiver: caregiverId },
    )
  }

  const primaryFor = (caregiverId: number) =>
    elders.data
      ?.filter((elder) => elder.primaryCaregiverId === String(caregiverId))
      .map((elder) => elder.name)

  const directoryLoading = caregivers.isPending || register.isPending
  const viewLoading = view === 'applications' ? applications.isPending : directoryLoading

  return (
    <ManagerShell headerContext="Caregivers">
      <div className={styles.layout}>
        <div className={styles.lists}>
          {notice && (
            <Callout tone="info" role="status" className={styles.notice}>
              {notice}
            </Callout>
          )}

          <div className={styles.toolbar}>
            <FilterChips
              label="Caregivers view"
              options={[
                { value: 'caregivers', label: 'Caregivers', count: directory.length },
                { value: 'applications', label: 'Applications', count: pending.length },
              ]}
              value={view}
              onChange={(next) => open({ view: next })}
            />
            {view === 'caregivers' && (
              <SearchField
                className={styles.search}
                placeholder="Search caregivers by name or ID"
                value={query}
                // A new search starts from the first page, on its first match.
                onChange={(next) => open({ query: next, page: 1 })}
              />
            )}
          </div>

          {view === 'applications' ? (
            <section aria-label="Applications">
              {applications.isError ? (
                <p className={styles.status}>Could not load applications.</p>
              ) : applications.isPending ? (
                <p className={styles.status}>Loading applications…</p>
              ) : (
                <>
                  <CaregiverApplicationTable
                    rows={applicationRows}
                    selectedId={selectedApplication?.id ?? null}
                    onSelect={(row) => open({ application: row.id })}
                  />
                  {pending.length > CAREGIVER_PAGE_SIZE && (
                    <div className={styles.pagination}>
                      <Pagination
                        page={currentPage}
                        pageSize={CAREGIVER_PAGE_SIZE}
                        total={pending.length}
                        noun="applications"
                        onPageChange={(next) => open({ page: next })}
                      />
                    </div>
                  )}
                </>
              )}
            </section>
          ) : (
            <section aria-label="All caregivers">
              {caregivers.isError ? (
                <p className={styles.status}>Could not load caregivers.</p>
              ) : directoryLoading ? (
                <p className={styles.status}>Loading caregivers…</p>
              ) : (
                <>
                  <CaregiverTable
                    rows={caregiverRows}
                    selectedId={selectedCaregiver?.id ?? null}
                    onSelect={(row) => open({ caregiver: row.id })}
                    empty={query.trim() ? `No caregivers match “${query.trim()}”.` : undefined}
                  />
                  {matching.length > CAREGIVER_PAGE_SIZE && (
                    <div className={styles.pagination}>
                      <Pagination
                        page={currentPage}
                        pageSize={CAREGIVER_PAGE_SIZE}
                        total={matching.length}
                        noun="caregivers"
                        onPageChange={(next) => open({ page: next })}
                      />
                    </div>
                  )}
                </>
              )}
            </section>
          )}
        </div>

        {selectedApplication ? (
          <CaregiverApplicationPanel
            key={`application-${selectedApplication.id}`}
            application={selectedApplication}
            onApprove={(message) =>
              decide(
                selectedApplication,
                async () =>
                  (await approveCaregiverApplication(selectedApplication.id, selectedApplication.version, message))
                    .caregiverId,
                `${selectedApplication.fullName} approved and available for rostering.`,
              )
            }
            onDecline={(message) =>
              decide(
                selectedApplication,
                async () => {
                  await rejectCaregiverApplication(selectedApplication.id, selectedApplication.version, message)
                  return null
                },
                `${selectedApplication.fullName}'s application declined. They'll see your reason in the caregiver app.`,
              )
            }
          />
        ) : selectedCaregiver ? (
          <CaregiverPanel
            key={`caregiver-${selectedCaregiver.id}`}
            caregiver={selectedCaregiver}
            primaryFor={primaryFor(selectedCaregiver.id)}
          />
        ) : (
          <aside className={styles.emptyPanel} aria-label="Caregiver detail">
            {!viewLoading && <MetaText tone="faint">Nothing to show yet.</MetaText>}
          </aside>
        )}
      </div>
    </ManagerShell>
  )
}
