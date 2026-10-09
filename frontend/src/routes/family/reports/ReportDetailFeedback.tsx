import { Link, Navigate } from 'react-router-dom'
import { ApiError } from '../../../shared/api/client'
import styles from './FamilyReports.module.css'

/** Recovers access to the same report; server errors never become report text.
 * @author Wang Zhili
 */
export function ReportDetailFeedback({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const status = error instanceof ApiError ? error.status : undefined
  // The landing page is the only sign-in screen.
  if (status === 401) return <Navigate to="/" replace />
  const forbidden = status === 403
  const missing = status === 404
  const invalid = status === 400
  return <section className={styles.state} role="alert">
    <h2>{forbidden ? 'Report access unavailable' : missing ? 'Report not found' : invalid ? 'Invalid report link' : 'Unable to load this report'}</h2>
    <p>{forbidden ? 'This report is not available to your account. Your access may have changed. Try again or sign in with another family account.'
      : missing ? 'This report could not be found. Return to your reports to choose another.'
      : invalid ? 'Check the report link or return to your reports.'
      : 'The report could not be loaded. Check your connection and try again.'}</p>
    {!missing && !invalid && <button onClick={onRetry}>Try again</button>}
    {forbidden && <Link to="/">Sign in with another account</Link>}
  </section>
}
