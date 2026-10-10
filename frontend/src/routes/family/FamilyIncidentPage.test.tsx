import { StrictMode } from 'react'
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import FamilyHome from './index'
import { NotificationBell } from '../../shared/components/notifications/NotificationBell'
import { NotificationsProvider } from '../../shared/components/notifications/NotificationsProvider'
import { NOTIFICATIONS_CHANGED_EVENT } from '../../features/notifications/api'
import { FamilyIncidentPage } from './incidents/FamilyIncidentPage'
import type { FamilyIncidentAcknowledgement } from '../../features/incidents/familyTypes'

// The navigation's waiting counts are tested with FamilyLayout and on Home; here they stay at zero so
// only this page's own requests are made.
vi.mock('./components/usePendingDecisions', () => ({
  usePendingDecisions: () => ({ changes: [], spotChecks: [], requests: [], total: 0 }),
}))

const family = { id: 7, username: 'family-a', displayName: 'Family A', roles: ['FAMILY'] }
const emptyReceipt = { id: null, incidentId: 601, familyMemberId: 42, viewedAt: null, acknowledgedAt: null, responseNote: null }
const viewed = { ...emptyReceipt, id: 51, viewedAt: '2026-10-07T16:10:00+08:00' }
const acknowledged = { ...viewed, acknowledgedAt: '2026-10-07T16:20:00+08:00', responseNote: 'I have spoken to the team.' }
const detail = { id: 601, elderId: 101, visitId: 201, source: 'CAREGIVER', category: 'FALL', severity: 'HIGH', status: 'OPEN',
  description: 'A fall was reported.\n\n  The care team is responding.', reportedAt: '2026-10-07T07:00:00Z', resolvedAt: null,
  acknowledgeBy: '2026-10-07T18:00:00+08:00', acknowledgement: emptyReceipt }
