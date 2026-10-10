import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../../shared/api/client'
import { useFamilyElders } from '../../../features/family-account/useFamilyAccount'
import { decideValueAddedServiceRequest } from '../../../features/value-added-services/api'
import type { ValueAddedServiceRequest } from '../../../features/value-added-services/types'
import {
  familyRequestsKey,
  useFamilyValueAddedRequests,
} from '../../../features/value-added-services/useValueAddedServiceQueries'
import { useSelectedElder } from '../components/selectedElder'
import { FamilyValueAddedRequestForm } from './FamilyValueAddedRequestForm'
import styles from './FamilyValueAddedServices.module.css'

export function FamilyValueAddedServicesPage() {
  const elders = useFamilyElders()
  const { elderId } = useSelectedElder()
  const selectedElderId = elderId ?? elders.data?.[0]?.id ?? null
  const client = useQueryClient()
  const query = useFamilyValueAddedRequests(selectedElderId)
  const requests = query.data ?? []
  const loading = query.isLoading
  const [decideError, setDecideError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const loadError = query.error ? (query.error instanceof ApiError ? query.error.message : 'Unable to load service requests.') : null
  const error = decideError ?? loadError

  // Patching the cached list keeps the navigation's waiting count in step with this page.
  function setRequests(update: (current: ValueAddedServiceRequest[]) => ValueAddedServiceRequest[]) {
    client.setQueryData<ValueAddedServiceRequest[]>(familyRequestsKey(selectedElderId), (current) => update(current ?? []))
  }

  async function decide(id: number, decision: 'APPROVED' | 'REJECTED') {
    setBusyId(id)
    setDecideError(null)
    try {
      const updated = await decideValueAddedServiceRequest(id, decision)
      setRequests((current) => current.map((item) => item.id === id ? updated : item))
    } catch (cause) {
      setDecideError(cause instanceof ApiError ? cause.message : 'Unable to record the decision.')
    } finally {
      setBusyId(null)
    }
  }

  const pending = requests.filter((item) => item.status === 'PENDING_APPROVAL')
  const history = requests.filter((item) => item.status !== 'PENDING_APPROVAL')

  return <div className={styles.page}>
    <header className={styles.band}>
      <p className={styles.eyebrow}>EXTRA SERVICES</p>
      <h1>Review service requests</h1>
      <p>Approve or decline requests made by the elder you are following, or book one for them.</p>
    </header>

    {error && <div className={styles.error} role="alert">{error}</div>}
    {loading && <p>Loading requests...</p>}
    {!loading && selectedElderId === null && <p className={styles.card}>No linked elder is available.</p>}

    {selectedElderId !== null && <FamilyValueAddedRequestForm key={selectedElderId} elderId={selectedElderId}
      onCreated={(created) => setRequests((current) => [created, ...current])} />}

    <section className={styles.card}>
      <h2>Pending approval</h2>
      {pending.length === 0 ? <p className={styles.muted}>No requests are waiting for approval.</p> : pending.map((request) => <article className={styles.request} key={request.id}>
        <div><strong>{request.serviceName}</strong><p>{request.specialInstructions || 'No special instructions.'}</p></div>
        <dl>
          <div><dt>Requested for</dt><dd>{request.requestedSchedule ? new Date(request.requestedSchedule).toLocaleString() : 'Not specified'}</dd></div>
          <div><dt>Status</dt><dd>{request.status}</dd></div>
        </dl>
        <div className={styles.actions}>
          <button disabled={busyId === request.id} onClick={() => decide(request.id, 'APPROVED')}>Approve</button>
          <button className={styles.reject} disabled={busyId === request.id} onClick={() => decide(request.id, 'REJECTED')}>Reject</button>
        </div>
      </article>)}
    </section>

    <section className={styles.card}>
      <h2>Decision history</h2>
      {history.length === 0 ? <p className={styles.muted}>No decisions yet.</p> : history.map((request) => <article className={styles.request} key={request.id}>
        <strong>{request.serviceName}</strong>
        <p>Status: <strong>{request.status}</strong>{request.visitId ? ` · Visit #${request.visitId}` : ''}</p>
      </article>)}
    </section>
  </div>
}
