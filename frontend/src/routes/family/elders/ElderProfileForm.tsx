import { useId, useState } from 'react'
import type { InputHTMLAttributes } from 'react'
import type { ElderBasicDetails } from '../../../features/family-elders/api'
import { singaporeToday } from '../../../features/schedule/presentation'
import {
  DIALECTS, dateOfBirthError, dateOfBirthWarning, formatSgPhone, joinDialects, normalizeName, normalizeSgPhone,
  parseDialects, personNameError, phoneError, postalCodeError,
} from '../../../shared/validation/sg'
import styles from './FamilyElders.module.css'

type Draft = {
  fullName: string
  dateOfBirth: string
  phone: string
  address: string
  postalCode: string
  dialects: string[]
  gender: ElderBasicDetails['gender']
  livesAlone: boolean | null
  mobilityLevel: ElderBasicDetails['mobilityLevel']
}
type FieldName = 'fullName' | 'dateOfBirth' | 'phone' | 'address' | 'postalCode' | 'preferredDialects'
export type ProfileErrors = Partial<Record<FieldName, string>>

function validate(draft: Draft, today: string): ProfileErrors {
  return {
    fullName: personNameError(draft.fullName, "Enter the elder's full name."),
    dateOfBirth: dateOfBirthError(draft.dateOfBirth, today),
    phone: phoneError(draft.phone),
    address: [...draft.address.trim()].length > 255 ? 'Use 255 characters or fewer.' : undefined,
    postalCode: postalCodeError(draft.postalCode),
  }
}

/**
 * Edits the basic details a family member with full access keeps for an elder. Each detail is
 * checked against its Singapore format before saving, and what the server rejects is shown under
 * the same input (`serverErrors`).
 */