function json(data: unknown, status = 200) { return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } }) }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>((done) => { resolve = done }); return { promise, resolve } }
function installApi(override?: (url: URL, init: RequestInit) => Response | Promise<Response> | undefined) {
  let receipt: FamilyIncidentAcknowledgement = { ...emptyReceipt }
  const fetchMock = vi.fn((path: string, init: RequestInit) => {
    const url = new URL(path, 'http://localhost')
    const response = override?.(url, init)
    if (response !== undefined) return Promise.resolve(response)
    if (url.pathname === '/api/auth/me') return Promise.resolve(json(family))
    if (url.pathname === '/api/auth/csrf') { document.cookie = 'XSRF-TOKEN=test-token; path=/'; return Promise.resolve(new Response(null, { status: 204 })) }
    if (url.pathname === '/api/family/incidents/601') return Promise.resolve(json({ ...detail, acknowledgement: receipt }))
    if (url.pathname === '/api/incidents/601/view') { receipt = { ...receipt, ...viewed, acknowledgedAt: receipt.acknowledgedAt, responseNote: receipt.responseNote }; return Promise.resolve(json(receipt)) }
    if (url.pathname === '/api/incidents/601/acknowledge') { receipt = { ...receipt, ...acknowledged, viewedAt: receipt.viewedAt, responseNote: JSON.parse(init.body as string).responseNote }; return Promise.resolve(json(receipt)) }
    throw new Error(`Unexpected API request: ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
function Navigation() {
  const navigate = useNavigate()
  return <><button onClick={() => navigate('/family/incidents/602')}>Another incident</button><button onClick={() => navigate('/')}>Leave</button></>
}
function open(path = '/family/incidents/601', strict = false, state?: unknown) {
  const app = <MemoryRouter initialEntries={[{ pathname: path.split('?')[0], search: path.includes('?') ? path.slice(path.indexOf('?')) : '', state }]}><Navigation /><Routes>
    <Route path="/" element={<h1>Landing</h1>} /><Route path="/family/*" element={<FamilyHome />} />
  </Routes></MemoryRouter>
  return render(strict ? <StrictMode>{app}</StrictMode> : app)
}
function activity() {
  return { visibility: vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible'),
    online: vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true) }
}
async function pause(environment: ReturnType<typeof activity>, reason: 'hidden' | 'offline') {
  await act(async () => {
    if (reason === 'hidden') { environment.visibility.mockReturnValue('hidden'); document.dispatchEvent(new Event('visibilitychange')) }
    else { environment.online.mockReturnValue(false); window.dispatchEvent(new Event('offline')) }
  })
}
async function resume(environment: ReturnType<typeof activity>) {
  await act(async () => {
    environment.visibility.mockReturnValue('visible'); environment.online.mockReturnValue(true)
    window.dispatchEvent(new Event('online')); document.dispatchEvent(new Event('visibilitychange'))
    window.dispatchEvent(new Event('online')); document.dispatchEvent(new Event('visibilitychange'))
  })
}
beforeEach(() => vi.stubGlobal('scrollTo', vi.fn()))
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/' })

describe('FM05 family incident page', () => {
  it('opens a deep link with the family projection, then records viewing only after rendering', async () => {
    const fetchMock = installApi((url) => {
      if (url.pathname === '/api/incidents/601/view') expect(screen.getByRole('article', { name: 'Incident #601' })).toBeInTheDocument()
      return undefined
    })
    open('/family/incidents/601?elderId=999&familyMemberId=999&notificationId=999')
    const article = await screen.findByRole('article')
    await screen.findByText(/First viewed/)
    expect(document.title).toBe('Incident details · CareLink')
    expect(within(article).getByText('Elder profile #101 · Visit #201')).toBeInTheDocument()
    expect(within(article).getByText('High severity')).toBeInTheDocument()
    expect(within(article).getByText('Reported', { selector: 'span' })).toBeInTheDocument()
    expect(within(article).getByRole('region', { name: 'What happened' }).querySelector('p')?.textContent).toBe(detail.description)
    expect(within(article).getByText('7 Oct 2026, 15:00')).toBeInTheDocument()
    expect(within(article).getByText('7 Oct 2026, 18:00')).toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/auth/me', '/api/family/incidents/601', '/api/auth/me', '/api/auth/csrf', '/api/incidents/601/view'])
    const write = fetchMock.mock.calls.find(([path]) => path.endsWith('/view'))![1]
    expect(write.method).toBe('POST'); expect(write.body).toBeUndefined()
    expect(new Headers(write.headers).get('X-XSRF-TOKEN')).toBe('test-token')
    for (const [, init] of fetchMock.mock.calls) { expect(init.credentials).toBe('include'); expect(init.signal).toBeInstanceOf(AbortSignal) }
    expect(fetchMock.mock.calls.some(([path]) => path.includes('/notifications/'))).toBe(false)
    expect(screen.queryByText('Awareness confirmed')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Resolve|Take over/ })).not.toBeInTheDocument()
  })

  it('requires an explicit awareness click, submits only the note and shows the saved server receipt', async () => {
    const fetchMock = installApi(); open(); await screen.findByText(/First viewed/)
    const user = userEvent.setup()
    await user.type(screen.getByRole('textbox', { name: 'Optional note' }), 'I have spoken to the team.')
    await user.click(screen.getByRole('button', { name: 'I am aware' }))
    expect(await screen.findByText('Awareness confirmed')).toBeInTheDocument()
    expect(screen.getByText('I have spoken to the team.')).toBeInTheDocument()
    expect(screen.getByText('7 Oct 2026, 16:20')).toBeInTheDocument()
    const writes = fetchMock.mock.calls.filter(([path]) => path.endsWith('/acknowledge'))
    expect(writes).toHaveLength(1)
    expect(JSON.parse(writes[0][1].body as string)).toEqual({ responseNote: 'I have spoken to the team.' })
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.getByText('Reported', { selector: 'span' })).toBeInTheDocument()
  })

  it('allows awareness without a personal deadline and after resolution, without inventing times', async () => {
    const fetchMock = installApi((url) => url.pathname === '/api/family/incidents/601' ? json({ ...detail, status: 'RESOLVED', acknowledgeBy: null,
      visitId: null, description: null, acknowledgement: viewed }) : undefined)
    open(); await screen.findByRole('article')
    expect(screen.getByText('No personal response time is recorded. You can still confirm awareness.')).toBeInTheDocument()
    expect(screen.getByText('No description was recorded.')).toBeInTheDocument()
    expect(screen.queryByText(/Invalid Date|Resolved on/)).not.toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'I am aware' }))
    await screen.findByText('Awareness confirmed')
    expect(JSON.parse(fetchMock.mock.calls.find(([path]) => path.endsWith('/acknowledge'))![1].body as string)).toEqual({ responseNote: null })
  })

  it('shows an existing first acknowledgement and plain-text notes without sending another write', async () => {
    const note = '<img src=x onerror="alert(1)">\n\n  **Plain text**'
    const fetchMock = installApi((url) => url.pathname === '/api/family/incidents/601' ? json({ ...detail, description: note, acknowledgement: { ...acknowledged, responseNote: note } }) : undefined)
    open(); const article = await screen.findByRole('article')
    expect(await screen.findByText('Awareness confirmed')).toBeInTheDocument()
    expect(article.querySelector('img')).toBeNull()
    expect(within(article).getAllByText(/<img src=x/)).toHaveLength(2)
    expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/view') || path.endsWith('/acknowledge'))).toBe(false)
    expect(screen.queryByRole('button', { name: 'I am aware' })).not.toBeInTheDocument()
  })

  it('retains the note on failed acknowledgement and retries only on another explicit click', async () => {
    let fail = true
    const fetchMock = installApi((url) => url.pathname.endsWith('/acknowledge') && fail ? json({ detail: 'Private database error' }, 503) : undefined)
    open(); await screen.findByText(/First viewed/)
    const user = userEvent.setup(); const note = screen.getByRole('textbox')
    await user.type(note, 'Please keep me updated.')
    await user.click(screen.getByRole('button', { name: 'I am aware' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not confirm')
    expect(note).toHaveValue('Please keep me updated.')
    expect(screen.queryByText('Private database error')).not.toBeInTheDocument()
    expect(screen.queryByText('Awareness confirmed')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/acknowledge'))).toHaveLength(1)
    fail = false; await user.click(screen.getByRole('button', { name: 'I am aware' }))
    await screen.findByText('Awareness confirmed')
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/acknowledge'))).toHaveLength(2)
  })

  it('does not block awareness when the independent viewing receipt fails', async () => {
    let fail = true
    const fetchMock = installApi((url) => url.pathname.endsWith('/view') && fail ? json({}, 500) : undefined)
    open(); await screen.findByRole('button', { name: 'Retry viewing receipt' })
    expect(screen.queryByText(/First viewed/)).not.toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'I am aware' }))
    await screen.findByText('Awareness confirmed')
    fail = false; await userEvent.setup().click(screen.getByRole('button', { name: 'Retry viewing receipt' }))
    await screen.findByText(/First viewed/)
    expect(screen.getByText('Awareness confirmed')).toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/view'))).toHaveLength(2)
  })

  it('disables repeat submission from CSRF initialisation until the authoritative response', async () => {
    const response = deferred<Response>()
    const fetchMock = installApi((url) => url.pathname.endsWith('/acknowledge') ? response.promise : undefined)
    open(); await screen.findByText(/First viewed/)
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'I am aware' }))
    const saving = await screen.findByRole('button', { name: 'Confirming…' })
    expect(saving).toBeDisabled(); expect(screen.getByRole('textbox')).toBeDisabled()
    expect(screen.queryByText('Awareness confirmed')).not.toBeInTheDocument()
    await user.click(saving)
    await act(async () => response.resolve(json(acknowledged)))
    await screen.findByText('Awareness confirmed')
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/acknowledge'))).toHaveLength(1)
  })

  it('keeps awareness when an older viewing response arrives later', async () => {
    const response = deferred<Response>()
    installApi((url) => url.pathname.endsWith('/view') ? response.promise : undefined)
    open(); await screen.findByRole('article')
    await userEvent.setup().click(screen.getByRole('button', { name: 'I am aware' }))
    await screen.findByText('Awareness confirmed')
    await act(async () => response.resolve(json(viewed)))
    await screen.findByText(/First viewed/)
    expect(screen.getByText('Awareness confirmed')).toBeInTheDocument()
    expect(screen.getByText('7 Oct 2026, 16:20')).toBeInTheDocument()
  })

  it.each([401, 403])('clears all care content and note on acknowledgement status %s', async (status) => {
    installApi((url) => url.pathname.endsWith('/acknowledge') ? json({ detail: 'Private detail' }, status) : undefined)
    open(); await screen.findByText(/First viewed/)
    await userEvent.setup().click(screen.getByRole('button', { name: 'I am aware' }))
    await screen.findByRole('heading', { name: status === 401 ? 'Landing' : 'Incident access unavailable' })
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByText('Private detail')).not.toBeInTheDocument()
  })

  it.each([[400, 'Invalid incident link'], [403, 'Incident access unavailable'], [404, 'Incident not found'], [503, 'Unable to load this incident']])(
    'shows safe detail-load feedback for %s', async (status, heading) => {
      const fetchMock = installApi((url) => url.pathname === '/api/family/incidents/601' ? json({ detail: 'Private server error' }, status as number) : undefined)
      open(); await screen.findByRole('heading', { name: heading as string })
      expect(screen.queryByRole('article')).not.toBeInTheDocument()
      expect(screen.queryByText('Private server error')).not.toBeInTheDocument()
      expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/view'))).toBe(false)
    })

  it.each(['0', '-1', '1e2', 'abc', '9223372036854775808'])('rejects malformed incident link %s before any care read', async (id) => {
    const fetchMock = installApi(); open('/family/incidents/' + id)
    await screen.findByRole('heading', { name: 'Invalid incident link' })
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rechecks the session before writing and clears the former account content if it changes', async () => {
    let changed = false
    const fetchMock = installApi((url) => changed && url.pathname === '/api/auth/me' ? json({ ...family, id: 9 }) : undefined)
    open(); await screen.findByText(/First viewed/); changed = true
    await userEvent.setup().click(screen.getByRole('button', { name: 'I am aware' }))
    await screen.findByRole('heading', { name: 'Incident access unavailable' })
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/acknowledge'))).toBe(false)
  })

  it('cancels a pending receipt on incident change and ignores its late response', async () => {
    const response = deferred<Response>()
    const fetchMock = installApi((url) => url.pathname.endsWith('/601/view') ? response.promise
      : url.pathname === '/api/family/incidents/602' ? json({ ...detail, id: 602, description: 'New incident', acknowledgement: { ...acknowledged, incidentId: 602 } }) : undefined)
    open(); await screen.findByRole('article')
    await waitFor(() => expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/view'))).toBe(true))
    await userEvent.setup().click(screen.getByRole('button', { name: 'Another incident' }))
    await screen.findByText('New incident')
    const signal = fetchMock.mock.calls.find(([path]) => path.endsWith('/601/view'))![1].signal!
    expect(signal.aborted).toBe(true)
    await act(async () => response.resolve(json(viewed)))
    expect(screen.getByRole('article')).toHaveAccessibleName('Incident #602')
    expect(screen.queryByText(detail.description)).not.toBeInTheDocument()
  })

  it('retries a failed detail load with a fresh family check and no premature viewing write', async () => {
    let fail = true
    const fetchMock = installApi((url) => fail && url.pathname === '/api/family/incidents/601' ? json({}, 503) : undefined)
    open(); await screen.findByRole('heading', { name: 'Unable to load this incident' })
    expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/view'))).toBe(false)
    fail = false
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))
    await screen.findByText(/First viewed/)
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/family/incidents/601')).toHaveLength(2)
  })

  it('reloads the saved first receipt on refresh without an additional viewing command', async () => {
    const fetchMock = installApi(); open(); await screen.findByText(/First viewed/)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByText(/First viewed/)
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/family/incidents/601')).toHaveLength(2)
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/view'))).toHaveLength(1)
  })

  it.each([['MANAGER', 'Incident access unavailable'], ['ROLE_FAMILY', 'Incident #601']])(
    'checks the current %s role before reading care content', async (role, heading) => {
      const fetchMock = installApi((url) => url.pathname === '/api/auth/me' ? json({ ...family, roles: [role] }) : undefined)
      open(); await screen.findByRole('heading', { name: heading })
      if (role === 'MANAGER') expect(fetchMock.mock.calls.some(([path]) => path.includes('/family/incidents/'))).toBe(false)
    })

  it('limits the optional note to the server limit while retaining plain text and whitespace', async () => {
    const fetchMock = installApi(); open(); await screen.findByText(/First viewed/)
    const note = ' '.repeat(2) + 'x'.repeat(254)
    const user = userEvent.setup()
    await user.type(screen.getByRole('textbox'), note)
    expect(screen.getByRole('textbox')).toHaveValue(note.slice(0,255))
    expect(screen.getByText('255/255 characters')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'I am aware' }))
    await screen.findByText('Awareness confirmed')
    expect(JSON.parse(fetchMock.mock.calls.find(([path]) => path.endsWith('/acknowledge'))![1].body as string).responseNote).toBe(note.slice(0,255))
  })

  it('disables confirmation during CSRF setup and cancels the pending write when leaving', async () => {
    const csrf = deferred<Response>()
    let hold = false
    const fetchMock = installApi((url) => hold && url.pathname === '/api/auth/csrf' ? csrf.promise : undefined)
    open(); await screen.findByText(/First viewed/); hold = true
    await userEvent.setup().click(screen.getByRole('button', { name: 'I am aware' }))
    await waitFor(() => expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/csrf')).toHaveLength(2))
    expect(screen.getByRole('button', { name: 'Confirming…' })).toBeDisabled()
    expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/acknowledge'))).toBe(false)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Leave' }))
    await screen.findByRole('heading', { name: 'Landing' })
    await act(async () => csrf.resolve(new Response(null, { status: 204 })))
    expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/acknowledge'))).toBe(false)
  })

  it('starts no requests until the page is both visible and online', async () => {
    const environment = activity()
    environment.visibility.mockReturnValue('hidden'); environment.online.mockReturnValue(false)
    const fetchMock = installApi(); open()
    await screen.findByText(/Updates paused while offline/)
    expect(fetchMock).not.toHaveBeenCalled()
    await act(async () => { environment.online.mockReturnValue(true); window.dispatchEvent(new Event('online')) })
    expect(screen.getByText(/Updates paused while this tab is hidden/)).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
    await resume(environment); await screen.findByText(/First viewed/)
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/family/incidents/601')).toHaveLength(1)
  })

  it.each(['hidden', 'offline'] as const)('cancels an unfinished detail on %s and reloads once without accepting the old response', async (reason) => {
    const environment = activity(); const response = deferred<Response>(); let hold = true
    const fetchMock = installApi((url) => url.pathname === '/api/family/incidents/601'
      ? hold ? response.promise : json({ ...detail, description: 'Fresh care detail', acknowledgement: viewed }) : undefined)
    open(); await waitFor(() => expect(fetchMock.mock.calls.some(([path]) => path === '/api/family/incidents/601')).toBe(true))
    const signal = fetchMock.mock.calls.find(([path]) => path === '/api/family/incidents/601')![1].signal!
    await pause(environment, reason)
    expect(signal.aborted).toBe(true); expect(screen.queryByRole('article')).not.toBeInTheDocument()
    hold = false; await resume(environment); await screen.findByText('Fresh care detail')
    await act(async () => response.resolve(json({ ...detail, description: 'Obsolete care detail' })))
    expect(screen.queryByText('Obsolete care detail')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/family/incidents/601')).toHaveLength(2)
  })

  it('retains only the same account draft and refreshes a newly established window on return', async () => {
    const environment = activity(); let returning = false
    installApi((url) => url.pathname === '/api/family/incidents/601'
      ? json({ ...detail, acknowledgeBy: returning ? detail.acknowledgeBy : null, acknowledgement: viewed }) : undefined)
    open(); await screen.findByRole('article')
    await userEvent.setup().type(screen.getByRole('textbox'), 'Please call me.')
    await pause(environment, 'offline')
    expect(screen.queryByRole('article')).not.toBeInTheDocument(); expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    returning = true; await resume(environment)
    expect(await screen.findByRole('textbox')).toHaveValue('Please call me.')
    expect(screen.getByText('7 Oct 2026, 18:00')).toBeInTheDocument()
  })

  it.each([true, false])('queries an interrupted awareness command without resending it (committed=%s)', async (committed) => {
    const environment = activity(); const response = deferred<Response>(); let returning = false
    const fetchMock = installApi((url) => url.pathname.endsWith('/acknowledge') ? response.promise
      : returning && url.pathname === '/api/family/incidents/601' ? json({ ...detail, acknowledgement: committed ? acknowledged : viewed }) : undefined)
    open(); await screen.findByText(/First viewed/)
    await userEvent.setup().type(screen.getByRole('textbox'), 'My unsaved note')
    await userEvent.setup().click(screen.getByRole('button', { name: 'I am aware' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/acknowledge'))).toBe(true))
    const signal = fetchMock.mock.calls.find(([path]) => path.endsWith('/acknowledge'))![1].signal!
    await pause(environment, 'offline'); expect(signal.aborted).toBe(true)
    returning = true; await resume(environment); await screen.findByRole('article')
    if (committed) { expect(await screen.findByText('Awareness confirmed')).toBeInTheDocument(); expect(screen.getByText(acknowledged.responseNote)).toBeInTheDocument() }
    else { expect(await screen.findByRole('alert')).toHaveTextContent('We could not confirm'); expect(screen.getByRole('textbox')).toHaveValue('My unsaved note') }
    await act(async () => response.resolve(json({ ...acknowledged, responseNote: 'Ignored late response' })))
    expect(screen.queryByText('Ignored late response')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/acknowledge'))).toHaveLength(1)
  })

  it('does not send an old awareness command after CSRF setup completes during a pause', async () => {
    const environment = activity(); const csrf = deferred<Response>(); let hold = false
    const fetchMock = installApi((url) => hold && url.pathname === '/api/auth/csrf' ? csrf.promise : undefined)
    open(); await screen.findByText(/First viewed/); hold = true
    await userEvent.setup().click(screen.getByRole('button', { name: 'I am aware' }))
    await waitFor(() => expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/csrf')).toHaveLength(2))
    await pause(environment, 'hidden')
    await act(async () => csrf.resolve(new Response(null, { status: 204 })))
    expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/acknowledge'))).toBe(false)
    hold = false; await resume(environment); await screen.findByRole('button', { name: 'I am aware' })
    expect(screen.getByRole('alert')).toHaveTextContent('We could not confirm')
  })

  it('clears the previous account draft and receipt on a new family session', async () => {
    const environment = activity(); let returning = false
    installApi((url) => returning && url.pathname === '/api/auth/me' ? json({ ...family, id: 9, username: 'family-b' })
      : returning && url.pathname === '/api/family/incidents/601' ? json({ ...detail, description: 'New account care detail', acknowledgement: { ...viewed, familyMemberId: 7 } }) : undefined)
    open(); await screen.findByText(/First viewed/)
    await userEvent.setup().type(screen.getByRole('textbox'), 'Old account private note')
    await pause(environment, 'hidden'); returning = true; await resume(environment)
    await screen.findByText('New account care detail')
    expect(screen.getByRole('textbox')).toHaveValue('')
    expect(screen.queryByText('Old account private note')).not.toBeInTheDocument()
  })

  it.each([401, 403].flatMap((status) => ['/api/auth/me', '/api/family/incidents/601'].map((path) => ({ status, path }))))(
    'clears care data and refuses automatic retries after $status from $path on resume', async ({ status, path }) => {
      const environment = activity(); let returning = false
      const fetchMock = installApi((url) => returning && url.pathname === path ? json({}, status) : undefined)
      open(); await screen.findByText(/First viewed/)
      await userEvent.setup().type(screen.getByRole('textbox'), 'A private draft')
      await pause(environment, 'hidden'); returning = true; await resume(environment)
      await screen.findByRole('heading', { name: status === 401 ? 'Landing' : 'Incident access unavailable' })
      expect(screen.queryByRole('article')).not.toBeInTheDocument(); expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
      const count = fetchMock.mock.calls.length
      await pause(environment, 'offline'); await resume(environment)
      expect(fetchMock).toHaveBeenCalledTimes(count)
    })

  it('allows explicit reauthorization after access is restored without restoring the old draft', async () => {
    const environment = activity(); let denied = false
    installApi((url) => denied && url.pathname === '/api/family/incidents/601' ? json({}, 403) : undefined)
    open(); await screen.findByText(/First viewed/); await userEvent.setup().type(screen.getByRole('textbox'), 'Old private draft')
    await pause(environment, 'hidden'); denied = true; await resume(environment)
    await screen.findByRole('heading', { name: 'Incident access unavailable' }); denied = false
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))
    await screen.findByRole('article'); expect(screen.getByRole('textbox')).toHaveValue('')
  })

  it('recovers a transient failed read when the network returns', async () => {
    const environment = activity(); let failed = true
    const fetchMock = installApi((url) => failed && url.pathname === '/api/family/incidents/601' ? Promise.reject(new TypeError('Offline')) : undefined)
    open(); await screen.findByRole('heading', { name: 'Unable to load this incident' })
    await pause(environment, 'offline'); failed = false; await resume(environment)
    await screen.findByText(/First viewed/)
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/family/incidents/601')).toHaveLength(2)
  })

  it('cancels an old detail on route change and removes recovery listeners on leaving', async () => {
    const environment = activity(); const response = deferred<Response>()
    const fetchMock = installApi((url) => url.pathname === '/api/family/incidents/601' ? response.promise
      : url.pathname === '/api/family/incidents/602' ? json({ ...detail, id: 602, description: 'Current incident', acknowledgement: { ...acknowledged, incidentId: 602 } }) : undefined)
    open(); await waitFor(() => expect(fetchMock.mock.calls.some(([path]) => path === '/api/family/incidents/601')).toBe(true))
    const signal = fetchMock.mock.calls.find(([path]) => path === '/api/family/incidents/601')![1].signal!
    await userEvent.setup().click(screen.getByRole('button', { name: 'Another incident' })); await screen.findByText('Current incident')
    expect(signal.aborted).toBe(true)
    await act(async () => response.resolve(json(detail)))
    expect(screen.getByRole('article')).toHaveAccessibleName('Incident #602')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Leave' })); await screen.findByRole('heading', { name: 'Landing' })
    const count = fetchMock.mock.calls.length; await pause(environment, 'offline'); await resume(environment)
    expect(fetchMock).toHaveBeenCalledTimes(count)
  })

  it('does not let an aborted session denial overwrite a resumed successful read', async () => {
    const environment = activity(); const response = deferred<Response>(); let hold = true
    const fetchMock = installApi((url) => hold && url.pathname === '/api/auth/me' ? response.promise : undefined)
    open(); await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    await pause(environment, 'offline'); hold = false; await resume(environment); await screen.findByText(/First viewed/)
    await act(async () => response.resolve(json({}, 403)))
    expect(screen.getByRole('article')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Incident access unavailable' })).not.toBeInTheDocument()
  })

  it('does not create duplicate viewing requests under StrictMode', async () => {
    const fetchMock = installApi(); open('/family/incidents/601', true)
    await screen.findByText(/First viewed/)
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/view'))).toHaveLength(1)
  })
})

const notice = { id: 801, elderId: 101, eventType: 'INCIDENT_RAISED', channel: 'IN_APP', title: 'HIGH: FALL', body: 'A care incident was reported.',
  resourceType: 'INCIDENT', resourceId: 601, status: 'SENT', createdAt: detail.reportedAt, sentAt: detail.reportedAt, readAt: null }
const context = { familyIncidentNotification: { id: 801, incidentId: 601 } }
function notificationApi(override?: (url: URL, init: RequestInit) => Response | Promise<Response> | undefined) {
  let read = false
  return installApi((url, init) => {
    const response = override?.(url, init)
    if (response !== undefined) return response
    if (url.pathname === '/api/notifications/me/unread-count') return json({ unread: read ? 0 : 1 })
    if (url.pathname === '/api/notifications/me') return json({ items: [{ ...notice, status: read ? 'READ' : 'SENT' }], page: 0, size: 20, totalElements: 1 })
    if (url.pathname === '/api/notifications/801/read') { read = true; return json({ ...notice, status: 'READ', readAt: viewed.viewedAt }) }
    return undefined
  })
}
function bellAndDetail() {
  return render(<NotificationsProvider><MemoryRouter initialEntries={['/family/start']}>
    <NotificationBell /><Navigation /><Routes>
      <Route path="/family/start" element={<h1>Family inbox</h1>} />
      <Route path="/family/incidents/:id" element={<FamilyIncidentPage />} />
      <Route path="/" element={<h1>Landing</h1>} />
    </Routes>
  </MemoryRouter></NotificationsProvider>)
}
async function chooseIncident() {
  await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
  await userEvent.click(await screen.findByRole('button', { name: /HIGH: FALL/ }))
}

describe('FM05 bell to authorized detail', () => {
  it('renders authorized details before independent read/view writes and refreshes the bell only on confirmed read', async () => {
    const response = deferred<Response>()
    let loaded = false
    const fetchMock = notificationApi((url) => {
      if (url.pathname === '/api/family/incidents/601' && !loaded) return response.promise
      if (url.pathname.endsWith('/view') || url.pathname === '/api/notifications/801/read') {
        expect(screen.getByRole('article', { name: 'Incident #601' })).toBeInTheDocument()
      }
      return undefined
    })
    bellAndDetail(); await chooseIncident()
    expect(await screen.findByText('Loading incident details…')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/view') || path.endsWith('/read'))).toBe(false)
    expect(screen.getByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument()
    loaded = true; await act(async () => response.resolve(json(detail)))
    await screen.findByText('Notification marked as read.'); await screen.findByText(/First viewed/)
    await screen.findByRole('button', { name: 'Notifications' })
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/notifications/801/read')).toHaveLength(1)
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/view'))).toHaveLength(1)
    expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/acknowledge'))).toBe(false)
    expect(screen.getByRole('button', { name: 'I am aware' })).toBeInTheDocument()
    const command = fetchMock.mock.calls.find(([path]) => path === '/api/notifications/801/read')![1]
    expect(command.body).toBeUndefined(); expect(command.method).toBe('POST')
    expect(new Headers(command.headers).get('X-XSRF-TOKEN')).toBe('test-token')
  })

  it('failed read keeps the bell unread, permits independent awareness and retries only on explicit click', async () => {
    let fail = true
    const fetchMock = notificationApi((url) => url.pathname === '/api/notifications/801/read' && fail ? json({ detail: 'Private notification fault' }, 503) : undefined)
    bellAndDetail(); await chooseIncident()
    await screen.findByRole('button', { name: 'Retry notification read' }); await screen.findByText(/First viewed/)
    expect(screen.getByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument()
    expect(screen.queryByText('Notification marked as read.')).not.toBeInTheDocument()
    expect(screen.queryByText('Private notification fault')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'I am aware' }))
    await screen.findByText('Awareness confirmed')
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/notifications/801/read')).toHaveLength(1)
    fail = false; await userEvent.click(screen.getByRole('button', { name: 'Retry notification read' }))
    await screen.findByText('Notification marked as read.'); await screen.findByRole('button', { name: 'Notifications' })
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/notifications/801/read')).toHaveLength(2)
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/view'))).toHaveLength(1)
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/acknowledge'))).toHaveLength(1)
    expect(screen.getByText('7 Oct 2026, 18:00')).toBeInTheDocument()
  })

  it('a failed view does not undo confirmed notification read or invent awareness', async () => {
    notificationApi((url) => url.pathname.endsWith('/view') ? json({}, 503) : undefined)
    open('/family/incidents/601', true, context)
    await screen.findByText('Notification marked as read.')
    await screen.findByRole('button', { name: 'Retry viewing receipt' })
    expect(screen.queryByText(/First viewed/)).not.toBeInTheDocument()
    expect(screen.queryByText('Awareness confirmed')).not.toBeInTheDocument()
  })

  it.each([401, 403, 404, 503])('denied or failed detail %s never marks notification read or viewed', async (status) => {
    const fetchMock = notificationApi((url) => url.pathname === '/api/family/incidents/601' ? json({}, status) : undefined)
    bellAndDetail(); await chooseIncident()
    await screen.findByRole('heading', { name: status === 401 ? 'Landing' : status === 403 ? 'Incident access unavailable' : status === 404 ? 'Incident not found' : 'Unable to load this incident' })
    expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/view') || path.endsWith('/read') || path.endsWith('/acknowledge'))).toBe(false)
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })

  it.each([403, 404])('loss of notification access %s clears care content until reauthorized', async (status) => {
    notificationApi((url) => url.pathname === '/api/notifications/801/read' ? json({}, status) : undefined)
    open('/family/incidents/601', false, context)
    await screen.findByRole('heading', { name: 'Incident access unavailable' })
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    expect(screen.queryByText('Notification marked as read.')).not.toBeInTheDocument()
  })

  it.each([null, { familyIncidentNotification: { id: 0, incidentId: 601 } }, { familyIncidentNotification: { id: 801, incidentId: 602 } },
    { familyIncidentNotification: { id: '801', incidentId: 601 } }, { familyIncidentNotification: { id: Number.MAX_SAFE_INTEGER + 1, incidentId: 601 } }])(
    'ignores invalid or mismatched notification context %j', async (state) => {
      const fetchMock = notificationApi(); open('/family/incidents/601', false, state)
      await screen.findByText(/First viewed/)
      expect(fetchMock.mock.calls.some(([path]) => path.includes('/notifications/'))).toBe(false)
    })

  it.each([{ id: 802 }, { resourceId: 602 }, { status: 'SENT' }])('rejects an unconfirmed notification receipt %j without refreshing the bell', async (change) => {
    const changed = vi.fn()
    window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, changed)
    try {
      const fetchMock = notificationApi((url) => url.pathname === '/api/notifications/801/read' ? json({ ...notice, status: 'READ', ...change }) : undefined)
      open('/family/incidents/601', false, context)
      await screen.findByRole('button', { name: 'Retry notification read' }); await screen.findByText(/First viewed/)
      expect(changed).not.toHaveBeenCalled()
      expect(screen.queryByText('Notification marked as read.')).not.toBeInTheDocument()
      expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/acknowledge'))).toBe(false)
    } finally { window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, changed) }
  })

  it.each(['offline', 'hidden'] as const)('cancels a pending notification read when %s and reauthorizes before resuming', async (reason) => {
    const environment = activity(); const response = deferred<Response>(); let hold = true
    const fetchMock = notificationApi((url) => hold && url.pathname === '/api/notifications/801/read' ? response.promise : undefined)
    open('/family/incidents/601', false, context)
    await waitFor(() => expect(fetchMock.mock.calls.some(([path]) => path === '/api/notifications/801/read')).toBe(true))
    const signal = fetchMock.mock.calls.find(([path]) => path === '/api/notifications/801/read')![1].signal!
    await pause(environment, reason); expect(signal.aborted).toBe(true)
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    const count = fetchMock.mock.calls.length
    hold = false; await resume(environment); await screen.findByText('Notification marked as read.')
    const resumed = fetchMock.mock.calls.slice(count).map(([path]) => path)
    expect(resumed.slice(0, 2)).toEqual(['/api/auth/me', '/api/family/incidents/601'])
    await act(async () => response.resolve(json({}, 403)))
    expect(screen.getByRole('article')).toBeInTheDocument()
    expect(screen.getByText('Notification marked as read.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/acknowledge'))).toBe(false)
  })

  it('aborts notification read on leaving and ignores its late success', async () => {
    const response = deferred<Response>(); const changed = vi.fn()
    window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, changed)
    try {
      const fetchMock = notificationApi((url) => url.pathname === '/api/notifications/801/read' ? response.promise : undefined)
      open('/family/incidents/601', false, context); await screen.findByText('Marking notification as read…')
      await waitFor(() => expect(fetchMock.mock.calls.some(([path]) => path === '/api/notifications/801/read')).toBe(true))
      await userEvent.click(screen.getByRole('button', { name: 'Leave' }))
      const signal = fetchMock.mock.calls.find(([path]) => path === '/api/notifications/801/read')![1].signal!
      expect(signal.aborted).toBe(true)
      await act(async () => response.resolve(json({ ...notice, status: 'READ' })))
      expect(changed).not.toHaveBeenCalled()
      expect(screen.queryByText('Notification marked as read.')).not.toBeInTheDocument()
    } finally { window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, changed) }
  })
})
