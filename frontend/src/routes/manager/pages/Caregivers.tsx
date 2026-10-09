import { useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { Callout, MetaText, Pagination, SectionHeader } from '../../../shared/components/ui'
import { CaregiverApplicationPanel } from '../components/CaregiverApplicationPanel'
import { CaregiverApplicationTable } from '../components/CaregiverApplicationTable'
import { CaregiverPanel } from '../components/CaregiverPanel'
import { CaregiverTable } from '../components/CaregiverTable'
import { ManagerShell } from '../components/ManagerShell'
import { approveCaregiverApplication, declineCaregiverApplication } from '../data/caregiverApplications'
import type { CaregiverApplication } from '../data/caregiverApplications'
import { CAREGIVER_PAGE_SIZE, directoryRows, nextApplicationId } from '../lib/caregivers'
import { useApprovedApplicants, useCaregiverApplications } from '../lib/useCaregiverApplications'
import { useCaregivers } from '../lib/useCaregivers'
import { useCredentialRegister } from '../lib/useCertifications'
import { useElders } from '../lib/useElders'
import styles from './Caregivers.module.css'

/**
 * Caregivers — MG02. Caregivers apply from the caregiver app with their details and
 * certificates; the applications wait at the top until the manager approves (which enables the
 * login and publishes every certificate) or declines with a reason. Below is every caregiver,
 * with their certificates and status. Either kind of row opens in the panel on the right.
 * The open row and the caregiver page live in the URL (?application=7, ?caregiver=12&page=2).
 */
export default function Caregivers() {
  const [params, setParams] = useSearchParams()
  const queryClient = useQueryClient()
  const applications = useCaregiverApplications()
  const approved = useApprovedApplicants()
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
    () => directoryRows(caregivers.data ?? [], register.data ?? [], approved.data ?? [], new Date()),
    [caregivers.data, register.data, approved.data],
  )

  const page = Math.max(1, Number(params.get('page')) || 1)
  const pageCount = Math.max(1, Math.ceil(directory.length / CAREGIVER_PAGE_SIZE))
  const currentPage = Math.min(page, pageCount)
  const pageRows = directory.slice((currentPage - 1) * CAREGIVER_PAGE_SIZE, currentPage * CAREGIVER_PAGE_SIZE)

  // An explicit choice wins; otherwise the newest application, else the first caregiver shown.
  const applicationParam = Number(params.get('application')) || null
  const caregiverParam = Number(params.get('caregiver')) || null
  const chosenApplication = pending.find((a) => a.id === applicationParam) ?? null
  const chosenCaregiver = directory.find((c) => c.id === caregiverParam) ?? null
  const stillDeciding = deciding !== null && deciding.params === params.toString() ? deciding.row : null
  const selectedApplication = stillDeciding ?? chosenApplication ?? (caregiverParam ? null : (pending[0] ?? null))
  const selectedCaregiver = selectedApplication ? null : (chosenCaregiver ?? pageRows[0] ?? null)

  const go = (next: { page?: number; application?: number | null; caregiver?: number | null }) => {
    const query: Record<string, string> = { page: String(next.page ?? currentPage) }
    if (next.application) query.application = String(next.application)
    if (next.caregiver) query.caregiver = String(next.caregiver)
    setParams(query, { replace: true })
  }

  /** Opens a row the manager clicked; the last decision's notice goes with the row it was about. */
  const open = (next: { page?: number; application?: number | null; caregiver?: number | null }) => {
    setNotice(null)
    setDeciding(null)
    go(next)
  }

  /**
   * Answers the application, then opens the next one waiting — or, once none are left, the
   * caregiver an approval just created (`action` resolves to its id; null for a decline).
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
    go(nextId !== null ? { application: nextId } : { caregiver: caregiverId })
  }

  const primaryFor = (caregiverId: number) =>
    elders.data
      ?.filter((elder) => elder.primaryCaregiverId === String(caregiverId))
      .map((elder) => elder.name)

  const directoryLoading = caregivers.isPending || register.isPending || approved.isPending

  return (
    <ManagerShell headerContext="Caregivers">
      <div className={styles.layout}>
        <div className={styles.lists}>
          {notice && (
            <Callout tone="info" role="status" className={styles.notice}>
              {notice}
            </Callout>
          )}

          <section aria-labelledby="caregiver-applications-heading">
            <SectionHeader
              id="caregiver-applications-heading"
              title="Applications"
              meta={applications.isSuccess ? `${pending.length} waiting` : undefined}
            />
            {applications.isError ? (
              <p className={styles.status}>Could not load applications.</p>
            ) : applications.isPending ? (
              <p className={styles.status}>Loading applications…</p>
            ) : (
              <CaregiverApplicationTable
                rows={pending}
                selectedId={selectedApplication?.id ?? null}
                onSelect={(row) => open({ application: row.id })}
              />
            )}
          </section>

          <section aria-labelledby="caregiver-list-heading" className={styles.directory}>
            <SectionHeader
              id="caregiver-list-heading"
              title="All caregivers"
              meta={directoryLoading ? undefined : `${directory.length} on record`}
            />
            {caregivers.isError ? (
              <p className={styles.status}>Could not load caregivers.</p>
            ) : directoryLoading ? (
              <p className={styles.status}>Loading caregivers…</p>
            ) : (
              <>
                <CaregiverTable
                  rows={pageRows}
                  selectedId={selectedCaregiver?.id ?? null}
                  onSelect={(row) => open({ caregiver: row.id })}
                />
                {directory.length > CAREGIVER_PAGE_SIZE && (
                  <div className={styles.pagination}>
                    <Pagination
                      page={currentPage}
                      pageSize={CAREGIVER_PAGE_SIZE}
                      total={directory.length}
                      noun="caregivers"
                      onPageChange={(next) => open({ page: next })}
                    />
                  </div>
                )}
              </>
            )}
          </section>
        </div>

        {selectedApplication ? (
          <CaregiverApplicationPanel
            key={`application-${selectedApplication.id}`}
            application={selectedApplication}
            onApprove={(message) =>
              decide(
                selectedApplication,
                async () => (await approveCaregiverApplication(selectedApplication.id, message)).caregiverId,
                `${selectedApplication.fullName} approved. Their login is active and their certificates are published.`,
              )
            }
            onDecline={(message) =>
              decide(
                selectedApplication,
                async () => {
                  await declineCaregiverApplication(selectedApplication.id, message)
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
            {!directoryLoading && applications.isSuccess && <MetaText tone="faint">Nothing to show yet.</MetaText>}
          </aside>
        )}
      </div>
    </ManagerShell>
  )
}
