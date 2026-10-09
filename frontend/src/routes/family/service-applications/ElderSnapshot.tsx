import type { ElderBasicDetails } from '../../../features/family-elders/api'
import styles from '../intake/FamilyIntake.module.css'

/** The same basic fields are shown before submission and in the immutable saved snapshot. */
export function ElderSnapshot({ details }: { details: ElderBasicDetails }) {
  const mobility = { INDEPENDENT: 'Independent', ASSISTIVE_CANE: 'Uses a walking aid', WHEELCHAIR_BEDBOUND: 'Wheelchair / bedbound' }
  const rows = [
    ['Full name', details.fullName], ['Date of birth', details.dateOfBirth],
    ['Gender', details.gender?.toLowerCase()], ['Phone', details.phone], ['Home address', details.address],
    ['Postal code', details.postalCode], ['Preferred dialects', details.preferredDialects],
    ['Lives alone', details.livesAlone === null ? null : details.livesAlone ? 'Yes' : 'No'],
    ['Mobility', details.mobilityLevel ? mobility[details.mobilityLevel] : null],
  ]
  return <dl className={styles.fields}>{rows.map(([label, value]) => <div key={label}>
    <dt>{label}</dt><dd>{value || 'Not recorded'}</dd>
  </div>)}</dl>
}
