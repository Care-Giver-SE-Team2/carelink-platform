import { Link } from 'react-router-dom'
import { useFamilyElderProfiles } from '../../../features/family-elders/queries'
import { ageOn, singaporeToday } from '../../../features/schedule/presentation'
import styles from './FamilyElders.module.css'

export function FamilyEldersPage() {
  const elders = useFamilyElderProfiles()
  const today = singaporeToday()
  return <div className={styles.page}>
    <header className={styles.header}>
      <p className={styles.eyebrow}>Your family's care</p>
      <h1>My elders</h1>
      <p>View the elders linked to you and keep their basic details up to date.</p>
    </header>
    <div className={styles.content}>
      <div className={styles.actions}>
        <Link to="/family/family-bindings">Review binding requests →</Link>
        <button type="button" onClick={() => void elders.refetch()} disabled={elders.isFetching}>Refresh</button>
      </div>
      {elders.isPending ? <p role="status">Loading your elders…</p> : elders.isError ? <div className={styles.card} role="alert">
        <p>Unable to load your elders. Please try again.</p>
      </div> : elders.data.length === 0 ? <section className={styles.card}>
        <h2>No linked elders yet</h2>
        <p>An elder can send you a binding request. Confirm it in Family bindings to see their profile here.</p>
        <Link to="/family/family-bindings">Go to Family bindings →</Link>
      </section> : <ul className={styles.list}>
        {elders.data.map((elder) => {
          const age = ageOn(elder.dateOfBirth, today)
          return <li className={styles.card} key={elder.id}>
            <div className={styles.cardHeading}>
              <h2>{elder.fullName}</h2>
              <span className={styles.pill}>{elder.accessScope === 'FULL' ? 'Full access' : 'Read-only'}</span>
            </div>
            <p className={styles.meta}>{age === null ? 'Date of birth not recorded' : `${age} years old`}</p>
            <p>{elder.address || 'Address not recorded'}</p>
            <Link to={`/family/elders/${elder.id}`} aria-label={`View details for ${elder.fullName}`}>View basic details →</Link>
          </li>
        })}
      </ul>}
    </div>
  </div>
}
