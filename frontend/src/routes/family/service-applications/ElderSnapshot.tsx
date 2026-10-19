import type { ElderBasicDetails } from '../../../features/family-elders/api'
import { dateOfBirthLabel } from '../../../features/schedule/presentation'
import { formatSgPhone } from '../../../shared/validation/sg'
import styles from '../intake/FamilyIntake.module.css'

/** The same basic fields are shown before submission and in the immutable saved snapshot. */
export function ElderSnapshot({ details }: { details: ElderBasicDetails }) {
  const mobility = { INDEPENDENT: 'Independent', ASSISTIVE_CANE: 'Uses a walking aid', WHEELCHAIR_BEDBOUND: 'Wheelchair / bedbound' }
  const gender = { MALE: 'Male', FEMALE: 'Female', OTHER: 'Other' }
  const rows = [
    ['Full name', details.fullName], ['Date of birth', dateOfBirthLabel(details.dateOfBirth)],
    ['Gender', details.gender ? gender[details.gender] : null], ['Phone', details.phone && formatSgPhone(details.phone)], ['Home address', details.address],
    ['Postal code', details.postalCode], ['Preferred languages', details.preferredDialects?.split(',').join(', ')],
    ['Lives alone', details.livesAlone === null ? null : details.livesAlone ? 'Yes' : 'No'],
    ['Mobility', details.mobilityLevel ? mobility[details.mobilityLevel] : null],
  ]
  return <dl className={styles.fields}>{rows.map(([label, value]) => <div key={label}>
    <dt>{label}</dt><dd>{value || 'Not recorded'}</dd>
  </div>)}</dl>
}
