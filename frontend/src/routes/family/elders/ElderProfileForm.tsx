import { useState } from 'react'
import type { ElderBasicDetails } from '../../../features/family-elders/api'
import { singaporeToday } from '../../../features/schedule/presentation'
import styles from './FamilyElders.module.css'

const textFields = [
  { key: 'fullName', label: 'Full name', type: 'text', maxLength: 100, required: true },
  { key: 'dateOfBirth', label: 'Date of birth', type: 'date', max: singaporeToday },
  { key: 'phone', label: 'Phone', type: 'tel', maxLength: 20 },
  { key: 'address', label: 'Home address', type: 'text', maxLength: 255 },
  { key: 'postalCode', label: 'Postal code', type: 'text', pattern: '[0-9]{6}', maxLength: 6 },
  { key: 'preferredDialects', label: 'Preferred dialects', type: 'text', maxLength: 100 },
] as const

export function ElderProfileForm({ details, busy, error, onSave, onCancel }: {
  details: ElderBasicDetails
  busy: boolean
  error: string
  onSave: (details: ElderBasicDetails) => void
  onCancel: () => void
}) {
  const [draft, setDraft] = useState(details)
  function text(value: string) { return value.trim() || null }

  return <form className={styles.card} onSubmit={(event) => {
    event.preventDefault()
    if (!busy) {
      const normalized = { ...draft, fullName: draft.fullName.trim() }
      for (const field of textFields) {
        if (field.key !== 'fullName') normalized[field.key] = text(draft[field.key] ?? '')
      }
      onSave(normalized)
    }
  }}>
    <h2>Edit basic details</h2>
    <p className={styles.meta}>You can complete these details gradually. Full name is required.</p>
    {error && <p role="alert" className={styles.error}>{error}</p>}
    <fieldset className={styles.fields} disabled={busy}>
      {textFields.map((field) => <label className={styles.field} key={field.key}>
        {field.label}{'required' in field && field.required ? ' *' : ''}
        <input name={field.key} type={field.type} value={draft[field.key] ?? ''}
          required={'required' in field && field.required}
          maxLength={'maxLength' in field ? field.maxLength : undefined}
          max={'max' in field ? field.max() : undefined}
          pattern={'pattern' in field ? field.pattern : undefined}
          inputMode={field.key === 'postalCode' ? 'numeric' : undefined}
          onChange={(event) => setDraft({ ...draft, [field.key]: event.target.value })} />
      </label>)}
      <label className={styles.field}>Gender
        <select value={draft.gender ?? ''} onChange={(event) => setDraft({ ...draft, gender: event.target.value as ElderBasicDetails['gender'] || null })}>
          <option value="">Not recorded</option><option value="MALE">Male</option><option value="FEMALE">Female</option><option value="OTHER">Other</option>
        </select>
      </label>
      <label className={styles.field}>Lives alone
        <select value={draft.livesAlone === null ? '' : String(draft.livesAlone)} onChange={(event) => setDraft({
          ...draft, livesAlone: event.target.value === '' ? null : event.target.value === 'true',
        })}>
          <option value="">Not recorded</option><option value="true">Yes</option><option value="false">No</option>
        </select>
      </label>
      <label className={styles.field}>Mobility
        <select value={draft.mobilityLevel ?? ''} onChange={(event) => setDraft({ ...draft, mobilityLevel: event.target.value as ElderBasicDetails['mobilityLevel'] || null })}>
          <option value="">Not recorded</option><option value="INDEPENDENT">Independent</option>
          <option value="ASSISTIVE_CANE">Uses a walking aid</option><option value="WHEELCHAIR_BEDBOUND">Wheelchair / bedbound</option>
        </select>
      </label>
    </fieldset>
    <div className={styles.actions}>
      <button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save details'}</button>
      <button type="button" disabled={busy} onClick={onCancel}>Cancel</button>
    </div>
  </form>
}
