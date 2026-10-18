import { DIALECTS } from '../../shared/validation/sg'
import type { ApplicationDetails } from '../../features/admission/api'

export default function ApplicationFields({ values, onChange }: { values: ApplicationDetails; onChange: (next: ApplicationDetails) => void }) {
  const dialects = values.declaredDialects?.split(',') ?? []
  return <>
    <label>Full name<input autoComplete="name" value={values.fullName} onChange={e => onChange({ ...values, fullName: e.target.value })} maxLength={100} required /></label>
    <label>Mobile number<input type="tel" autoComplete="tel" value={values.phone} onChange={e => onChange({ ...values, phone: e.target.value })} placeholder="e.g. 91234567" required /></label>
    <label>Preferred area (optional)<input value={values.requestedSector ?? ''} onChange={e => onChange({ ...values, requestedSector: e.target.value || null })} maxLength={50} /></label>
    <p>This is an intention, not your manager-assigned working area.</p>
    <fieldset><legend>Languages spoken (optional)</legend>{DIALECTS.map(language => <label key={language}>
      <input type="checkbox" checked={dialects.includes(language)} onChange={e => onChange({ ...values,
        declaredDialects: (e.target.checked ? [...dialects, language] : dialects.filter(d => d !== language)).join(',') || null })} />{language}
    </label>)}</fieldset>
  </>
}
