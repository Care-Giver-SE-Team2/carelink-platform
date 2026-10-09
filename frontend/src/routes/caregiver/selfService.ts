import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../shared/api/client'

export function isAccessFailure(error: unknown) {
  return error instanceof ApiError && (error.status === 401 || error.status === 403)
}

export function serviceError(error: unknown) {
  if (!(error instanceof ApiError) || error.status >= 500) return 'The result could not be confirmed. Refresh the list to check before submitting again.'
  const code = error.body && typeof error.body === 'object' && 'code' in error.body ? String(error.body.code) : ''
  const messages: Record<string, string> = {
    ABSENCE_OVERLAPS: 'These dates overlap an existing pending or approved leave request.',
    ABSENCE_ENDS_BEFORE_IT_STARTS: 'The end date must not be before the start date.',
    ABSENCE_ALREADY_OVER: 'Choose leave that ends today or later.',
    SPOT_CHECK_NOT_YOURS: 'This conclusion is not yours to answer.',
    SPOT_CHECK_NOT_CONCLUDED: 'This spot check cannot currently be answered.',
    SPOT_CHECK_RESPONSE_REQUIRED: 'Write a response before submitting.',
  }
  return messages[code] ?? (error.status === 400 ? 'Check your entries and try again.' : error.status === 404 ? 'This record is no longer available. Refresh the list.' : 'This request cannot currently be saved. Refresh the list and try again.')
}

/** A write is never retried automatically, and a late response cannot update an abandoned page. */
export function useSelfServiceWrite() {
  const active = useRef<AbortController | null>(null)
  const mounted = useRef(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>(null)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; active.current?.abort(); active.current = null }
  }, [])
  async function send<T>(write: (signal: AbortSignal) => Promise<T>, saved: (value: T) => void) {
    if (active.current) return
    const controller = new AbortController()
    active.current = controller
    setPending(true); setError(null)
    const timeout = window.setTimeout(() => controller.abort(), 20000)
    try {
      const value = await write(controller.signal)
      if (mounted.current && active.current === controller) saved(value)
    } catch (failure) {
      if (mounted.current && active.current === controller) setError(failure)
    } finally {
      window.clearTimeout(timeout)
      if (mounted.current && active.current === controller) { active.current = null; setPending(false) }
    }
  }
  return { send, pending, error, clearError: () => setError(null) }
}
