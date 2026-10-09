import { Link, Navigate } from 'react-router-dom'
import { ApiError } from '../../../shared/api/client'
import styles from './FamilySchedule.module.css'

/**
 * Offers access rechecks or retry without retaining protected schedule data; a lost session returns to the landing page.
 * @author Wang Zhili
 */
export function ScheduleFeedback({ error, onRetry, onResetAccess }: {
  error: unknown
  onRetry: () => void
  onResetAccess: () => void
}) {
  const status = error instanceof ApiError ? error.status : undefined
  // The landing page is the only sign-in screen.
  if (status === 401) return <Navigate to="/" replace />
  const forbidden = status === 403
  return (
    <section className={styles.state} role="alert">
      <h2>{forbidden ? 'Schedule access unavailable' : 'Unable to load this schedule'}</h2>
      <p>{forbidden
        ? 'Your access may have changed. Reload your available elders, or sign in with another family account.'
        : 'The schedule could not be loaded. Check your connection and try again.'}</p>
      {status === 400 && error instanceof ApiError && <p>{error.message}</p>}
      <button onClick={forbidden ? onResetAccess : onRetry}>
        {forbidden ? 'Reload available elders' : 'Try again'}
      </button>
      {forbidden && <Link to="/">Sign in with another account</Link>}
    </section>
  )
}
