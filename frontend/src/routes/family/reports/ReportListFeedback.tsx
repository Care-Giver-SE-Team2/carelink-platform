import { Link, Navigate } from 'react-router-dom'
import { ApiError } from '../../../shared/api/client'
import styles from './FamilyReports.module.css'

/**
 * Restores report access without retaining the previous account's protected content.
 * @author Wang Zhili
 */
export function ReportListFeedback({ error, onRetry, onResetAccess }: {
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
      <h2>{forbidden ? 'Report access unavailable' : 'Unable to load care reports'}</h2>
      <p>{forbidden
        ? 'Your access may have changed. Reload your available elders, or sign in with another family account.'
        : 'Care reports could not be loaded. Check your connection and try again.'}</p>
      <button onClick={forbidden ? onResetAccess : onRetry}>
        {forbidden ? 'Reload available elders' : 'Try again'}
      </button>
      {forbidden && <Link to="/">Sign in with another account</Link>}
    </section>
  )
}
