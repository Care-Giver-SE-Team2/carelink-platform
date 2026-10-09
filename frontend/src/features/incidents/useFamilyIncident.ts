import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ApiError } from '../../shared/api/client'
import { getCurrentUser } from '../auth/api'
import { acknowledgeFamilyIncident, getFamilyIncident, viewFamilyIncident } from './familyApi'
import type { FamilyIncidentAcknowledgement, FamilyIncidentDetail } from './familyTypes'
import { markNotificationRead, NOTIFICATIONS_CHANGED_EVENT } from '../notifications/api'

type Resource = { status: 'loading' } | { status: 'error'; error: unknown } | { status: 'success'; data: FamilyIncidentDetail }
type CommandState = 'idle' | 'saving' | 'saved' | 'error'
type State = { resource: Resource; view: CommandState; notification: CommandState; acknowledgement: CommandState; pause: 'offline' | 'hidden' | null; note: string }
const initial: State = { resource: { status: 'loading' }, view: 'idle', notification: 'idle', acknowledgement: 'idle', pause: null, note: '' }
const pauseReason = (): State['pause'] => !navigator.onLine ? 'offline' : document.visibilityState === 'hidden' ? 'hidden' : null

/** Cancels obsolete reads/writes and keeps recipient receipts independent under racing responses.
 * @author Wang Zhili
 */
