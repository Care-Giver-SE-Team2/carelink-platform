import { useEffect, useRef } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { ApiError } from '../../../shared/api/client'
import styles from './IntakeForm.module.css'

/**
 * Explains submission failures and offers a separate list check. A lost session returns to the
 * landing page, the only sign-in screen.
 * @param failure Failed request and whether the application POST was started
 * @author Wang Zhili
 */
export function IntakeSubmissionFeedback({
  failure,
}: {
  failure: { error: unknown; sent: boolean }
}) {
  const summary = useRef<HTMLDivElement>(null)
  useEffect(() => {
    summary.current?.focus()
  }, [failure])
  const status = failure.error instanceof ApiError ? failure.error.status : undefined
  const session = status === 401
  const permission = status === 403
  const validation = status === 400
  // 409: refused by the one-elder-one-record rule, so definitely not saved.
  const alreadyApplied = status === 409 && conflictCode(failure.error) === 'APPLICATION_ALREADY_SUBMITTED'
  const alreadyRegistered = status === 409 && !alreadyApplied
  const uncertain = failure.sent && !session && !permission && !validation && status !== 409
  if (session) return <Navigate to="/" replace />
  const title = permission
      ? 'Submission not permitted'
      : alreadyApplied
        ? 'Application already submitted'
        : alreadyRegistered
          ? 'Already known to the care team'
          : uncertain
            ? 'Submission status unknown'
            : validation
              ? 'Check your application'
              : 'Unable to prepare submission'
  const message = permission
      ? 'Your session protection or family permissions may have changed. Sign in again, or contact your care team. Your entries have been kept.'
      : alreadyApplied
        ? 'You already have an application for this person waiting for review, so this one was not sent. You can follow its progress in your applications.'
        : alreadyRegistered
          ? 'This person is already registered with the care team, or someone has already applied for them, so this application was not sent. Contact the care team if you need to be linked to them or to update their details. Your entries have been kept.'
          : uncertain
            ? 'Your application may have been saved. Check your applications before choosing to submit again. Your entries have been kept.'
            : validation
              ? 'The application was not accepted. Review the fields and try again. Your entries have been kept.'
              : 'We could not prepare your request. Check your connection and try again. Your application has not been sent.'

  return (
    <div className={styles.feedback}>
      <div className={styles.errorSummary} role="alert" tabIndex={-1} ref={summary}>
        <h2>{title}</h2>
        <p>{message}</p>
        {alreadyApplied && <Link to="/family/intake">View my applications</Link>}
        {uncertain && (
          <Link to="/family/intake" target="_blank" rel="noopener noreferrer">
            Check my applications (new tab)
          </Link>
        )}
        {permission && <Link to="/">Sign in again</Link>}
      </div>
    </div>
  )
}

/** The business-rule code a 409 carries in its problem body ("APPLICATION_ALREADY_SUBMITTED"). */
function conflictCode(error: unknown): string | undefined {
  if (!(error instanceof ApiError) || typeof error.body !== 'object' || error.body === null) return undefined
  const code = (error.body as { code?: unknown }).code
  return typeof code === 'string' ? code : undefined
}
