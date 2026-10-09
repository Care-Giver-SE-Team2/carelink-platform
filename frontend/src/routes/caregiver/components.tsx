import { useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../../shared/api/client'
import styles from './Caregiver.module.css'

export function LastFetched({ at, subject = 'roster' }: { at: number; subject?: string }) {
  return <p className={styles.muted}>Last successfully fetched: {new Intl.DateTimeFormat('en-SG', {
    timeZone: 'Asia/Singapore', dateStyle: 'medium', timeStyle: 'medium',
  }).format(at)} (SGT). This is the fetch time, not the {subject} modification time. Refresh or return to this page to check for changes; updates are not live.</p>
}

export function QueryError({ error, retry, profile = false, back = '/caregiver' }: { error: unknown; retry: () => void; profile?: boolean; back?: string }) {
  const navigate = useNavigate()
  const status = error instanceof ApiError ? error.status : 0
  const body = error instanceof ApiError ? error.body : null
  const cancelled = status === 409 && typeof body === 'object' && body !== null && 'code' in body && body.code === 'VISIT_CANCELLED'
  useEffect(() => { if (status === 401) navigate('/', { replace: true }) }, [status, navigate])
  if (status === 401) return <p role="status">Your session has ended. Returning to sign in…</p>
  const heading = cancelled ? 'Visit cancelled' : status === 403 ? 'Access not permitted' : status === 404 ? profile ? 'Caregiver profile not linked' : 'Visit not found' : status === 400 ? 'Check the date range' : 'Unable to load this page'
  const message = cancelled ? 'This visit has been cancelled. Its work pack is no longer available. Return to your schedule.' : status === 403 ? 'This page is available only to the caregiver currently assigned to the visit.'
    : status === 404 ? profile ? 'Your account has no caregiver profile. Ask your manager to link it.' : 'This visit is unavailable. Return to your schedule.'
    : status === 400 ? 'Enter both dates in order, with a range of at most 31 days.'
    : status === 409 ? 'This visit’s plan data needs attention. Please contact your manager.'
    : 'Check your connection and try again. Previously loaded care details have been cleared.'
  return <div className={styles.error} role="alert"><h2>{heading}</h2><p>{message}</p><button className={styles.button} onClick={retry}>Try again</button>{' '}<Link to={back}>My schedule</Link></div>
}