export function useFamilyIncident(id: string, notificationId: number | null = null) {
  const [revision, setRevision] = useState(0)
  const key = useMemo(() => ({ id, notificationId, revision }), [id, notificationId, revision])
  const [result, setResult] = useState({ key, ...initial })
  const operations = useRef<{ view: () => void; notification: () => void; acknowledge: (note: string) => void; note: (value: string) => void } | null>(null)
  const refresh = useCallback(() => setRevision((value) => value + 1), [])
  const recordView = useCallback(() => operations.current?.view(), [])
  const recordNotificationRead = useCallback(() => operations.current?.notification(), [])
  const acknowledge = useCallback((note: string) => operations.current?.acknowledge(note), [])
  const setNote = useCallback((note: string) => operations.current?.note(note), [])

  useEffect(() => {
    let state: State = { ...initial, pause: pauseReason() }
    let disposed = false
    let stopped = false
    let interruptedAcknowledgement = false
    let loading: AbortController | undefined
    let account: number | undefined
    const requests = new Set<AbortController>()
    const publish = (change: Partial<State>) => {
      state = { ...state, ...change }
      setResult({ key, ...state })
    }
    const cancelRequests = () => {
      operations.current = null
      requests.forEach((request) => request.abort())
      requests.clear()
      loading = undefined
    }
    const stop = (error: unknown) => {
      stopped = error instanceof ApiError && [400, 401, 403, 404].includes(error.status)
      cancelRequests()
      publish({ ...initial, pause: state.pause, note: stopped ? '' : state.note, resource: { status: 'error', error } })
    }
    async function checkSession(signal: AbortSignal) {
      const user = await getCurrentUser(signal)
      signal.throwIfAborted()
      if (!user.roles.some((role) => role.replace(/^ROLE_/, '') === 'FAMILY')) throw new ApiError('Family access is required.', 403)
      return user.id
    }
    // A late view response must not erase awareness saved by a concurrent command.
    const mergeReceipt = (receipt: FamilyIncidentAcknowledgement) => {
      if (state.resource.status !== 'success') return
      const old = state.resource.data.acknowledgement
      const acknowledgement = { ...receipt, viewedAt: receipt.viewedAt ?? old.viewedAt,
        acknowledgedAt: receipt.acknowledgedAt ?? old.acknowledgedAt,
        responseNote: receipt.acknowledgedAt ? receipt.responseNote : old.responseNote }
      publish({ resource: { status: 'success', data: { ...state.resource.data, acknowledgement } } })
    }
    async function command(action: 'view' | 'acknowledgement' | 'notification', note = '') {
      if (action === 'notification' && key.notificationId === null) return
      if (disposed || state.pause || state.resource.status !== 'success' || state[action] === 'saving' || state[action] === 'saved') return
      const request = new AbortController()
      requests.add(request)
      publish({ [action]: 'saving' })
      try {
        if (await checkSession(request.signal) !== account) throw new ApiError('The family session has changed.', 403)
        if (action === 'notification') {
          const receipt = await markNotificationRead(key.notificationId!, request.signal)
          if (disposed || request.signal.aborted) return
          if (receipt.id !== key.notificationId || receipt.status !== 'READ' || receipt.resourceType !== 'INCIDENT' || receipt.resourceId !== Number(key.id)) {
            throw new Error('Notification read was not confirmed')
          }
          window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED_EVENT))
        } else {
          const receipt = action === 'view' ? await viewFamilyIncident(key.id, request.signal)
            : await acknowledgeFamilyIncident(key.id, note, request.signal)
          if (disposed || request.signal.aborted) return
          mergeReceipt(receipt)
        }
        if (action === 'acknowledgement') interruptedAcknowledgement = false
        publish({ [action]: 'saved' })
      } catch (error) {
        if (disposed || request.signal.aborted) return
        if (error instanceof ApiError && [401, 403, 404].includes(error.status)) {
          // A missing notification may mean binding revocation; hide care content until reauthorized.
          stop(action === 'notification' && error.status === 404 ? new ApiError('Notification access unavailable.', 403) : error)
        }
        else publish({ [action]: 'error' })
      } finally { requests.delete(request) }
    }
    async function load() {
      if (disposed || stopped || state.pause || loading) return
      const request = new AbortController()
      loading = request
      requests.add(request)
      operations.current = null
      publish({ resource: { status: 'loading' }, view: 'idle', notification: 'idle', acknowledgement: 'idle' })
      try {
        if (!/^[1-9]\d{0,18}$/.test(key.id) || BigInt(key.id) > 9223372036854775807n) throw new ApiError('Invalid incident link.', 400)
        const currentAccount = await checkSession(request.signal)
        // A retained draft belongs only to the account that wrote it.
        if (account !== currentAccount) { interruptedAcknowledgement = false; publish({ note: '' }) }
        account = currentAccount
        const data = await getFamilyIncident(key.id, request.signal)
        if (disposed || request.signal.aborted) return
        operations.current = { view: () => { void command('view') }, notification: () => { void command('notification') }, acknowledge: (note) => { void command('acknowledgement', note) },
          note: (value) => publish({ note: value }) }
        publish({ resource: { status: 'success', data }, view: data.acknowledgement.viewedAt ? 'saved' : 'idle',
          acknowledgement: data.acknowledgement.acknowledgedAt ? 'saved' : interruptedAcknowledgement ? 'error' : 'idle' })
      } catch (error) {
        if (!disposed && !request.signal.aborted) stop(error)
      } finally {
        requests.delete(request)
        if (loading === request) loading = undefined
      }
    }
    const activityChanged = () => {
      const pause = pauseReason()
      if (pause === state.pause) return
      if (pause) {
        // Aborting cannot undo a committed acknowledgement; query it on resume, never resend it.
        if (state.acknowledgement === 'saving') interruptedAcknowledgement = true
        cancelRequests()
        publish(stopped ? { pause } : { pause, resource: { status: 'loading' }, view: 'idle', notification: 'idle', acknowledgement: 'idle' })
      } else {
        publish({ pause: null })
        void load()
      }
    }
    document.addEventListener('visibilitychange', activityChanged)
    window.addEventListener('online', activityChanged)
    window.addEventListener('offline', activityChanged)
    publish({ pause: state.pause })
    void load()
    return () => {
      disposed = true
      cancelRequests()
      document.removeEventListener('visibilitychange', activityChanged)
      window.removeEventListener('online', activityChanged)
      window.removeEventListener('offline', activityChanged)
    }
  }, [key])

  // Hide the previous incident synchronously, before effects run for the new link or refresh.
  const state = result.key === key ? result : initial
  return { ...state, refresh, recordView, recordNotificationRead, acknowledge, setNote }
}