export function ElderProfileForm({ details, busy, error, serverErrors = {}, onSave, onCancel }: {
  details: ElderBasicDetails
  busy: boolean
  error: string
  serverErrors?: ProfileErrors
  onSave: (details: ElderBasicDetails) => void
  onCancel: () => void
}) {
  const today = singaporeToday()
  const id = useId()
  const [draft, setDraft] = useState<Draft>({
    fullName: details.fullName,
    dateOfBirth: details.dateOfBirth ?? '',
    phone: formatSgPhone(details.phone),
    address: details.address ?? '',
    postalCode: details.postalCode ?? '',
    dialects: parseDialects(details.preferredDialects),
    gender: details.gender,
    livesAlone: details.livesAlone,
    mobilityLevel: details.mobilityLevel,
  })
  const [checked, setChecked] = useState<ProfileErrors>({})
  // A field the family has changed since the server answered shows its own check, not the old answer.
  const [touchedSinceSave, setTouchedSinceSave] = useState<Set<FieldName>>(new Set())
  const errors: ProfileErrors = { ...checked }
  for (const [field, message] of Object.entries(serverErrors) as [FieldName, string][]) {
    if (!touchedSinceSave.has(field)) errors[field] ??= message
  }
  const warning = dateOfBirthWarning(draft.dateOfBirth, today)

  function update<K extends keyof Draft>(key: K, value: Draft[K], field?: FieldName) {
    setDraft((current) => ({ ...current, [key]: value }))
    if (field) {
      setChecked((current) => ({ ...current, [field]: undefined }))
      setTouchedSinceSave((current) => new Set(current).add(field))
    }
  }

  function submit() {
    if (busy) return
    const found = validate(draft, today)
    setChecked(found)
    setTouchedSinceSave(new Set())
    if (Object.values(found).some(Boolean)) return
    onSave({
      fullName: normalizeName(draft.fullName),
      gender: draft.gender,
      dateOfBirth: draft.dateOfBirth || null,
      phone: draft.phone.trim() ? normalizeSgPhone(draft.phone) : null,
      address: draft.address.trim() || null,
      postalCode: draft.postalCode.trim() || null,
      preferredDialects: joinDialects(draft.dialects),
      livesAlone: draft.livesAlone,
      mobilityLevel: draft.mobilityLevel,
    })
  }

  function fieldError(field: FieldName) {
    return errors[field] ? <span id={`${id}-${field}-error`} className={styles.fieldError}>{errors[field]}</span> : null
  }

  /** One text input with its label, its error and an optional hint under it. */
  function textField(field: 'fullName' | 'dateOfBirth' | 'phone' | 'address' | 'postalCode', label: string,
    input: InputHTMLAttributes<HTMLInputElement>, hint?: string, clean: (value: string) => string = (value) => value) {
    const inputId = `${id}-${field}`
    const described = [errors[field] && `${inputId}-error`, hint && `${inputId}-hint`].filter(Boolean).join(' ')
    return <div className={styles.field}>
      <label htmlFor={inputId}>{label}</label>
      <input id={inputId} name={field} autoComplete="off" {...input} value={draft[field]}
        aria-invalid={!!errors[field]} aria-describedby={described || undefined}
        onChange={(event) => update(field, clean(event.target.value), field)} />
      {fieldError(field)}
      {hint && <span id={`${inputId}-hint`} className={styles.fieldHint}>{hint}</span>}
    </div>
  }

  return <form className={styles.card} noValidate onSubmit={(event) => { event.preventDefault(); submit() }}>
    <h2>Edit basic details</h2>
    <p className={styles.meta}>You can complete these details gradually. Full name is required.</p>
    {error && <p role="alert" className={styles.error}>{error}</p>}
    <fieldset className={styles.fields} disabled={busy}>
      {textField('fullName', 'Full name *', { type: 'text', maxLength: 100 })}
      {textField('dateOfBirth', 'Date of birth', { type: 'date', min: '1900-01-01', max: today },
        errors.dateOfBirth ? undefined : warning)}
      {textField('phone', 'Phone', { type: 'tel', inputMode: 'tel', placeholder: '6123 4567', maxLength: 15 },
        'Home line or mobile, 8 digits.')}
      {textField('address', 'Home address', { type: 'text', placeholder: 'Blk 123 Ang Mo Kio Ave 6, #04-56', maxLength: 255 })}
      {textField('postalCode', 'Postal code', { type: 'text', inputMode: 'numeric', placeholder: '560123', maxLength: 6 },
        undefined, (value) => value.replace(/\D/g, ''))}
      <fieldset className={styles.choices} aria-describedby={errors.preferredDialects ? `${id}-preferredDialects-error` : undefined}>
        <legend>Preferred languages</legend>
        <div className={styles.choiceGrid}>
          {DIALECTS.map((dialect) => <label key={dialect}>
            <input type="checkbox" checked={draft.dialects.includes(dialect)} onChange={(event) => update('dialects',
              event.target.checked ? [...draft.dialects, dialect] : draft.dialects.filter((item) => item !== dialect),
              'preferredDialects')} />
            {dialect}
          </label>)}
        </div>
        {fieldError('preferredDialects')}
      </fieldset>
      <label className={styles.field}>Gender
        <select value={draft.gender ?? ''} onChange={(event) => update('gender', event.target.value as ElderBasicDetails['gender'] || null)}>
          <option value="">Not recorded</option><option value="MALE">Male</option><option value="FEMALE">Female</option><option value="OTHER">Other</option>
        </select>
      </label>
      <label className={styles.field}>Lives alone
        <select value={draft.livesAlone === null ? '' : String(draft.livesAlone)} onChange={(event) =>
          update('livesAlone', event.target.value === '' ? null : event.target.value === 'true')}>
          <option value="">Not recorded</option><option value="true">Yes</option><option value="false">No</option>
        </select>
      </label>
      <label className={styles.field}>Mobility
        <select value={draft.mobilityLevel ?? ''} onChange={(event) => update('mobilityLevel', event.target.value as ElderBasicDetails['mobilityLevel'] || null)}>
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
