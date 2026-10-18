import { useCallback, useEffect, useRef, useState } from 'react'

export type QueryResult<T> = { key: string; revision: number } & (
  { status: 'loading' } | { status: 'success'; data: T; receivedAt: number } | { status: 'error'; error: unknown }
)
/** No cross-account cache. Changed parameters hide previous responses before effects run. */
export function useCaregiverQuery<T>(key: string, load: (signal: AbortSignal) => Promise<T>, refreshOnReturn = false) {
  const [revision, setRevision] = useState(0)
  const [result, setResult] = useState<QueryResult<T>>({ key, revision, status: 'loading' })
  const pending = useRef<AbortController | null>(null)
  const lastRefresh = useRef(-Infinity)
  const reload = useCallback(() => {
    pending.current?.abort()
    lastRefresh.current = Date.now()
    setRevision(r => r + 1)
  }, [])
  useEffect(() => {
    if (!refreshOnReturn) return
    const refresh = () => {
      if (document.visibilityState !== 'hidden' && Date.now() - lastRefresh.current >= 500) reload()
    }
    window.addEventListener('focus', refresh)
    window.addEventListener('online', refresh)
    document.addEventListener('visibilitychange', refresh)
    return () => {
      window.removeEventListener('focus', refresh)
      window.removeEventListener('online', refresh)
      document.removeEventListener('visibilitychange', refresh)
    }
  }, [refreshOnReturn, reload])
  useEffect(() => {
    const controller = new AbortController()
    pending.current = controller
    load(controller.signal).then(
      data => { if (!controller.signal.aborted) setResult({ key, revision, status: 'success', data, receivedAt: Date.now() }) },
      error => { if (!controller.signal.aborted) setResult({ key, revision, status: 'error', error }) },
    )
    return () => controller.abort()
  }, [key, load, revision])
  const current: QueryResult<T> = result.key === key && result.revision === revision ? result : { key, revision, status: 'loading' }
  return { result: current, reload, retainedData: result.key === key && result.status === 'success' ? result.data : undefined }
}
