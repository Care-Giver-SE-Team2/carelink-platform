import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Report } from '../../features/reports/types'
import FamilyHome from './index'

const family = { id: 11, username: 'family_test', displayName: 'Family Test', roles: ['FAMILY'] }
const elders = [
  { id: 21, fullName: 'Tan Mei', dateOfBirth: null, address: null, sector: null, planStatus: 'published', planVersion: 1, nextVisitDate: null },
  { id: 22, fullName: 'Lim Wei', dateOfBirth: null, address: null, sector: null, planStatus: 'none', planVersion: null, nextVisitDate: null },
]
const report: Report = {
  id: 301, elderId: 21, audience: 'FAMILY', periodStart: '2026-09-21', periodEnd: '2026-09-27',
  status: 'PUBLISHED', dataComplete: true, missingItems: [], generatedBy: 'TEMPLATE',
  createdAt: '2026-09-28T00:30:00+08:00', archivedAt: null,
}
function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
function reportPage(params: URLSearchParams, items = [report], totalElements = items.length) {
  return json({ items, page: Number(params.get('page')), size: 20, totalElements })
}
function installApi(override?: (url: URL, init: RequestInit) => Response | Promise<Response> | undefined) {
  const fetchMock = vi.fn((path: string, init: RequestInit) => {
    const url = new URL(path, 'http://localhost')
    const response = override?.(url, init)
    if (response !== undefined) return Promise.resolve(response)
    if (url.pathname === '/api/auth/me') return Promise.resolve(json(family))
    if (url.pathname === '/api/elders') return Promise.resolve(json(elders))
    if (url.pathname === '/api/reports') return Promise.resolve(reportPage(url.searchParams, [{ ...report, elderId: Number(url.searchParams.get('elderId')) }]))
    throw new Error(`Unexpected API request: ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
function BrowserHistory() {
  const navigate = useNavigate()
  const location = useLocation()
  return <><button onClick={() => navigate(-1)}>Browser back</button><div aria-label="Current URL">{location.pathname}{location.search}</div></>
}
function openReports(path = '/family/reports') {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={[path]}>
      <BrowserHistory />
      <Routes>
        <Route path="/" element={<h1>Landing</h1>} />
        <Route path="/family/*" element={<FamilyHome />} />
      </Routes>
    </MemoryRouter></QueryClientProvider>,
  )
}
function queries(fetchMock: ReturnType<typeof installApi>) {
  return fetchMock.mock.calls.filter(([path]) => path.startsWith('/api/reports?'))
    .map(([path]) => Object.fromEntries(new URL(path, 'http://localhost').searchParams))
}

beforeEach(() => {
  vi.stubGlobal('scrollTo', vi.fn())
})
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('Family care report list', () => {
  it('checks the session and readable elders before loading family report metadata', async () => {
    const fetchMock = installApi()
    openReports()
    expect(await screen.findByRole('heading', { name: '21 Sep – 27 Sep 2026' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Reports' })).toHaveAttribute('aria-current', 'page')
    expect(document.title).toBe('Care reports · CareLink')
    expect(fetchMock.mock.calls.map(([path]) => path.split('?')[0])).toEqual(['/api/auth/me', '/api/elders', '/api/reports'])
    expect(queries(fetchMock)).toEqual([{ elderId: '21', audience: 'FAMILY', page: '0', size: '20' }])
    for (const [, init] of fetchMock.mock.calls) {
      expect(init.credentials).toBe('include')
      expect(init.signal).toBeInstanceOf(AbortSignal)
    }
    expect(screen.getByRole('combobox', { name: 'Care for' })).toHaveValue('21')
    expect(screen.getByText('1 report')).toBeInTheDocument()
    expect(screen.getByText('Showing 1–1 of 1')).toBeInTheDocument()
    expect(screen.getByText('Published')).toBeInTheDocument()
    expect(screen.getByText('Structured template')).toBeInTheDocument()
    expect(screen.getByText('Records complete')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Read report 301' })).toHaveAttribute('href', '/family/reports/301?elderId=21')
    expect(screen.queryByRole('link', { name: /Download/ })).not.toBeInTheDocument()
  })
  it('paginates on the server and resets the page when switching elders', async () => {
    const user = userEvent.setup()
    const fetchMock = installApi((url) => {
      if (url.pathname !== '/api/reports') return undefined
      const page = Number(url.searchParams.get('page'))
      const elderId = Number(url.searchParams.get('elderId'))
      const items = page === 0 ? Array.from({ length: 20 }, (_, index) => ({ ...report, id: 301 + index, elderId })) : [{ ...report, id: 321, elderId }]
      return reportPage(url.searchParams, items, 21)
    })
    openReports()
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    expect(screen.getByText('Showing 1–20 of 21')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument()
    expect(screen.getByText('Showing 21–21 of 21')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Care reports' })).getAllByRole('listitem')).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
    expect(queries(fetchMock).at(-1)).toMatchObject({ elderId: '21', page: '1' })
    await user.click(screen.getByRole('button', { name: 'Previous page' }))
    await screen.findByText('Page 1 of 2')
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    await screen.findByText('Page 2 of 2')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Care for' }), '22')
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    expect(queries(fetchMock).at(-1)).toMatchObject({ elderId: '22', page: '0', audience: 'FAMILY', size: '20' })
  })

  it.each([
    ['unbound', '/family/reports', 'No linked elders yet'],
    ['empty', '/family/reports', 'No care reports yet'],
    ['page', '/family/reports?elderId=21&page=99', 'No reports on this page'],
  ])('distinguishes the %s empty state from a failed request', async (kind, path, heading) => {
    const fetchMock = installApi((url) => {
      if (kind === 'unbound' && url.pathname === '/api/elders') return json([])
      if (url.pathname === '/api/reports' && (kind === 'empty' || url.searchParams.get('page') === '99')) {
        return reportPage(url.searchParams, [], kind === 'page' ? 1 : 0)
      }
      return undefined
    })
    openReports(path)
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Care reports' })).not.toBeInTheDocument()
    if (kind === 'unbound') {
      expect(queries(fetchMock)).toEqual([])
      expect(screen.getByRole('link', { name: 'View my applications' })).toHaveAttribute('href', '/family/service-applications')
      expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    }
    if (kind === 'page') {
      await userEvent.setup().click(screen.getByRole('button', { name: 'Back to first page' }))
      expect(await screen.findByText('Records complete')).toBeInTheDocument()
      expect(queries(fetchMock).at(-1)).toMatchObject({ elderId: '21', page: '0' })
    }
  })

  it.each([401, 403].flatMap((code) => ['/api/auth/me', '/api/elders', '/api/reports'].map((endpoint) => ({ code, endpoint }))))(
    'removes protected content after $code from $endpoint during refresh', async ({ code, endpoint }) => {
      let denied = false
      installApi((url) => denied && url.pathname === endpoint ? new Response(null, { status: code }) : undefined)
      openReports()
      await screen.findByText('Records complete')
      denied = true
      await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh' }))
      expect(await screen.findByRole('heading', { name: code === 401 ? 'Landing' : 'Report access unavailable' })).toBeInTheDocument()
      expect(screen.queryByRole('list', { name: 'Care reports' })).not.toBeInTheDocument()
      expect(screen.queryByRole('combobox', { name: 'Care for' })).not.toBeInTheDocument()
      expect(screen.queryByText('Tan Mei')).not.toBeInTheDocument()
      expect(screen.queryByText('21 Sep – 27 Sep 2026')).not.toBeInTheDocument()
    },
  )

  it('keeps the currently selected elder when a refresh reorders available elders', async () => {
    let reordered = false
    const fetchMock = installApi((url) => reordered && url.pathname === '/api/elders' ? json([...elders].reverse()) : undefined)
    openReports()
    await screen.findByText('Records complete')
    reordered = true
    await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByText('Records complete')
    expect(screen.getByRole('combobox', { name: 'Care for' })).toHaveValue('21')
    expect(queries(fetchMock).at(-1)).toMatchObject({ elderId: '21' })
  })

  it('restores the elder and page from the URL and retains them on refresh', async () => {
    const fetchMock = installApi((url) => url.pathname === '/api/reports'
      ? reportPage(url.searchParams, [{ ...report, elderId: 22, id: 321 }], 21) : undefined)
    openReports('/family/reports?elderId=22&page=1')
    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Care for' })).toHaveValue('22')
    expect(queries(fetchMock)).toEqual([{ elderId: '22', page: '1', size: '20', audience: 'FAMILY' }])
    await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByText('Page 2 of 2')
    expect(queries(fetchMock).at(-1)).toMatchObject({ elderId: '22', page: '1' })
    expect(screen.getByLabelText('Current URL')).toHaveTextContent('/family/reports?elderId=22&page=1')
  })

  it.each(['-1', '1.5', '2147483648', 'abc'])(
    'uses a safe first page for invalid page %s and ignores caller-supplied identity and audience', async (page) => {
      const fetchMock = installApi()
      openReports(`/family/reports?page=${page}&elderId=invalid&audience=INTERNAL&familyMemberId=999&size=200&role=MANAGER`)
      await screen.findByText('Records complete')
      expect(queries(fetchMock)).toEqual([{ elderId: '21', page: '0', size: '20', audience: 'FAMILY' }])
    },
  )

  it('shows archived and incomplete metadata as plain text while preserving server order', async () => {
    const note = '<img src=x onerror="alert(1)"> Follow-up still pending.'
    installApi((url) => url.pathname === '/api/reports' ? reportPage(url.searchParams, [
      { ...report, id: 302, status: 'ARCHIVED', generatedBy: 'MODEL', periodStart: '2025-12-29', periodEnd: '2026-01-04', dataComplete: false, missingItems: [note] },
      report,
    ]) : undefined)
    openReports()
    const list = await screen.findByRole('list', { name: 'Care reports' })
    expect(within(list).getAllByRole('heading').map((heading) => heading.textContent)).toEqual(['29 Dec 2025 – 4 Jan 2026', '21 Sep – 27 Sep 2026'])
    expect(screen.getByText('Archived')).toBeInTheDocument()
    expect(screen.getByText('Language-model summary')).toBeInTheDocument()
    expect(screen.getByText('Some care records are missing')).toBeInTheDocument()
    expect(screen.getByText(note)).toBeInTheDocument()
    expect(list.querySelector('img')).toBeNull()
    expect(screen.getByText('Records complete')).toBeInTheDocument()
    expect(screen.queryByText(/null|Invalid Date/)).not.toBeInTheDocument()
  })

  it('rejects a manager session before querying protected family resources', async () => {
    const fetchMock = installApi((url) => url.pathname === '/api/auth/me' ? json({ ...family, roles: ['MANAGER'] }) : undefined)
    openReports()
    await screen.findByRole('heading', { name: 'Report access unavailable' })
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/auth/me'])
    expect(screen.getByRole('link', { name: 'Sign in with another account' })).toHaveAttribute('href', '/')
  })

  it('rechecks revoked bindings and reloads available elders before retrying reports', async () => {
    let revoked = false
    const fetchMock = installApi((url) => revoked && url.pathname === '/api/elders' ? json([elders[1]]) : undefined)
    openReports('/family/reports?elderId=21')
    await screen.findByText('Records complete')
    revoked = true
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByRole('heading', { name: 'Report access unavailable' })
    expect(queries(fetchMock)).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Reload available elders' }))
    await screen.findByText('Records complete')
    expect(screen.getByRole('combobox', { name: 'Care for' })).toHaveValue('22')
    expect(queries(fetchMock).at(-1)).toMatchObject({ elderId: '22', page: '0' })
  })

  it('requires current elder access even for a valid elder ID in a deep link', async () => {
    const fetchMock = installApi()
    openReports('/family/reports?elderId=999&page=1')
    await screen.findByRole('heading', { name: 'Report access unavailable' })
    expect(queries(fetchMock)).toEqual([])
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })

  it('returns to the landing page without loading reports when there is no session', async () => {
    const fetchMock = installApi((url) => url.pathname === '/api/auth/me' ? new Response(null, { status: 401 }) : undefined)
    openReports('/family/reports?elderId=21&page=4')
    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/auth/me'])
    expect(screen.queryByLabelText('Username')).not.toBeInTheDocument()
  })

  it.each(['network', 'server', 'audit'])('retries a %s failure without presenting it as an empty report list', async (failure) => {
    let failed = true
    installApi((url) => {
      if (url.pathname !== '/api/reports' || !failed) return undefined
      return failure === 'network' ? Promise.reject(new TypeError('Network unavailable'))
        : json({ detail: 'Private server details' }, failure === 'audit' ? 503 : 500)
    })
    openReports()
    await screen.findByRole('heading', { name: 'Unable to load care reports' })
    expect(screen.queryByText('Private server details')).not.toBeInTheDocument()
    expect(screen.queryByText('No care reports yet')).not.toBeInTheDocument()
    failed = false
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('Records complete')).toBeInTheDocument()
  })

  it('cancels old selections and does not restore stale reports when navigating back', async () => {
    let first = true
    let finishOld!: (response: Response) => void
    let finishCurrent!: (response: Response) => void
    let oldSignal!: AbortSignal
    installApi((url, init) => {
      if (url.pathname !== '/api/reports') return undefined
      if (url.searchParams.get('elderId') === '22') {
        oldSignal = init.signal as AbortSignal
        return new Promise<Response>((resolve) => { finishOld = resolve })
      }
      if (first) { first = false; return undefined }
      return new Promise<Response>((resolve) => { finishCurrent = resolve })
    })
    openReports('/family/reports?elderId=21')
    await screen.findByText('Records complete')
    const user = userEvent.setup()
    await user.selectOptions(screen.getByRole('combobox', { name: 'Care for' }), '22')
    await waitFor(() => expect(finishOld).toBeDefined())
    expect(screen.queryByText('Records complete')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Browser back' }))
    await waitFor(() => expect(finishCurrent).toBeDefined())
    expect(oldSignal.aborted).toBe(true)
    expect(screen.queryByText('Records complete')).not.toBeInTheDocument()
    await act(async () => finishOld(json({ items: [{ ...report, elderId: 22 }], page: 0, size: 20, totalElements: 1 })))
    expect(screen.getByRole('status')).toHaveTextContent('Loading your reports')
    expect(screen.queryByRole('list', { name: 'Care reports' })).not.toBeInTheDocument()
    await act(async () => finishCurrent(new Response(null, { status: 401 })))
    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
  })

  it.each(['/api/auth/me', '/api/elders', '/api/reports'])(
    'cancels pending %s on unmount and does not start later requests', async (endpoint) => {
      let finish!: (response: Response) => void
      let signal!: AbortSignal
      const fetchMock = installApi((url, init) => {
        if (url.pathname !== endpoint) return undefined
        signal = init.signal as AbortSignal
        return new Promise<Response>((resolve) => { finish = resolve })
      })
      const view = openReports()
      await waitFor(() => expect(finish).toBeDefined())
      const calls = fetchMock.mock.calls.length
      view.unmount()
      expect(signal.aborted).toBe(true)
      await act(async () => finish(json(endpoint === '/api/auth/me' ? family : endpoint === '/api/elders' ? elders : { items: [report], page: 0, size: 20, totalElements: 1 })))
      expect(fetchMock).toHaveBeenCalledTimes(calls)
    },
  )

  it('keeps applications, the weekly schedule and the weekly summary reachable through the tab bar', async () => {
    installApi((url) => ['/api/visits', '/api/family/service-applications'].includes(url.pathname)
      ? json({ items: [], page: 0, size: 20, totalElements: 0 }) : undefined)
    openReports()
    await screen.findByText('Records complete')
    const user = userEvent.setup()
    await user.click(screen.getByRole('link', { name: 'Schedule' }))
    expect(await screen.findByRole('heading', { name: 'No visits this week' })).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Services' }))
    expect(await screen.findByRole('heading', { name: 'No service applications yet' })).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Reports' }))
    expect(await screen.findByRole('heading', { name: 'Weekly summary' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Reports' })).toHaveAttribute('aria-current', 'page')
  })

})
