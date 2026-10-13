import { useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { Callout, MetaText, SectionHeader } from '../../../shared/components/ui'
import { ManagerShell } from '../components/ManagerShell'
import { ProviderApplicationPanel } from '../components/ProviderApplicationPanel'
import { ProviderApplicationTable } from '../components/ProviderApplicationTable'
import { ProviderPanel } from '../components/ProviderPanel'
import { ProviderTable } from '../components/ProviderTable'
import {
  approveProviderApplication,
  declineProviderApplication,
  reinstateProvider,
  suspendProvider,
} from '../data/providerApplications'
import type { ProviderApplication } from '../data/providerApplications'
import { nextApplicationId } from '../lib/caregivers'
import { useProviderApplications, useProviders } from '../lib/useProviderApplications'
import styles from './Providers.module.css'

/**
 * Providers — PT01. Third-party providers apply from the Partner Portal with their
 * organisation, the services they offer and their webhook; the applications wait at the top
 * until the manager approves (which gives the provider access to the Open API) or declines with
 * a reason. Below is every approved provider and how its integration is doing; one can be
 * suspended and reinstated. Either kind of row opens in the panel on the right.
 * The open row lives in the URL (?application=14, ?provider=109).
 */
export default function Providers() {
  const [params, setParams] = useSearchParams()
  const queryClient = useQueryClient()
  const applications = useProviderApplications()
  const providers = useProviders()
  const [notice, setNotice] = useState<string | null>(null)
  // The application being answered stays open until the URL names the row to open next (the
  // router applies that a render later), so the panel never flashes whatever row the refreshed
  // lists would fall back to in between. `params` is the URL it was answered from.
  const [deciding, setDeciding] = useState<{ row: ProviderApplication; params: string } | null>(null)

  const pending = useMemo(() => applications.data ?? [], [applications.data])
  const approved = useMemo(() => providers.data ?? [], [providers.data])

  // An explicit choice wins; otherwise the newest application, else the first provider.
  const applicationParam = Number(params.get('application')) || null
  const providerParam = Number(params.get('provider')) || null
  const chosenApplication = pending.find((a) => a.id === applicationParam) ?? null
  const chosenProvider = approved.find((p) => p.providerId === providerParam) ?? null
  const stillDeciding = deciding !== null && deciding.params === params.toString() ? deciding.row : null
  const selectedApplication = stillDeciding ?? chosenApplication ?? (providerParam ? null : (pending[0] ?? null))
  const selectedProvider = selectedApplication ? null : (chosenProvider ?? approved[0] ?? null)

  const go = (next: { application?: number | null; provider?: number | null }) => {
    const query: Record<string, string> = {}
    if (next.application) query.application = String(next.application)
    if (next.provider) query.provider = String(next.provider)
    setParams(query, { replace: true })
  }

  /** Opens a row the manager clicked; the last decision's notice goes with the row it was about. */
  const open = (next: { application?: number | null; provider?: number | null }) => {
    setNotice(null)
    setDeciding(null)
    go(next)
  }

  /**
   * Answers the application, then opens the next one waiting — or, once none are left, the
   * provider an approval just registered (`action` resolves to its id; null for a decline).
   */
  async function decide(row: ProviderApplication, action: () => Promise<number | null>, done: string) {
    const nextId = nextApplicationId(pending, row.id)
    setDeciding({ row, params: params.toString() })
    let providerId: number | null
    try {
      providerId = await action()
    } catch (err) {
      setDeciding(null)
      throw err
    }
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['providerApplications'] }),
      queryClient.invalidateQueries({ queryKey: ['providers'] }),
    ])
    setNotice(done)
    go(nextId !== null ? { application: nextId } : { provider: providerId })
  }

  return (
    <ManagerShell headerContext="Providers">
      <div className={styles.layout}>
        <div className={styles.lists}>
          {notice && (
            <Callout tone="info" role="status" className={styles.notice}>
              {notice}
            </Callout>
          )}

          <section aria-labelledby="provider-applications-heading">
            <SectionHeader
              id="provider-applications-heading"
              title="Applications"
              meta={applications.isSuccess ? `${pending.length} waiting` : undefined}
            />
            {applications.isError ? (
              <p className={styles.status}>Could not load applications.</p>
            ) : applications.isPending ? (
              <p className={styles.status}>Loading applications…</p>
            ) : (
              <ProviderApplicationTable
                rows={pending}
                selectedId={selectedApplication?.id ?? null}
                onSelect={(row) => open({ application: row.id })}
              />
            )}
          </section>

          <section aria-labelledby="provider-list-heading" className={styles.directory}>
            <SectionHeader
              id="provider-list-heading"
              title="All providers"
              meta={providers.isSuccess ? `${approved.length} approved` : undefined}
            />
            {providers.isError ? (
              <p className={styles.status}>Could not load providers.</p>
            ) : providers.isPending ? (
              <p className={styles.status}>Loading providers…</p>
            ) : (
              <ProviderTable
                rows={approved}
                selectedId={selectedProvider?.providerId ?? null}
                onSelect={(row) => open({ provider: row.providerId })}
              />
            )}
          </section>
        </div>

        {selectedApplication ? (
          <ProviderApplicationPanel
            key={`application-${selectedApplication.id}`}
            application={selectedApplication}
            onApprove={(message) =>
              decide(
                selectedApplication,
                async () => (await approveProviderApplication(selectedApplication.id, message)).providerId,
                `${selectedApplication.organisationName} approved. They can now collect their Open API credentials from the Partner Portal.`,
              )
            }
            onDecline={(message) =>
              decide(
                selectedApplication,
                async () => {
                  await declineProviderApplication(selectedApplication.id, message)
                  return null
                },
                `${selectedApplication.organisationName}'s application declined. They'll see your reason in the Partner Portal.`,
              )
            }
          />
        ) : selectedProvider ? (
          <ProviderPanel
            key={`provider-${selectedProvider.providerId}`}
            provider={selectedProvider}
            onSuspend={async () => {
              await suspendProvider(selectedProvider.id)
              await queryClient.invalidateQueries({ queryKey: ['providers'] })
              setNotice(`${selectedProvider.organisationName} suspended. Its tokens no longer work.`)
            }}
            onReinstate={async () => {
              await reinstateProvider(selectedProvider.id)
              await queryClient.invalidateQueries({ queryKey: ['providers'] })
              setNotice(`${selectedProvider.organisationName} reinstated.`)
            }}
          />
        ) : (
          <aside className={styles.emptyPanel} aria-label="Provider detail">
            {providers.isSuccess && applications.isSuccess && <MetaText tone="faint">Nothing to show yet.</MetaText>}
          </aside>
        )}
      </div>
    </ManagerShell>
  )
}
