import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { refreshFamilyElders } from '../../../features/family-elders/queries'
import { useSelectedElder } from '../components/selectedElder'
import { decideIncomingFamilyBinding, getIncomingFamilyBindings } from '../../../features/family-binding/api'
import type { IncomingFamilyBinding } from '../../../features/family-binding/api'

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

  return <section style={{ padding: '2rem', maxWidth: 860 }}>
    <p>YOUR FAMILY'S CARE</p>
    <h1>Family bindings</h1>
    <p>Review requests from elders who would like to share their care information with you.</p>
    <button type="button" onClick={() => void refresh()}>Refresh</button>
    {error && <p role="alert">{error}</p>}
    {notice && <p role="status">{notice}</p>}
    {loading ? <p>Loading bindings...</p> : bindings.length === 0 ? <p>No binding requests.</p> :
      <ul style={{ listStyle: 'none', padding: 0 }}>
        {bindings.map(binding => <li key={binding.id} style={{ border: '1px solid #d5dedb', borderRadius: 12, padding: 20, marginTop: 16 }}>
          <h2>{binding.elderName}</h2>
          <p>Relationship: {binding.relationship.replaceAll('_', ' ')}</p>
          <p>Access: {binding.accessScope === 'FULL' ? 'Full access' : 'Read-only'}</p>
          <p>Status: {binding.status.replaceAll('_', ' ')}</p>
          {binding.status === 'PENDING_CONFIRMATION' && <div style={{ display: 'flex', gap: 12 }}>
            <button type="button" disabled={busy !== null} onClick={() => void decide(binding.id, true)}>Confirm binding</button>
            <button type="button" disabled={busy !== null} onClick={() => void decide(binding.id, false)}>Reject binding</button>
          </div>}
        </li>)}
      </ul>}
  </section>
}
