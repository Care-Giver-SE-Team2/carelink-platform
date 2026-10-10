import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import FamilyHome from './index'

// The navigation's waiting counts are tested with FamilyLayout and on Home; here they stay at zero so
// only this page's own requests are made.
vi.mock('./components/usePendingDecisions', () => ({
  usePendingDecisions: () => ({ changes: [], spotChecks: [], requests: [], total: 0 }),
}))

const family = { id: 11, username: 'family_test', displayName: 'Family Test', roles: ['FAMILY'] }
const body = 'Completed two visits.\n\n  Follow-up: continue the agreed care plan.\n'
const disclaimer = 'This summary records care observations and services; it is not a diagnosis or medical advice.'
const report = {
  id: 301, elderId: 21, audience: 'FAMILY', periodStart: '2026-09-21', periodEnd: '2026-09-27',
  status: 'PUBLISHED', dataComplete: false, missingItems: ['One visit record is missing.'], generatedBy: 'TEMPLATE',
  createdAt: '2026-09-27T16:30:00Z', archivedAt: null,
  sections: [{ title: 'Service completion', body }, { title: 'Observations', body: 'Comfortable during the visit.' }],
  disclaimer, amendments: [{ id: 71, note: 'Visit duration corrected to 45 minutes.\nOriginal report retained.', createdAt: '2026-09-28T02:00:00+08:00' }],
}
function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
}
function installApi(override?: (url: URL, init: RequestInit) => Response | Promise<Response> | undefined) {
  const fetchMock = vi.fn((path: string, init: RequestInit) => {
    const url = new URL(path, 'http://localhost')
    const response = override?.(url, init)
    if (response !== undefined) return Promise.resolve(response)
    if (url.pathname === '/api/auth/me') return Promise.resolve(json(family))
    if (url.pathname === '/api/reports/301') return Promise.resolve(json(report))
    throw new Error(`Unexpected API request: ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
function CurrentUrl() {
  const location = useLocation()
  const navigate = useNavigate()
  return <>
    <button onClick={() => navigate('/family/reports/302')}>Open another report</button>
    <button onClick={() => navigate(-1)}>Browser back</button>
    <div aria-label="Current URL">{location.pathname}{location.search}</div>
  </>
}
function openReport(path = '/family/reports/301') {
  return render(<MemoryRouter initialEntries={[path]}>
    <CurrentUrl />
    <Routes>
      <Route path="/" element={<h1>Landing</h1>} />
      <Route path="/family/*" element={<FamilyHome />} />
    </Routes>
  </MemoryRouter>)
}
beforeEach(() => vi.stubGlobal('scrollTo', vi.fn()))
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('Family care report reading', () => {
  it('opens a deep link after checking the family session and preserves the filed text and corrections', async () => {
    const fetchMock = installApi()
    openReport('/family/reports/301?elderId=999&page=2&audience=INTERNAL&familyMemberId=999')
    const article = await screen.findByRole('article', { name: '21 Sep – 27 Sep 2026' })
    expect(document.title).toBe('Care report · CareLink')
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/auth/me', '/api/reports/301'])
    for (const [, init] of fetchMock.mock.calls) {
      expect(init.credentials).toBe('include')
      expect(init.signal).toBeInstanceOf(AbortSignal)
      expect(init.method ?? 'GET').toBe('GET')
    }
    expect(within(article).getByText('Elder profile #21')).toBeInTheDocument()
    expect(within(article).getByLabelText('Care context at report generation')).toHaveTextContent('Main caregiver: Not recorded · Care plan: Not recorded')
    expect(within(article).getByText('Published')).toBeInTheDocument()
    expect(within(article).getByText('Structured template')).toBeInTheDocument()
    expect(within(article).getByText('Some care records are missing')).toBeInTheDocument()
    expect(within(article).getByText('One visit record is missing.')).toBeInTheDocument()
    expect(within(article).getByRole('region', { name: 'Visits' }).querySelector('p')?.textContent).toBe(body)
    expect(within(article).getByText(disclaimer)).toBeInTheDocument()
    const corrections = within(article).getByRole('region', { name: 'Corrections and follow-ups' })
    expect(corrections.querySelector('p')?.textContent).toBe('Visit duration corrected to 45 minutes.\nOriginal report retained.')
    expect(within(article).getByText('28 Sept 2026, 00:30')).toBeInTheDocument()
    expect(within(corrections).getByText('28 Sept 2026, 02:00')).toBeInTheDocument()
    expect(within(article).getByText('Times are shown in Singapore time.')).toBeInTheDocument()
    expect(screen.queryByText(/Archived on|null|Invalid Date/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Generate|Correct|Download/ })).not.toBeInTheDocument()
  })

  it.each([
    ['/family/reports', 0, '/family/reports?elderId=21'],
    ['/family/reports?elderId=21&page=1', 1, '/family/reports?elderId=21&page=1'],
  ])('reads from %s and returns to the same elder and page', async (path, page, backUrl) => {
    const fetchMock = installApi((url) => {
      if (url.pathname === '/api/elders') return json([{ id: 21, fullName: 'Tan Mei' }])
      if (url.pathname === '/api/reports') return json({ items: [report], page, size: 20, totalElements: 21 })
      return undefined
    })
    openReport(path)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('link', { name: 'Read report 301' }))
    await screen.findByRole('article')
    expect(screen.getByLabelText('Current URL')).toHaveTextContent(backUrl.replace('/reports?', '/reports/301?'))
    await user.click(screen.getByRole('link', { name: 'Back to reports' }))
    expect(await screen.findByRole('combobox', { name: 'Care for' })).toHaveValue('21')
    expect(screen.getByLabelText('Current URL')).toHaveTextContent(backUrl)
    const lastQuery = new URL(fetchMock.mock.calls.filter(([url]) => url.startsWith('/api/reports?')).at(-1)![0], 'http://localhost').searchParams
    expect(lastQuery.get('elderId')).toBe('21')
    expect(lastQuery.get('page')).toBe(String(page))
  })

  it.each([401, 403].flatMap((status) => ['/api/auth/me', '/api/reports/301'].map((endpoint) => ({ status, endpoint }))))(
    'clears the report after $status from $endpoint on refresh', async ({ status, endpoint }) => {
      let denied = false
      installApi((url) => denied && url.pathname === endpoint ? json({ detail: 'Internal access details' }, status) : undefined)
      openReport()
      await screen.findByRole('article')
      denied = true
      await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh' }))
      expect(await screen.findByRole('heading', { name: status === 401 ? 'Landing' : 'Report access unavailable' })).toBeInTheDocument()
      expect(screen.queryByRole('article')).not.toBeInTheDocument()
      expect(screen.queryByText('Elder profile #21')).not.toBeInTheDocument()
      expect(screen.queryByText('Internal access details')).not.toBeInTheDocument()
      if (status === 403) expect(screen.getByRole('link', { name: 'Back to reports' })).toBeInTheDocument()
    },
  )

  it.each([
    [400, 'Invalid report link'],
    [404, 'Report not found'],
    [500, 'Unable to load this report'],
    [503, 'Unable to load this report'],
  ])('distinguishes status %s without exposing the server error', async (status, heading) => {
    let failed = true
    installApi((url) => failed && url.pathname === '/api/reports/301' ? json({ detail: 'Private server message' }, status) : undefined)
    openReport()
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
    expect(screen.queryByText('Private server message')).not.toBeInTheDocument()
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to reports' })).toHaveAttribute('href', '/family/reports')
    if (status >= 500) {
      failed = false
      await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))
      expect(await screen.findByRole('article')).toBeInTheDocument()
    }
  })

  it('reads an archived report with no sections or corrections without inventing an archive time', async () => {
    installApi((url) => url.pathname === '/api/reports/301' ? json({
      ...report, status: 'ARCHIVED', generatedBy: 'MODEL', dataComplete: true, missingItems: [], sections: [], amendments: [],
    }) : undefined)
    openReport()
    const article = await screen.findByRole('article')
    expect(within(article).getByText('Archived')).toBeInTheDocument()
    expect(within(article).getByText('Language-model summary')).toBeInTheDocument()
    expect(within(article).getByText('Records complete')).toBeInTheDocument()
    expect(within(article).getByText('No report sections were recorded.')).toBeInTheDocument()
    expect(within(article).getByText('No corrections or follow-ups have been added.')).toBeInTheDocument()
    expect(within(article).getByText(disclaimer)).toBeInTheDocument()
    expect(within(article).queryByText(/Archived on/)).not.toBeInTheDocument()
  })

  it('keeps section order, blank lines and HTML-looking text as literal text', async () => {
    const note = '<img src=x onerror="alert(1)">\n\n  **Recorded observation**\n'
    installApi((url) => url.pathname === '/api/reports/301' ? json({ ...report,
      missingItems: [note], sections: [{ title: 'Incidents', body: note }, { title: 'Observations', body: '' }],
      amendments: [{ id: 71, note, createdAt: '2026-09-28T02:00:00+08:00' }],
    }) : undefined)
    openReport()
    const article = await screen.findByRole('article')
    expect(within(article).getAllByRole('heading', { level: 2 }).map((node) => node.textContent)).toEqual(['Incidents', 'Caregiver notes', 'Corrections and follow-ups'])
    expect(within(article).getByRole('region', { name: 'Incidents' }).querySelector('p')?.textContent).toBe(note)
    expect(within(article).getByRole('region', { name: 'Caregiver notes' }).querySelector('p')?.textContent).toBe('')
    expect(within(article).getByRole('region', { name: 'Corrections and follow-ups' }).querySelector('p')?.textContent).toBe(note)
    expect(article.querySelector('img')).toBeNull()
    expect(article.querySelector('strong')).toBeNull()
  })

  it('appends a newly filed correction on refresh without changing the original body', async () => {
    let corrected = false
    installApi((url) => corrected && url.pathname === '/api/reports/301' ? json({ ...report,
      amendments: [...report.amendments, { id: 72, note: 'Additional correction.', createdAt: '2026-09-29T03:00:00+08:00' }],
    }) : undefined)
    openReport()
    await screen.findByRole('article')
    corrected = true
    await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByText('Additional correction.')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Visits' }).querySelector('p')?.textContent).toBe(body)
    expect(Array.from(screen.getByRole('region', { name: 'Corrections and follow-ups' }).querySelectorAll('p')).map((node) => node.textContent))
      .toEqual(['Visit duration corrected to 45 minutes.\nOriginal report retained.', 'Additional correction.'])
  })

  it('blocks a manager session before requesting the shared report endpoint', async () => {
    const fetchMock = installApi((url) => url.pathname === '/api/auth/me' ? json({ ...family, roles: ['MANAGER'] }) : undefined)
    openReport()
    await screen.findByRole('heading', { name: 'Report access unavailable' })
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/auth/me'])
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })

  it('returns to the landing page on 401 and never shows the report', async () => {
    installApi((url) => url.pathname === '/api/reports/301' ? new Response(null, { status: 401 }) : undefined)
    openReport('/family/reports/301?elderId=21&page=1')
    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Username')).not.toBeInTheDocument()
  })

  it('links to the landing page to sign in with another account on 403', async () => {
    installApi((url) => url.pathname === '/api/reports/301' ? new Response(null, { status: 403 }) : undefined)
    openReport('/family/reports/301?elderId=21&page=1')
    expect(await screen.findByRole('link', { name: 'Sign in with another account' })).toHaveAttribute('href', '/')
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })

  it('retries a connection failure without presenting an empty report', async () => {
    let failed = true
    installApi((url) => failed && url.pathname === '/api/reports/301' ? Promise.reject(new TypeError('Network unavailable')) : undefined)
    openReport()
    await screen.findByRole('heading', { name: 'Unable to load this report' })
    expect(screen.queryByText('No report sections were recorded.')).not.toBeInTheDocument()
    failed = false
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('article')).toBeInTheDocument()
  })

  it('hides protected text while a refresh is still checking the session', async () => {
    let refreshing = false
    let finish!: (response: Response) => void
    installApi((url) => refreshing && url.pathname === '/api/auth/me'
      ? new Promise<Response>((resolve) => { finish = resolve }) : undefined)
    openReport()
    await screen.findByRole('article')
    refreshing = true
    await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh' }))
    expect(screen.getByRole('status')).toHaveTextContent('Loading your report')
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    await act(async () => finish(new Response(null, { status: 401 })))
    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
  })

  it('cancels a previous report and never restores stale text when navigating back', async () => {
    let first = true
    let finishOld!: (response: Response) => void
    let finishCurrent!: (response: Response) => void
    let oldSignal!: AbortSignal
    installApi((url, init) => {
      if (url.pathname === '/api/reports/302') {
        oldSignal = init.signal as AbortSignal
        return new Promise<Response>((resolve) => { finishOld = resolve })
      }
      if (url.pathname === '/api/reports/301') {
        if (first) { first = false; return undefined }
        return new Promise<Response>((resolve) => { finishCurrent = resolve })
      }
      return undefined
    })
    openReport()
    await screen.findByRole('article')
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Open another report' }))
    await waitFor(() => expect(finishOld).toBeDefined())
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Browser back' }))
    await waitFor(() => expect(finishCurrent).toBeDefined())
    expect(oldSignal.aborted).toBe(true)
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    await act(async () => finishOld(json({ ...report, id: 302 })))
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Loading your report')
    await act(async () => finishCurrent(new Response(null, { status: 403 })))
    expect(await screen.findByRole('heading', { name: 'Report access unavailable' })).toBeInTheDocument()
  })

  it.each(['/api/auth/me', '/api/reports/301'])('cancels pending %s on unmount', async (endpoint) => {
    let finish!: (response: Response) => void
    let signal!: AbortSignal
    const fetchMock = installApi((url, init) => {
      if (url.pathname !== endpoint) return undefined
      signal = init.signal as AbortSignal
      return new Promise<Response>((resolve) => { finish = resolve })
    })
    const view = openReport()
    await waitFor(() => expect(finish).toBeDefined())
    const calls = fetchMock.mock.calls.length
    view.unmount()
    expect(signal.aborted).toBe(true)
    await act(async () => finish(json(endpoint === '/api/auth/me' ? family : report)))
    expect(fetchMock).toHaveBeenCalledTimes(calls)
  })

  it.each([
    ['9223372036854775807', '/api/reports/9223372036854775807', 404, 'Report not found'],
    ['invalid%2Freport', '/api/reports/invalid%2Freport', 400, 'Invalid report link'],
  ])('preserves and encodes URL report ID %s without using numeric coercion', async (id, endpoint, status, heading) => {
    const fetchMock = installApi((url) => url.pathname === endpoint ? new Response(null, { status }) : undefined)
    openReport(`/family/reports/${id}?elderId=-1&page=2147483648&returnTo=https://example.com`)
    await screen.findByRole('heading', { name: heading })
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/auth/me', endpoint])
    expect(screen.getByRole('link', { name: 'Back to reports' })).toHaveAttribute('href', '/family/reports')
  })
})
