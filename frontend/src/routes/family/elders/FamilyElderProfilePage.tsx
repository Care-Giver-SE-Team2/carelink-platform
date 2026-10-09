import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { updateFamilyElderProfile } from '../../../features/family-elders/api'
import type { ElderBasicDetails } from '../../../features/family-elders/api'
import { elderProfileKey, refreshFamilyElders, useFamilyElderProfile } from '../../../features/family-elders/queries'
import { ApiError } from '../../../shared/api/client'
import { useSelectedElder } from '../components/selectedElder'
import { ElderProfileForm } from './ElderProfileForm'
import styles from './FamilyElders.module.css'

export function FamilyElderProfilePage() {
  const { elderId } = useParams()
  return <ElderProfile key={elderId} id={Number(elderId)} />
}

// A different route starts a fresh editor; never carry one elder's unsaved details into another profile.
function ElderProfile({ id }: { id: number }) {
  const profile = useFamilyElderProfile(id)
  const client = useQueryClient()
  const { setElderId } = useSelectedElder()
  const [editing, setEditing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  const [accessLost, setAccessLost] = useState(false)

  useEffect(() => {
    if (profile.isSuccess) setElderId(id)
  }, [id, profile.isSuccess, setElderId])

  async function save(details: ElderBasicDetails) {
    if (busy) return
    setBusy(true); setError(''); setSaved(false)
    try {
      const updated = await updateFamilyElderProfile(id, details)
      client.setQueryData(elderProfileKey(id), updated)
      setEditing(false); setSaved(true)
      await refreshFamilyElders(client)
    } catch (failure) {
      if (failure instanceof ApiError && [403, 404].includes(failure.status)) {
        // A binding can be revoked while the form is open. Stop showing cached protected details.
        setAccessLost(true); setEditing(false)
        await refreshFamilyElders(client)
      } else setError('Unable to save details. Your changes have not been saved. Please try again.')
    } finally { setBusy(false) }
  }

  const validId = Number.isSafeInteger(id) && id > 0
  if (!validId) return <div className={styles.content}><p role="alert">Invalid elder profile.</p><Link to="/family/elders">Back to My elders</Link></div>
  if (accessLost || profile.isError) return <div className={styles.content}>
    <p role="alert">Unable to view this profile. Check your current binding or try again.</p>
    <div className={styles.actions}>
      <button onClick={async () => { const result = await profile.refetch(); if (result.isSuccess) setAccessLost(false) }}>Try again</button>
      <Link to="/family/elders">Back to My elders</Link>
      <Link to="/family/family-bindings">Family bindings</Link>
    </div>
  </div>
  if (profile.isPending) return <p role="status">Loading elder profile…</p>

  const elder = profile.data
  const mobility = { INDEPENDENT: 'Independent', ASSISTIVE_CANE: 'Uses a walking aid', WHEELCHAIR_BEDBOUND: 'Wheelchair / bedbound' }
  const rows = [
    ['Full name', elder.fullName], ['Date of birth', elder.dateOfBirth],
    ['Gender', elder.gender?.toLowerCase()], ['Phone', elder.phone], ['Home address', elder.address],
    ['Postal code', elder.postalCode], ['Preferred dialects', elder.preferredDialects],
    ['Lives alone', elder.livesAlone === null ? null : elder.livesAlone ? 'Yes' : 'No'],
    ['Mobility', elder.mobilityLevel ? mobility[elder.mobilityLevel] : null],
  ]
  return <div className={styles.page}>
    <header className={styles.header}>
      <Link to="/family/elders">← My elders</Link>
      <p className={styles.eyebrow}>Basic details</p>
      <h1>{elder.fullName}</h1>
      <p>{elder.accessScope === 'FULL' ? 'Full access · You can maintain these basic details.' : 'Read-only · You can view these details.'}</p>
    </header>
    <div className={styles.content}>
      {saved && <p role="status" className={styles.notice}>Basic details saved.</p>}
      {editing && elder.accessScope === 'FULL' ? <ElderProfileForm key={id} details={{
        fullName: elder.fullName, gender: elder.gender, dateOfBirth: elder.dateOfBirth,
        phone: elder.phone, address: elder.address, postalCode: elder.postalCode,
        preferredDialects: elder.preferredDialects, livesAlone: elder.livesAlone, mobilityLevel: elder.mobilityLevel,
      }} busy={busy} error={error} onSave={(details) => void save(details)} onCancel={() => setEditing(false)} /> :
        <section className={styles.card}>
          <h2>Basic details</h2>
          <dl className={styles.details}>{rows.map(([label, value]) => <div key={label}>
            <dt>{label}</dt><dd>{value || 'Not recorded'}</dd>
          </div>)}</dl>
          {elder.accessScope === 'FULL' && <button disabled={busy} onClick={() => { setError(''); setSaved(false); setEditing(true) }}>Edit basic details</button>}
        </section>}
    </div>
  </div>
}
