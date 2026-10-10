import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { refreshFamilyElders } from '../../../features/family-elders/queries'
import { useSelectedElder } from '../components/selectedElder'
import { decideIncomingFamilyBinding, getIncomingFamilyBindings } from '../../../features/family-binding/api'
import type { IncomingFamilyBinding } from '../../../features/family-binding/api'
import styles from '../elders/FamilyElders.module.css'

export function FamilyBindingsPage() {
  const client = useQueryClient()
  const { setElderId } = useSelectedElder()
  const [bindings, setBindings] = useState<IncomingFamilyBinding[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  async function refresh() {
    setError('')
    try { setBindings(await getIncomingFamilyBindings()) }
    catch { setError('Unable to load family binding requests.') }
    finally { setLoading(false) }
  }
  useEffect(() => { void refresh() }, [])

  async function decide(id: number, approve: boolean) {
    setBusy(id); setError(''); setNotice('')
    try {
      const decided = await decideIncomingFamilyBinding(id, approve)
      if (approve) setElderId(decided.elderId)
      await refreshFamilyElders(client)
      setNotice(approve ? 'Binding confirmed.' : 'Binding rejected.')
      await refresh()
    } catch { setError('Unable to update binding. Please refresh and try again.') }
    finally { setBusy(null) }
  }

  // Laid out like My elders, which links here: the same header band, action row and cards.
  return <div className={styles.page}>
    <header className={styles.header}>
      <p className={styles.eyebrow}>Your family's care</p>
      <h1>Family bindings</h1>
      <p>Review requests from elders who would like to share their care information with you.</p>
    </header>
    <div className={styles.content}>
      <div className={styles.actions}>
        <button type="button" onClick={() => void refresh()}>Refresh</button>
      </div>
      {error && <p className={styles.error} role="alert">{error}</p>}
      {notice && <p className={styles.notice} role="status">{notice}</p>}
      {loading ? <p role="status">Loading bindings…</p> : bindings.length === 0 ? <p className={styles.card}>No binding requests.</p> :
        <ul className={styles.list}>
          {bindings.map(binding => <li key={binding.id} className={styles.card}>
            <h2>{binding.elderName}</h2>
            <p className={styles.meta}>Relationship: {binding.relationship.replaceAll('_', ' ')}</p>
            <p className={styles.meta}>Access: {binding.accessScope === 'FULL' ? 'Full access' : 'Read-only'}</p>
            <p className={styles.meta}>Status: {binding.status.replaceAll('_', ' ')}</p>
            {binding.status === 'PENDING_CONFIRMATION' && <div className={styles.actions}>
              <button type="button" disabled={busy !== null} onClick={() => void decide(binding.id, true)}>Confirm binding</button>
              <button type="button" disabled={busy !== null} onClick={() => void decide(binding.id, false)}>Reject binding</button>
            </div>}
          </li>)}
        </ul>}
    </div>
  </div>
}
