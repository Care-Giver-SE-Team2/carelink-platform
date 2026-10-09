import { Navigate } from 'react-router-dom'
import { ApiError } from '../../shared/api/client'
import { isAccessFailure } from './selfService'
import styles from './Caregiver.module.css'

export default function SelfServiceError({ error, retry, subject }: { error: unknown; retry: () => void; subject: string }) {
  if (error instanceof ApiError && error.status === 401) return <Navigate to="/" replace />
  const denied = isAccessFailure(error)
  return <section className={styles.error} role="alert">
    <h2>{denied ? 'Access not permitted' : `Unable to load ${subject}`}</h2>
    <p>{denied ? 'Your account cannot access this page. Sign in with a caregiver account.' : 'Check your connection and try again. Previously loaded records have been cleared.'}</p>
    <button className={styles.button} onClick={retry}>Try again</button>
  </section>
}
