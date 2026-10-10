import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode } from 'react'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import FamilyHome from './index'

// The navigation's waiting counts are tested with FamilyLayout and on Home; here they stay at zero so
// only this page's own requests are made.
vi.mock('./components/usePendingDecisions', () => ({
  usePendingDecisions: () => ({ changes: [], spotChecks: [], requests: [], total: 0 }),
}))

const family = { id: 7, username: 'family-a', displayName: 'Family A', roles: ['FAMILY'] }
const visit = {
  id: 501, elderId: 101, caregiverId: 201, serviceType: 'Home care', status: 'IN_PROGRESS',
  scheduledStart: '2026-09-30T09:00:00+08:00', scheduledEnd: '2026-09-30T10:00:00+08:00',
  checkedInAt: '2026-09-30T09:03:00+08:00', checkedOutAt: null, asOf: '2026-09-30T09:20:00+08:00',
}
const timeline = [
  { id: 801, visitId: 501, fromState: 'SCHEDULED', toState: 'ARRIVED', result: 'APPLIED', occurredAt: '2026-09-30T09:03:00+08:00' },
  { id: 802, visitId: 501, fromState: 'ARRIVED', toState: 'IN_PROGRESS', result: 'APPLIED', occurredAt: '2026-09-30T09:05:00+08:00' },
]
const tasks = [
  { id: 901, visitId: 501, name: 'Assist with walking', status: 'DONE', completedAt: '2026-09-30T09:15:00+08:00' },
  { id: 902, visitId: 501, name: 'Meal preparation', status: 'PENDING', completedAt: null },
  { id: 903, visitId: 501, name: 'Exercise', status: 'SKIPPED', completedAt: null },
  { id: 904, visitId: 501, name: 'Optional activity', status: 'REFUSED', completedAt: null },
]
function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
}
function installApi(override?: (url: URL, init: RequestInit) => Response | Promise<Response> | undefined) {
  const fetchMock = vi.fn((path: string, init: RequestInit) => {
    const url = new URL(path, 'http://localhost')
    const response = override?.(url, init)
    if (response !== undefined) return Promise.resolve(response)
    if (url.pathname === '/api/auth/me') return Promise.resolve(json(family))
    if (url.pathname === '/api/elders') return Promise.resolve(json([{ id: 101, fullName: 'Elder A' }]))
    if (url.pathname === '/api/visits') return Promise.resolve(json({
      items: [visit, { ...visit, id: 502, caregiverId: null }], page: 0, size: 20, totalElements: 2,
    }))
    if (url.pathname === '/api/visits/501') return Promise.resolve(json(visit))
    if (url.pathname === '/api/visits/501/timeline') return Promise.resolve(json(timeline))
    if (url.pathname === '/api/visits/501/tasks') return Promise.resolve(json(tasks))
    throw new Error(`Unexpected API request: ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
function CurrentUrl() {
  const navigate = useNavigate()
  const location = useLocation()
  return <>
    <button onClick={() => navigate('/family/visits/502')}>Open another visit</button>
    <button onClick={() => navigate(-1)}>Browser back</button>
    <div aria-label="Current URL">{location.pathname}{location.search}</div>
  </>
}
function openProgress(path = '/family/visits/501', strict = false) {
  const page = <MemoryRouter initialEntries={[path]}>
    <CurrentUrl />
    <Routes>
      <Route path="/" element={<h1>Landing</h1>} />
      <Route path="/family/*" element={<FamilyHome />} />
    </Routes>
  </MemoryRouter>
  return render(strict ? <StrictMode>{page}</StrictMode> : page)
}
beforeEach(() => vi.stubGlobal('scrollTo', vi.fn()))
afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('Family visit automatic refresh', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date', 'setTimeout', 'clearTimeout'] })
    vi.setSystemTime(new Date('2026-10-01T01:00:00Z'))
  })

  it.each(['/api/auth/me', '/api/visits/501', '/api/visits/501/timeline', '/api/visits/501/tasks'])(
    'does not overlap refresh rounds while %s is slow, including repeated manual clicks', async (path) => {
      let slow = false
      let finish!: (response: Response) => void
      const fetchMock = installApi((url) => slow && url.pathname === path
        ? new Promise<Response>((resolve) => { finish = resolve }) : undefined)
      await act(async () => { openProgress() })
      slow = true
      await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
      expect(screen.getByRole('button', { name: 'Refresh progress' })).toBeDisabled()
      fireEvent.click(screen.getByRole('button', { name: 'Refresh progress' }))
      const count = fetchMock.mock.calls.length
      await act(async () => { await vi.advanceTimersByTimeAsync(180000) })
      expect(fetchMock).toHaveBeenCalledTimes(count)
      slow = false
      await act(async () => finish(json(path.endsWith('/me') ? family : path.endsWith('/tasks') ? tasks : path.endsWith('/timeline') ? timeline : visit)))
      expect(screen.getByRole('button', { name: 'Refresh progress' })).toBeEnabled()
      await act(async () => { await vi.advanceTimersByTimeAsync(14999) })
      expect(fetchMock.mock.calls.filter(([p]) => p === '/api/auth/me')).toHaveLength(2)
      await act(async () => { await vi.advanceTimersByTimeAsync(1) })
      expect(fetchMock.mock.calls.filter(([p]) => p === '/api/auth/me')).toHaveLength(3)
    },
  )

  it('starts no requests until the page is both visible and online', async () => {
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
    const online = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const fetchMock = installApi()
    await act(async () => { openProgress() })
    expect(screen.getByText(/Updates paused while offline/)).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(60000) })
    expect(fetchMock).not.toHaveBeenCalled()
    await act(async () => { online.mockReturnValue(true); window.dispatchEvent(new Event('online')) })
    expect(screen.getByText(/Updates paused while this tab is hidden/)).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
    await act(async () => { visibility.mockReturnValue('visible'); document.dispatchEvent(new Event('visibilitychange')) })
    expect(screen.getByText('1 of 4 tasks completed')).toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/me')).toHaveLength(1)
  })

  it.each([401, 403].flatMap((status) => ['/api/auth/me', '/api/visits/501', '/api/visits/501/timeline', '/api/visits/501/tasks'].map((path) => [status, path] as const)))(
    'clears all data and stops automatic retries after %s from %s, even after visibility/network events', async (status, path) => {
      let denied = false
      const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
      const online = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
      const fetchMock = installApi((url) => denied && url.pathname === path ? json({}, status) : undefined)
      await act(async () => { openProgress() })
      denied = true
      await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
      expect(screen.getByRole('heading', { name: status === 401 ? 'Landing' : 'Visit access unavailable' })).toBeInTheDocument()
      expect(screen.queryByText('Home care')).not.toBeInTheDocument()
      expect(screen.queryByRole('list', { name: 'Visit tasks' })).not.toBeInTheDocument()
      const count = fetchMock.mock.calls.length
      await act(async () => {
        visibility.mockReturnValue('hidden'); document.dispatchEvent(new Event('visibilitychange'))
        online.mockReturnValue(false); window.dispatchEvent(new Event('offline'))
        online.mockReturnValue(true); window.dispatchEvent(new Event('online'))
        visibility.mockReturnValue('visible'); document.dispatchEvent(new Event('visibilitychange'))
        await vi.advanceTimersByTimeAsync(300000)
      })
      expect(fetchMock).toHaveBeenCalledTimes(count)
    },
  )

  it('allows explicit permission retry to resume polling after access is restored', async () => {
    let denied = true
    const fetchMock = installApi((url) => denied && url.pathname.endsWith('/tasks') ? json({}, 403) : undefined)
    await act(async () => { openProgress() })
    expect(screen.getByRole('heading', { name: 'Visit access unavailable' })).toBeInTheDocument()
    denied = false
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Try again' })) })
    expect(screen.getByText('1 of 4 tasks completed')).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/me')).toHaveLength(3)
  })

  it('discards all previous account data when another family session is detected', async () => {
    let otherAccount = false
    let finish!: (response: Response) => void
    installApi((url) => {
      if (!otherAccount) return
      if (url.pathname === '/api/auth/me') return json({ ...family, id: 9, username: 'family-b' })
      if (url.pathname === '/api/visits/501') return json({ ...visit, serviceType: 'New account care' })
      if (url.pathname.endsWith('/timeline')) return json([])
      if (url.pathname.endsWith('/tasks')) return new Promise<Response>((resolve) => { finish = resolve })
    })
    await act(async () => { openProgress() })
    otherAccount = true
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    expect(screen.queryByText('Home care')).not.toBeInTheDocument()
    expect(screen.queryByText('Assist with walking')).not.toBeInTheDocument()
    expect(screen.getByText('New account care')).toBeInTheDocument()
    await act(async () => finish(json({}, 503)))
    expect(screen.getByRole('alert')).not.toHaveTextContent('last loaded')
    expect(screen.queryByText('1 of 4 tasks completed')).not.toBeInTheDocument()
  })

  it.each([false, true])('keeps session-check failures retryable without inventing data (previous success: %s)', async (previous) => {
    let failing = !previous
    const fetchMock = installApi((url) => failing && url.pathname === '/api/auth/me' ? Promise.reject(new TypeError('Offline')) : undefined)
    await act(async () => { openProgress() })
    if (previous) {
      failing = true
      await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
      expect(screen.getByText('1 of 4 tasks completed')).toBeInTheDocument()
      expect(screen.getByText(/Visit details checked/)).toHaveTextContent('09:20')
      expect(screen.getAllByRole('alert')).toHaveLength(3)
    } else expect(screen.getByRole('heading', { name: 'Unable to load visit progress' })).toBeInTheDocument()
    expect(screen.getByText(/Automatic retry interval: 30 seconds/)).toBeInTheDocument()
    failing = false
    await act(async () => { await vi.advanceTimersByTimeAsync(30000) })
    expect(screen.getByText('1 of 4 tasks completed')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/me')).toHaveLength(previous ? 3 : 2)
  })

  it('cancels the scheduled poll on manual refresh and bounds repeated failure backoff at 60 seconds', async () => {
    let failing = false
    const fetchMock = installApi((url) => failing && url.pathname.endsWith('/tasks') ? json({}, 503) : undefined)
    await act(async () => { openProgress() })
    await act(async () => { await vi.advanceTimersByTimeAsync(10000) })
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Refresh progress' })) })
    await act(async () => { await vi.advanceTimersByTimeAsync(5000) })
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/me')).toHaveLength(2)
    failing = true
    await act(async () => { await vi.advanceTimersByTimeAsync(10000) })
    await act(async () => { await vi.advanceTimersByTimeAsync(30000) })
    await act(async () => { await vi.advanceTimersByTimeAsync(60000) })
    expect(screen.getByText(/Automatic retry interval: 60 seconds/)).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(60000) })
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/me')).toHaveLength(6)
    failing = false
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Retry task progress' })) })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/me')).toHaveLength(8)
  })

  it.each(['waiting', 'refreshing', 'backoff'])('leaves no automatic reads or event listeners after unmount during %s', async (stage) => {
    let refreshing = false
    let finish!: (response: Response) => void
    const fetchMock = installApi((url) => refreshing && url.pathname.endsWith('/tasks')
      ? stage === 'backoff' ? json({}, 503) : new Promise<Response>((resolve) => { finish = resolve }) : undefined)
    let view!: ReturnType<typeof openProgress>
    await act(async () => { view = openProgress() })
    if (stage !== 'waiting') {
      refreshing = true
      await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    }
    view.unmount()
    const count = fetchMock.mock.calls.length
    const visibility = vi.spyOn(document, 'visibilityState', 'get')
    const online = vi.spyOn(navigator, 'onLine', 'get')
    await act(async () => {
      if (stage === 'refreshing') finish(json(tasks))
      visibility.mockReturnValue('hidden'); document.dispatchEvent(new Event('visibilitychange'))
      online.mockReturnValue(false); window.dispatchEvent(new Event('offline'))
      visibility.mockReturnValue('visible'); document.dispatchEvent(new Event('visibilitychange'))
      online.mockReturnValue(true); window.dispatchEvent(new Event('online'))
      await vi.advanceTimersByTimeAsync(600000)
    })
    expect(fetchMock).toHaveBeenCalledTimes(count)
  })

  it('aborts a session check on going offline and ignores its late denial after resuming', async () => {
    let first = true
    let finish!: (response: Response) => void
    const online = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    const fetchMock = installApi((url) => url.pathname === '/api/auth/me' && first
      ? new Promise<Response>((resolve) => { finish = resolve }) : undefined)
    await act(async () => { openProgress() })
    await act(async () => { online.mockReturnValue(false); window.dispatchEvent(new Event('offline')) })
    expect(fetchMock.mock.calls[0][1].signal?.aborted).toBe(true)
    first = false
    await act(async () => { online.mockReturnValue(true); window.dispatchEvent(new Event('online')) })
    expect(screen.getByText('1 of 4 tasks completed')).toBeInTheDocument()
    await act(async () => finish(json({}, 401)))
    expect(screen.queryByRole('heading', { name: 'Landing' })).not.toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/tasks'))).toHaveLength(2)
  })

  it('keeps one live poll after StrictMode remounts the effect', async () => {
    const fetchMock = installApi()
    await act(async () => { openProgress('/family/visits/501', true) })
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/tasks'))).toHaveLength(1)
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/tasks'))).toHaveLength(2)
  })

  it('clears protected data when the session now has a non-family role', async () => {
    let changed = false
    const fetchMock = installApi((url) => changed && url.pathname === '/api/auth/me' ? json({ ...family, roles: ['MANAGER'] }) : undefined)
    await act(async () => { openProgress() })
    changed = true
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    expect(screen.getByRole('heading', { name: 'Visit access unavailable' })).toBeInTheDocument()
    expect(screen.queryByText('1 of 4 tasks completed')).not.toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(60000) })
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/tasks'))).toHaveLength(1)
  })

  it.each([400, 404])('stops polling when a later read returns %s and clears old care facts', async (status) => {
    let failing = false
    const fetchMock = installApi((url) => failing && url.pathname.endsWith('/tasks') ? json({}, status) : undefined)
    await act(async () => { openProgress() })
    failing = true
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    expect(screen.getByRole('heading', { name: status === 400 ? 'Invalid visit link' : 'Visit not found' })).toBeInTheDocument()
    expect(screen.queryByText('1 of 4 tasks completed')).not.toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(60000) })
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/tasks'))).toHaveLength(2)
  })

  it('reads updated care facts after 15 seconds without advancing saved state by time alone', async () => {
    let updated = false
    const fetchMock = installApi((url) => updated && url.pathname === '/api/visits/501'
      ? json({ ...visit, status: 'COMPLETED', asOf: '2026-10-01T09:00:15+08:00' }) : undefined)
    await act(async () => { openProgress() })
    const detail = screen.getByRole('region', { name: 'Visit details' })
    expect(within(detail).getByText('In progress')).toBeInTheDocument()
    updated = true
    await act(async () => { await vi.advanceTimersByTimeAsync(14999) })
    expect(within(detail).getByText('In progress')).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(1) })
    const refreshed = screen.getByRole('region', { name: 'Visit details' })
    expect(within(refreshed).getByText('Completed')).toBeInTheDocument()
    expect(within(refreshed).getByText(/Visit details checked/)).toHaveTextContent('09:00')
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/me')).toHaveLength(2)
  })

  it('keeps failed task data marked stale, backs off to 30 then 60 seconds and resets after recovery', async () => {
    let failing = false
    const fetchMock = installApi((url) => failing && url.pathname.endsWith('/tasks') ? json({}, 503) : undefined)
    await act(async () => { openProgress() })
    const stamp = screen.getByText(/Tasks loaded/).querySelector('time')!.dateTime
    failing = true
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    expect(screen.getByText('1 of 4 tasks completed')).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Task progress' })).getByRole('alert')).toHaveTextContent('out of date')
    expect(screen.getByText(/Tasks loaded/).querySelector('time')).toHaveAttribute('datetime', stamp)
    expect(screen.getByText(/Automatic retry interval: 30 seconds/)).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(29999) })
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/tasks'))).toHaveLength(2)
    await act(async () => { await vi.advanceTimersByTimeAsync(1) })
    expect(screen.getByText(/Automatic retry interval: 60 seconds/)).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(59999) })
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/tasks'))).toHaveLength(3)
    failing = false
    await act(async () => { await vi.advanceTimersByTimeAsync(1) })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByText(/Tasks loaded/).querySelector('time')).not.toHaveAttribute('datetime', stamp)
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/tasks'))).toHaveLength(5)
  })

  it.each(['hidden', 'offline'])('pauses while %s, cancels pending reads and refreshes once on return', async (reason) => {
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
    const online = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    let hold = false
    let finish!: (response: Response) => void
    const fetchMock = installApi((url) => hold && url.pathname.endsWith('/tasks')
      ? new Promise<Response>((resolve) => { finish = resolve }) : undefined)
    await act(async () => { openProgress() })
    hold = true
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    const pendingSignal = fetchMock.mock.calls.at(-1)![1].signal!
    await act(async () => {
      if (reason === 'hidden') { visibility.mockReturnValue('hidden'); document.dispatchEvent(new Event('visibilitychange')) }
      else { online.mockReturnValue(false); window.dispatchEvent(new Event('offline')) }
    })
    expect(pendingSignal.aborted).toBe(true)
    expect(screen.getByText(/Updates paused/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh progress' })).toBeDisabled()
    expect(screen.getByText('1 of 4 tasks completed')).toBeInTheDocument()
    const count = fetchMock.mock.calls.length
    await act(async () => { await vi.advanceTimersByTimeAsync(300000) })
    expect(fetchMock).toHaveBeenCalledTimes(count)
    await act(async () => finish(json([{ ...tasks[0], name: 'Late task from paused request' }])))
    expect(screen.queryByText('Late task from paused request')).not.toBeInTheDocument()
    hold = false
    await act(async () => {
      visibility.mockReturnValue('visible'); online.mockReturnValue(true)
      window.dispatchEvent(new Event('online')); document.dispatchEvent(new Event('visibilitychange'))
    })
    expect(screen.queryByText(/Updates paused/)).not.toBeInTheDocument()
    expect(screen.getByText('1 of 4 tasks completed')).toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/me')).toHaveLength(3)
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/me')).toHaveLength(4)
  })
})

describe('Family visit progress', () => {
  it.each([401, 403].flatMap((status) => ['details', 'timeline', 'tasks'].map((part) => [status, part] as const)))(
    'clears every section on %s from %s and ignores later successes', async (status, part) => {
      let refreshing = false
      const pending = new Map<string, (response: Response) => void>()
      const fetchMock = installApi((url) => refreshing && url.pathname.startsWith('/api/visits/')
        ? new Promise<Response>((resolve) => { pending.set(url.pathname, resolve) }) : undefined)
      openProgress()
      await screen.findByText('1 of 4 tasks completed')
      refreshing = true
      await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh progress' }))
      expect(screen.getByText('Home care')).toBeInTheDocument()
      await waitFor(() => expect(pending.size).toBe(3))
      const failedPath = '/api/visits/501' + (part === 'details' ? '' : '/' + part)
      await act(async () => pending.get(failedPath)!(json({}, status)))
      expect(await screen.findByRole('heading', { name: status === 401 ? 'Landing' : 'Visit access unavailable' })).toBeInTheDocument()
      for (const [path, finish] of pending) {
        if (path !== failedPath) await act(async () => finish(json(path.endsWith('/tasks') ? tasks : path.endsWith('/timeline') ? timeline : visit)))
      }
      expect(screen.queryByText('Home care')).not.toBeInTheDocument()
      expect(screen.queryByRole('list', { name: 'Visit tasks' })).not.toBeInTheDocument()
      expect(screen.queryByRole('list', { name: 'Service updates' })).not.toBeInTheDocument()
      expect(fetchMock.mock.calls.slice(-3).every(([, init]) => init.signal?.aborted)).toBe(true)
    },
  )

  it('ignores the first visit response after switching away and back to the same ID', async () => {
    let first = true
    const pending: Array<(response: Response) => void> = []
    const fetchMock = installApi((url) => {
      if (!url.pathname.startsWith('/api/visits/')) return
      if (url.pathname.startsWith('/api/visits/501') && first) return new Promise<Response>((resolve) => { pending.push(resolve) })
      if (url.pathname.endsWith('/tasks') || url.pathname.endsWith('/timeline')) return json([])
      return json({ ...visit, serviceType: url.pathname.endsWith('/502') ? 'Other care' : 'Fresh care' })
    })
    const user = userEvent.setup()
    openProgress()
    await waitFor(() => expect(pending).toHaveLength(3))
    first = false
    await user.click(screen.getByRole('button', { name: 'Open another visit' }))
    expect(await screen.findByText('Other care')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Browser back' }))
    expect(await screen.findByText('Fresh care')).toBeInTheDocument()
    await act(async () => { pending[0](json(visit)); pending[1](json(timeline)); pending[2](json(tasks)) })
    expect(screen.getByText('Fresh care')).toBeInTheDocument()
    expect(screen.queryByText('Home care')).not.toBeInTheDocument()
    expect(screen.queryByText('Assist with walking')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.slice(1, 4).every(([, init]) => init.signal?.aborted)).toBe(true)
  })

  it.each(['session', 'sections'])('cancels pending %s requests on leaving and ignores their late responses', async (stage) => {
    const pending: Array<(response: Response) => void> = []
    const fetchMock = installApi((url) => (stage === 'session' || url.pathname.startsWith('/api/visits/'))
      ? new Promise<Response>((resolve) => { pending.push(resolve) }) : undefined)
    const view = openProgress()
    await waitFor(() => expect(pending).toHaveLength(stage === 'session' ? 1 : 3))
    view.unmount()
    expect(fetchMock.mock.calls.every(([, init]) => init.signal?.aborted)).toBe(true)
    await act(async () => {
      if (stage === 'session') pending[0](json(family))
      else { pending[0](json(visit)); pending[1](json({}, 401)); pending[2](json({}, 503)) }
    })
    expect(fetchMock).toHaveBeenCalledTimes(stage === 'session' ? 1 : 4)
  })

  it('returns to the landing page when there is no session, without reading care data', async () => {
    const fetchMock = installApi((url) => url.pathname === '/api/auth/me' ? json({}, 401) : undefined)
    openProgress()
    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Username')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([path]) => path.startsWith('/api/visits'))).toBe(false)
  })

  it('links to the landing page to sign in with another account on 403', async () => {
    installApi((url) => url.pathname === '/api/auth/me' ? json({}, 403) : undefined)
    openProgress()
    expect(await screen.findByRole('link', { name: 'Sign in with another account' })).toHaveAttribute('href', '/')
    expect(screen.queryByText('Home care')).not.toBeInTheDocument()
  })

  it.each([400, 404])('clears all sections when a child endpoint returns %s', async (status) => {
    installApi((url) => url.pathname.endsWith('/timeline') ? json({}, status) : undefined)
    openProgress()
    expect(await screen.findByRole('heading', { name: status === 400 ? 'Invalid visit link' : 'Visit not found' })).toBeInTheDocument()
    expect(screen.queryByText('Home care')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })

  it.each(['network', 'server'])('recovers from a %s session-check failure without showing care data', async (failure) => {
    let unavailable = true
    const fetchMock = installApi((url) => url.pathname === '/api/auth/me' && unavailable
      ? failure === 'network' ? Promise.reject(new TypeError('Failed to fetch')) : json({}, 503) : undefined)
    openProgress()
    expect(await screen.findByRole('heading', { name: 'Unable to load visit progress' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    unavailable = false
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('1 of 4 tasks completed')).toBeInTheDocument()
  })

  it.each([
    ['SCHEDULED', 'Scheduled'], ['ARRIVED', 'Arrived'], ['IN_PROGRESS', 'In progress'],
    ['COMPLETED', 'Completed'], ['VERIFIED', 'Verified'], ['AUTO_CLOSED', 'Automatically closed'],
    ['EXCEPTION', 'Exception'], ['CANCELLED', 'Cancelled'],
  ])('shows stored %s without advancing it based on the clock', async (status, label) => {
    installApi((url) => url.pathname === '/api/visits/501' ? json({ ...visit, status }) : undefined)
    openProgress()
    const detail = await screen.findByRole('region', { name: 'Visit details' })
    expect(await within(detail).findByText(label)).toBeInTheDocument()
  })

  it('keeps missing visit facts and empty sections distinct from completed care', async () => {
    installApi((url) => {
      if (url.pathname === '/api/visits/501') return json({ ...visit, serviceType: null, caregiverId: null,
        status: 'SCHEDULED', scheduledEnd: null, checkedInAt: null, checkedOutAt: null })
      if (/\/(timeline|tasks)$/.test(url.pathname)) return json([])
    })
    openProgress()
    expect(await screen.findByText('No task records yet.')).toBeInTheDocument()
    expect(screen.getByText('No service updates recorded yet.')).toBeInTheDocument()
    expect(screen.getByText('Care visit')).toBeInTheDocument()
    expect(screen.getByText('Caregiver awaiting assignment')).toBeInTheDocument()
    expect(screen.getAllByText('Not recorded')).toHaveLength(3)
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('renders names as plain text and counts DONE even when its completion time is missing', async () => {
    const name = '<img src=x onerror=alert(1)>\n  A very long task description'
    installApi((url) => url.pathname.endsWith('/tasks') ? json([
      { ...tasks[0], name, completedAt: null }, { ...tasks[2], completedAt: visit.checkedInAt }, tasks[3],
    ]) : undefined)
    const { container } = openProgress()
    expect(await screen.findByText('1 of 3 tasks completed')).toBeInTheDocument()
    expect(screen.getByText(/<img src=x/).textContent).toBe(name)
    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByText('Completion time not recorded.')).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('max', '3')
  })

  it('formats overnight and UTC timestamps in Singapore and labels each section separately', async () => {
    installApi((url) => url.pathname === '/api/visits/501' ? json({ ...visit,
      scheduledStart: '2026-09-30T15:30:00Z', scheduledEnd: '2026-09-30T16:30:00Z' }) : undefined)
    const { container } = openProgress()
    await screen.findByText('1 of 4 tasks completed')
    expect(container.querySelector('time[datetime="2026-09-30T15:30:00Z"]')).toHaveTextContent('23:30')
    expect(container.querySelector('time[datetime="2026-09-30T16:30:00Z"]')).toHaveTextContent(/1 Oct 2026, 00:30/)
    expect(screen.getByText(/Visit details checked/)).toHaveTextContent('09:20')
    expect(screen.getByText(/Timeline loaded/).querySelector('time')).not.toHaveAttribute('datetime', visit.asOf)
    expect(screen.getByText(/Tasks loaded/).querySelector('time')).not.toHaveAttribute('datetime', visit.asOf)
  })

  it.each(['0', '-1', 'abc', '1.5', '1e3', '9223372036854775808', '%2Fprivate'])('rejects invalid visit ID %s without querying care data', async (id) => {
    const fetchMock = installApi()
    openProgress('/family/visits/' + id)
    expect(await screen.findByRole('heading', { name: 'Invalid visit link' })).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('preserves a valid long ID in requests without JavaScript number rounding', async () => {
    const id = '9223372036854775807'
    const fetchMock = installApi((url) => url.pathname.startsWith('/api/visits/') ? json({}, 404) : undefined)
    openProgress('/family/visits/' + id)
    expect(await screen.findByRole('heading', { name: 'Visit not found' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([path]) => path)).toContain('/api/visits/' + id)
    expect(screen.queryByText('No task records yet.')).not.toBeInTheDocument()
  })

  it.each(['MANAGER', 'CAREGIVER'])('does not request visit data for a %s session', async (role) => {
    const fetchMock = installApi((url) => url.pathname === '/api/auth/me' ? json({ ...family, roles: [role] }) : undefined)
    openProgress()
    expect(await screen.findByRole('heading', { name: 'Visit access unavailable' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/auth/me'])
  })

  it('accepts the prefixed family role', async () => {
    installApi((url) => url.pathname === '/api/auth/me' ? json({ ...family, roles: ['ROLE_FAMILY'] }) : undefined)
    openProgress()
    expect(await screen.findByText('1 of 4 tasks completed')).toBeInTheDocument()
  })

  it.each([
    ['/api/visits/501', 'Visit details'], ['/api/visits/501/timeline', 'Service timeline'], ['/api/visits/501/tasks', 'Task progress'],
  ])('allows independent sections to load while %s is pending', async (path, title) => {
    let finish!: (response: Response) => void
    installApi((url) => url.pathname === path ? new Promise<Response>((resolve) => { finish = resolve }) : undefined)
    openProgress()
    const section = await screen.findByRole('region', { name: title })
    expect(within(section).getByRole('status')).toHaveTextContent('Loading')
    await waitFor(() => expect(screen.getAllByRole('status')).toHaveLength(1))
    expect(screen.getByRole('button', { name: 'Refresh progress' })).toBeDisabled()
    await act(async () => finish(json(path.endsWith('/tasks') ? tasks : path.endsWith('/timeline') ? timeline : visit)))
    expect(await screen.findByText('1 of 4 tasks completed')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh progress' })).toBeEnabled()
  })

  it.each([
    ['/api/visits/501', 'Visit details'], ['/api/visits/501/timeline', 'Service timeline'], ['/api/visits/501/tasks', 'Task progress'],
  ])('retains the last successful %s data and timestamps with an explicit stale warning', async (path, title) => {
    let failed = false
    installApi((url) => failed && url.pathname === path ? json({ detail: 'PRIVATE server message' }, 503) : undefined)
    openProgress()
    await screen.findByText('1 of 4 tasks completed')
    const initialTimes = [...screen.getByRole('region', { name: title }).querySelectorAll('time')].map((time) => time.dateTime)
    failed = true
    await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh progress' }))
    const section = await screen.findByRole('region', { name: title })
    expect(await within(section).findByRole('alert')).toHaveTextContent('Unable to load')
    expect(within(section).getByRole('alert')).toHaveTextContent('out of date')
    expect([...section.querySelectorAll('time')].map((time) => time.dateTime)).toEqual(initialTimes)
    expect(screen.getByText('Home care')).toBeInTheDocument()
    expect(screen.getByText('1 of 4 tasks completed')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Service updates' })).toBeInTheDocument()
    expect(screen.queryByText('PRIVATE server message')).not.toBeInTheDocument()
    failed = false
    await userEvent.setup().click(within(section).getByRole('button'))
    expect(await screen.findByText('1 of 4 tasks completed')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('opens progress from assigned and unassigned schedule cards and returns to the schedule', async () => {
    installApi()
    const user = userEvent.setup()
    openProgress('/family/schedule')
    const link = await screen.findByRole('link', { name: 'View progress for visit 501' })
    expect(link).toHaveAttribute('href', '/family/visits/501')
    expect(screen.getByRole('link', { name: 'View progress for visit 502' })).toHaveAttribute('href', '/family/visits/502')
    await user.click(link)
    await screen.findByText('1 of 4 tasks completed')
    await user.click(screen.getByRole('link', { name: 'Back to schedule' }))
    expect(await screen.findByRole('list', { name: 'Scheduled visits' })).toBeInTheDocument()
  })
  it('opens a deep link through the family session and reads the three family projections', async () => {
    const fetchMock = installApi()
    openProgress('/family/visits/501?elderId=999&role=MANAGER&familyMemberId=999')
    expect(await screen.findByRole('heading', { name: 'Visit progress', level: 1 })).toBeInTheDocument()
    expect(await screen.findByText('1 of 4 tasks completed')).toBeInTheDocument()
    expect(document.title).toBe('Visit progress · CareLink')
    const detail = screen.getByRole('region', { name: 'Visit details' })
    expect(within(detail).getByText('Home care')).toBeInTheDocument()
    expect(within(detail).getByText('In progress')).toBeInTheDocument()
    expect(within(detail).getByText('Not recorded')).toBeInTheDocument()
    const history = screen.getByRole('list', { name: 'Service updates' })
    expect(within(history).getAllByRole('listitem')).toHaveLength(2)
    expect(history).toHaveTextContent('Scheduled → Arrived')
    expect(history).toHaveTextContent('Arrived → In progress')
    const taskList = screen.getByRole('list', { name: 'Visit tasks' })
    expect(within(taskList).getByText('Skipped')).toBeInTheDocument()
    expect(within(taskList).getByText('Refused')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Completed tasks' })).toHaveAttribute('value', '1')
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual([
      '/api/auth/me', '/api/visits/501', '/api/visits/501/timeline', '/api/visits/501/tasks',
    ])
    for (const [, init] of fetchMock.mock.calls) {
      expect(init.credentials).toBe('include')
      expect(init.signal).toBeInstanceOf(AbortSignal)
      expect(init.method ?? 'GET').toBe('GET')
    }
    expect(screen.getByRole('link', { name: 'Back to schedule' })).toHaveAttribute('href', '/family/schedule')
  })
})
