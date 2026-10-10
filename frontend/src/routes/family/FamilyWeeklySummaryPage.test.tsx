import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
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
const elders = [{ id: 21, fullName: 'Tan Mei' }, { id: 22, fullName: 'Lim Wei' }]
const summary = {
  reportId: 301, elderId: 21, periodStart: '2026-09-21', periodEnd: '2026-09-27',
  summaryText: 'Service completion\nTwo visits completed.\n\nObservations\n  Comfortable during the visit.\n',
  generatedBy: 'TEMPLATE', disclaimer: 'This summary records care observations and services; it is not a diagnosis or medical advice.',
}
const detail = {
  id: 301, elderId: 21, audience: 'FAMILY', periodStart: '2026-09-21', periodEnd: '2026-09-27',
  status: 'ARCHIVED', dataComplete: false, missingItems: ['One visit record is missing.'], generatedBy: 'TEMPLATE',
  createdAt: '2026-09-28T00:30:00+08:00', archivedAt: null,
  sections: [{ title: 'Service completion', body: 'Two visits completed.' }], disclaimer: summary.disclaimer,
  amendments: [{ id: 71, note: 'Visit duration corrected to 45 minutes.\nOriginal report retained.', createdAt: '2026-09-27T16:30:00Z' }],
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
    if (url.pathname === '/api/elders') return Promise.resolve(json(elders))
    if (url.pathname === '/api/elders/21/weekly-summary') return Promise.resolve(json(summary))
    if (url.pathname === '/api/reports/301') return Promise.resolve(json(detail))
    throw new Error(`Unexpected API request: ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
function openSummary(path = '/family/reports/weekly?elderId=21&weekStart=2026-09-21') {
  return render(<MemoryRouter initialEntries={[path]}>
    <CurrentUrl />
    <Routes>
      <Route path="/" element={<h1>Landing</h1>} />
      <Route path="/family/*" element={<FamilyHome />} />
    </Routes>
  </MemoryRouter>)
}
function CurrentUrl() {
  const location = useLocation()
  const navigate = useNavigate()
  return <><button onClick={() => navigate(-1)}>Browser back</button><div aria-label="Current URL">{location.pathname}{location.search}</div></>
}
beforeEach(() => vi.stubGlobal('scrollTo', vi.fn()))
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  vi.useRealTimers()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('Family weekly care summary', () => {
  it('presents legacy report visits as separate rows instead of a single summary paragraph', async () => {
    installApi((url) => url.pathname === '/api/reports/301' ? json({ ...detail, sections: [
      { title: 'Service completion', body: '2 visits: 1 scheduled, 1 verified.\nMon 21 Sep 09:00 · Personal care · Mei · verified · evidence 2 of 2 verified\nWed 23 Sep 09:00 · Personal care · Mei · scheduled · no evidence' },
      { title: 'Observations', body: 'Mon 21 Sep · Mei: Comfortable after the visit.' },
    ] }) : undefined)
    openSummary()
    const article = await screen.findByRole('article', { name: 'Weekly care summary' })
    const visits = within(article).getByRole('region', { name: 'Visits' })
    expect(within(visits).getByText('verified')).toBeInTheDocument()
    expect(within(visits).getByText('scheduled')).toBeInTheDocument()
    expect(within(article).getByRole('region', { name: 'Caregiver notes' })).toBeInTheDocument()
    expect(within(article).queryByLabelText('Summary text')).not.toBeInTheDocument()
    expect(within(article).queryByRole('img')).not.toBeInTheDocument()
  })

  it('uses the source report visual sections for a new weekly report', async () => {
    installApi((url) => url.pathname === '/api/reports/301' ? json({ ...detail, sections: [
      { key: 'overview', title: 'Overview', body: 'Care plan version 3 · 6.5 h a week.\nMain caregiver: Mei.\nVisits: 2 of 3 carried out.', figures: [
        { key: 'visits', label: 'Visits carried out', value: 2, outOf: 3, unit: null },
      ] },
      { key: 'services', title: 'Services', body: 'Personal care: 2 of 3 carried out' },
    ] }) : undefined)
    openSummary()
    const article = await screen.findByRole('article', { name: 'Weekly care summary' })
    expect(within(article).getByText('2 of 3')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'This week' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Care context at report generation')).toHaveTextContent('Main caregiver: Mei · Care plan v3 · 6.5 h/week')
    expect(screen.queryByText('Visits: 2 of 3 carried out.')).not.toBeInTheDocument()
    expect(within(article).getByText('Personal care: 2 of 3 carried out')).toBeInTheDocument()
    expect(within(article).queryByLabelText('Summary text')).not.toBeInTheDocument()
    expect(within(article).getByText('Some care records are missing')).toBeInTheDocument()
  })

  it('reads the selected week and its source report together, preserving text, completeness and corrections', async () => {
    const fetchMock = installApi()
    openSummary()
    const article = await screen.findByRole('article', { name: 'Weekly care summary' })
    expect(document.title).toBe('Weekly care summary · CareLink')
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual([
      '/api/auth/me', '/api/elders', '/api/elders/21/weekly-summary?weekStart=2026-09-21', '/api/reports/301',
    ])
    for (const [, init] of fetchMock.mock.calls) {
      expect(init.credentials).toBe('include')
      expect(init.signal).toBeInstanceOf(AbortSignal)
      expect(init.method ?? 'GET').toBe('GET')
    }
    expect(screen.getByRole('combobox', { name: 'Care for' })).toHaveValue('21')
    expect(screen.getByRole('heading', { name: '21 Sep – 27 Sep 2026' })).toBeInTheDocument()
    expect(within(article).getByText('Structured template')).toBeInTheDocument()
    expect(within(article).getByText('Archived')).toBeInTheDocument()
    expect(within(article).getByRole('region', { name: 'Visits' }).querySelector('p')?.textContent).toBe(detail.sections[0].body)
    expect(within(article).getByText('One visit record is missing.')).toBeInTheDocument()
    expect(within(article).getByText('Some care records are missing')).toBeInTheDocument()
    expect(within(article).getByRole('region', { name: 'Corrections and follow-ups' }).querySelector('p')?.textContent).toBe(detail.amendments[0].note)
    expect(within(article).getByText('28 Sept 2026, 00:30')).toBeInTheDocument()
    expect(within(article).getByText(summary.disclaimer)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Download/ })).not.toBeInTheDocument()
  })

  it('opens the previous complete Singapore week from the list and preserves list and week selections through detail navigation', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-09-27T16:30:00Z'))
    installApi((url) => url.pathname === '/api/reports' ? json({ items: [detail], page: 1, size: 20, totalElements: 21 }) : undefined)
    openSummary('/family/reports?elderId=21&page=1')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('link', { name: 'Read by week' }))
    await screen.findByRole('article')
    expect(screen.getByLabelText('Choose a date')).toHaveValue('2026-09-21')
    await user.click(screen.getByRole('link', { name: 'Read full report' }))
    await screen.findByRole('article', { name: '21 Sep – 27 Sep 2026' })
    expect(screen.getByLabelText('Current URL')).toHaveTextContent('/family/reports/301?elderId=21&page=1&weekStart=2026-09-21')
    await user.click(screen.getByRole('link', { name: 'Back to weekly summary' }))
    await screen.findByRole('article', { name: 'Weekly care summary' })
    expect(screen.getByLabelText('Choose a date')).toHaveValue('2026-09-21')
    await user.click(screen.getByRole('link', { name: 'All reports' }))
    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Care for' })).toHaveValue('21')
    expect(screen.getByLabelText('Current URL')).toHaveTextContent('/family/reports?elderId=21&page=1')
  })

  it('retains an explicit list elder even when opening weekly reading before the list loads', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-09-27T16:30:00Z'))
    installApi((url) => url.pathname === '/api/reports' ? new Promise<Response>(() => {})
      : url.pathname === '/api/elders/22/weekly-summary' ? json({ ...summary, elderId: 22, reportId: 302 })
      : url.pathname === '/api/reports/302' ? json({ ...detail, elderId: 22, id: 302 }) : undefined)
    openSummary('/family/reports?elderId=22&page=1')
    const link = screen.getByRole('link', { name: 'Read by week' })
    expect(link).toHaveAttribute('href', '/family/reports/weekly?elderId=22&page=1')
    await userEvent.setup().click(link)
    await screen.findByRole('article')
    expect(screen.getByRole('combobox', { name: 'Care for' })).toHaveValue('22')
  })

  it.each([
    ['/api/elders/21/weekly-summary', 'No report for this week'],
    ['/api/reports/301', 'Unable to load care reports'],
  ])('handles a 404 from %s without substituting another report', async (endpoint, heading) => {
    const fetchMock = installApi((url) => url.pathname === endpoint ? new Response(null, { status: 404 }) : undefined)
    openSummary()
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Summary text')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([path]) => path.startsWith('/api/reports?'))).toBe(false)
    if (endpoint.includes('weekly-summary')) {
      expect(fetchMock.mock.calls.some(([path]) => path.startsWith('/api/reports/'))).toBe(false)
      expect(screen.getByRole('combobox', { name: 'Care for' })).toHaveValue('21')
    }
  })

  it('shows missing bindings separately and does not query a summary', async () => {
    const fetchMock = installApi((url) => url.pathname === '/api/elders' ? json([]) : undefined)
    openSummary('/family/reports/weekly?weekStart=2026-09-21')
    expect(await screen.findByRole('heading', { name: 'No linked elders yet' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/auth/me', '/api/elders'])
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })

  it.each([401, 403].flatMap((status) => ['/api/auth/me', '/api/elders', '/api/elders/21/weekly-summary', '/api/reports/301'].map((endpoint) => ({ status, endpoint }))))(
    'clears all protected content after $status at $endpoint on refresh', async ({ status, endpoint }) => {
      let denied = false
      installApi((url) => denied && url.pathname === endpoint ? json({ detail: 'Private access details' }, status) : undefined)
      openSummary()
      await screen.findByRole('article')
      denied = true
      await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh' }))
      expect(await screen.findByRole('heading', { name: status === 401 ? 'Landing' : 'Report access unavailable' })).toBeInTheDocument()
      expect(screen.queryByRole('article')).not.toBeInTheDocument()
      expect(screen.queryByText('Tan Mei')).not.toBeInTheDocument()
      expect(screen.queryByText('One visit record is missing.')).not.toBeInTheDocument()
      expect(screen.queryByText('Private access details')).not.toBeInTheDocument()
    },
  )

  it.each([
    ['summary', { elderId: 22 }],
    ['summary', { periodStart: '2026-09-14' }],
    ['summary', { periodEnd: '2026-09-28' }],
    ['detail', { id: 999 }],
    ['detail', { elderId: 22 }],
    ['detail', { periodStart: '2026-09-14' }],
    ['detail', { periodEnd: '2026-09-28' }],
  ])('rejects mismatched %s metadata %j before showing a combined reading', async (source, fields) => {
    installApi((url) => source === 'summary' && url.pathname.includes('weekly-summary') ? json({ ...summary, ...fields })
      : source === 'detail' && url.pathname === '/api/reports/301' ? json({ ...detail, ...fields }) : undefined)
    openSummary()
    expect(await screen.findByRole('heading', { name: 'Unable to load care reports' })).toBeInTheDocument()
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Summary text')).not.toBeInTheDocument()
  })

  it('selects Monday–Sunday weeks across years and months, then returns to the current Singapore week', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-09-27T16:30:00Z'))
    const fetchMock = installApi((url) => url.pathname.includes('weekly-summary') ? new Response(null, { status: 404 }) : undefined)
    openSummary()
    await screen.findByRole('heading', { name: 'No report for this week' })
    const user = userEvent.setup()
    fireEvent.change(screen.getByLabelText('Choose a date'), { target: { value: '2026-01-01' } })
    await screen.findByRole('heading', { name: '29 Dec 2025 – 4 Jan 2026' })
    await screen.findByRole('heading', { name: 'No report for this week' })
    expect(screen.getByLabelText('Choose a date')).toHaveValue('2025-12-29')
    await user.click(screen.getByRole('button', { name: 'Previous week' }))
    await screen.findByRole('heading', { name: '22 Dec – 28 Dec 2025' })
    await screen.findByRole('heading', { name: 'No report for this week' })
    await user.click(screen.getByRole('button', { name: 'Next week' }))
    await screen.findByRole('heading', { name: '29 Dec 2025 – 4 Jan 2026' })
    await screen.findByRole('heading', { name: 'No report for this week' })
    await user.click(screen.getByRole('button', { name: 'This week' }))
    await screen.findByRole('heading', { name: '28 Sep – 4 Oct 2026' })
    await screen.findByRole('heading', { name: 'No report for this week' })
    expect(fetchMock.mock.calls.filter(([path]) => path.includes('weekly-summary')).map(([path]) => new URL(path, 'http://localhost').searchParams.get('weekStart')))
      .toEqual(['2026-09-21', '2025-12-29', '2025-12-22', '2025-12-29', '2026-09-28'])
  })

  it.each([
    ['1000-01-06', 'Previous week'],
    ['9999-12-26', 'Next week'],
  ])('keeps date %s inside the supported full-week bounds', async (date, disabledButton) => {
    installApi((url) => url.pathname.includes('weekly-summary') ? new Response(null, { status: 404 }) : undefined)
    openSummary(`/family/reports/weekly?elderId=21&weekStart=${date}`)
    await screen.findByRole('heading', { name: 'No report for this week' })
    expect(screen.getByRole('button', { name: disabledButton })).toBeDisabled()
  })

  it.each(['', '2026-02-30', '0999-12-31', '9999-12-31'])('ignores unsupported picker date %s without starting another request', async (date) => {
    const fetchMock = installApi()
    openSummary()
    await screen.findByRole('article')
    const count = fetchMock.mock.calls.length
    fireEvent.change(screen.getByLabelText('Choose a date'), { target: { value: date } })
    expect(screen.getByRole('heading', { name: '21 Sep – 27 Sep 2026' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(count)
  })

  it('normalizes a deep-linked date to Monday and ignores identity, audience and arbitrary return parameters', async () => {
    const fetchMock = installApi()
    openSummary('/family/reports/weekly?elderId=21&weekStart=2026-09-23&role=MANAGER&familyMemberId=999&audience=INTERNAL&returnTo=https://example.com')
    await screen.findByRole('article')
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual([
      '/api/auth/me', '/api/elders', '/api/elders/21/weekly-summary?weekStart=2026-09-21', '/api/reports/301',
    ])
    expect(screen.getByRole('link', { name: 'Read full report' })).toHaveAttribute('href', '/family/reports/301?elderId=21&weekStart=2026-09-21')
    expect(screen.getByRole('link', { name: 'All reports' })).toHaveAttribute('href', '/family/reports?elderId=21')
  })

  it('switches elders using the selected week and clears the originating list page', async () => {
    const fetchMock = installApi((url) => url.pathname === '/api/elders/22/weekly-summary' ? json({ ...summary, elderId: 22, reportId: 302, summaryText: 'Care for Lim Wei.' })
      : url.pathname === '/api/reports/302' ? json({ ...detail, id: 302, elderId: 22, sections: [{ title: 'Observations', body: 'Care for Lim Wei.' }], dataComplete: true, missingItems: [], amendments: [] }) : undefined)
    openSummary('/family/reports/weekly?elderId=21&page=3&weekStart=2026-09-21')
    await screen.findByRole('article')
    await userEvent.setup().selectOptions(screen.getByRole('combobox', { name: 'Care for' }), '22')
    await screen.findByText('Care for Lim Wei.')
    expect(screen.getByText('Records complete')).toBeInTheDocument()
    expect(screen.getByText('No corrections or follow-ups have been added.')).toBeInTheDocument()
    expect(screen.queryByText('One visit record is missing.')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Current URL')).toHaveTextContent('/family/reports/weekly?elderId=22&weekStart=2026-09-21')
    expect(fetchMock.mock.calls.map(([path]) => path).slice(-2)).toEqual(['/api/elders/22/weekly-summary?weekStart=2026-09-21', '/api/reports/302'])
  })

  it('pins the resolved elder when refreshing after the available elders reorder', async () => {
    let reordered = false
    const fetchMock = installApi((url) => reordered && url.pathname === '/api/elders' ? json([...elders].reverse()) : undefined)
    openSummary('/family/reports/weekly?weekStart=2026-09-21')
    await screen.findByRole('article')
    reordered = true
    await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByRole('article')
    expect(screen.getByRole('combobox', { name: 'Care for' })).toHaveValue('21')
    expect(fetchMock.mock.calls.filter(([path]) => path.includes('weekly-summary')).map(([path]) => path)).toEqual([
      '/api/elders/21/weekly-summary?weekStart=2026-09-21', '/api/elders/21/weekly-summary?weekStart=2026-09-21',
    ])
  })

  it.each(['manager', 'unreadable'])('rejects %s access before querying summaries', async (mode) => {
    const fetchMock = installApi((url) => mode === 'manager' && url.pathname === '/api/auth/me' ? json({ ...family, roles: ['MANAGER'] }) : undefined)
    openSummary(`/family/reports/weekly?elderId=${mode === 'unreadable' ? 999 : 21}&weekStart=2026-09-21`)
    await screen.findByRole('heading', { name: 'Report access unavailable' })
    expect(fetchMock.mock.calls.some(([path]) => path.includes('weekly-summary'))).toBe(false)
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })

  it.each(['network', 'server', 'audit'].flatMap((failure) => ['/api/elders/21/weekly-summary', '/api/reports/301'].map((endpoint) => ({ failure, endpoint }))))(
    'retries $failure at $endpoint without presenting an empty week or partial content', async ({ failure, endpoint }) => {
      let failed = true
      installApi((url) => {
        if (!failed || url.pathname !== endpoint) return undefined
        return failure === 'network' ? Promise.reject(new TypeError('Network unavailable')) : json({ detail: 'Private server details' }, failure === 'audit' ? 503 : 500)
      })
      openSummary()
      await screen.findByRole('heading', { name: 'Unable to load care reports' })
      expect(screen.queryByRole('article')).not.toBeInTheDocument()
      expect(screen.queryByText('Private server details')).not.toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'No report for this week' })).not.toBeInTheDocument()
      failed = false
      await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))
      expect(await screen.findByRole('article')).toBeInTheDocument()
    },
  )

  it('preserves literal saved chapter text while newly added corrections stay separate on refresh', async () => {
    const text = '<img src=x onerror="alert(1)">\n\n  **Original observation**\n'
    let corrected = false
    installApi((url) => url.pathname.includes('weekly-summary') ? json({ ...summary, summaryText: text })
      : url.pathname === '/api/reports/301' ? json({ ...detail, sections: [{ title: 'Observations', body: text }], amendments: corrected ? [...detail.amendments, { id: 72, note: 'Additional correction.', createdAt: '2026-09-29T00:00:00+08:00' }] : detail.amendments }) : undefined)
    openSummary()
    await screen.findByRole('article')
    corrected = true
    await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByText('Additional correction.')
    expect(screen.getByRole('region', { name: 'Caregiver notes' }).querySelector('p')?.textContent).toBe(text)
    expect(screen.getByRole('article').querySelector('img')).toBeNull()
    expect(screen.getByRole('article').querySelector('strong')).toBeNull()
    expect(Array.from(screen.getByRole('region', { name: 'Corrections and follow-ups' }).querySelectorAll('p')).map((node) => node.textContent))
      .toEqual(['Visit duration corrected to 45 minutes.\nOriginal report retained.', 'Additional correction.'])
  })

  it('withholds the summary until its source detail finishes loading', async () => {
    let finish!: (response: Response) => void
    installApi((url) => url.pathname === '/api/reports/301' ? new Promise<Response>((resolve) => { finish = resolve }) : undefined)
    openSummary()
    await waitFor(() => expect(finish).toBeDefined())
    expect(screen.getByRole('status')).toHaveTextContent('Loading your weekly summary')
    expect(screen.queryByLabelText('Summary text')).not.toBeInTheDocument()
    await act(async () => finish(json(detail)))
    expect(await screen.findByRole('article')).toBeInTheDocument()
  })

  it.each(['/api/auth/me', '/api/elders', '/api/elders/21/weekly-summary', '/api/reports/301'])(
    'cancels pending %s on unmount and does not start later requests', async (endpoint) => {
      let finish!: (response: Response) => void
      let signal!: AbortSignal
      const fetchMock = installApi((url, init) => {
        if (url.pathname !== endpoint) return undefined
        signal = init.signal as AbortSignal
        return new Promise<Response>((resolve) => { finish = resolve })
      })
      const view = openSummary()
      await waitFor(() => expect(finish).toBeDefined())
      const count = fetchMock.mock.calls.length
      view.unmount()
      expect(signal.aborted).toBe(true)
      await act(async () => finish(json(endpoint === '/api/auth/me' ? family : endpoint === '/api/elders' ? elders : endpoint.includes('weekly-summary') ? summary : detail)))
      expect(fetchMock).toHaveBeenCalledTimes(count)
    },
  )

  it.each(['week', 'elder'])('cancels old %s content and never restores it after browser back', async (selection) => {
    let first = true
    let finishOld!: (response: Response) => void
    let finishCurrent!: (response: Response) => void
    let oldSignal!: AbortSignal
    const otherSummary = { ...summary, reportId: 302, ...(selection === 'elder' ? { elderId: 22 } : { periodStart: '2026-09-28', periodEnd: '2026-10-04' }) }
    installApi((url, init) => {
      if (url.pathname.includes('weekly-summary') && (url.pathname.includes('/22/') || url.searchParams.get('weekStart') === '2026-09-28')) return json(otherSummary)
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
    openSummary()
    await screen.findByRole('article')
    const user = userEvent.setup()
    if (selection === 'elder') await user.selectOptions(screen.getByRole('combobox', { name: 'Care for' }), '22')
    else await user.click(screen.getByRole('button', { name: 'Next week' }))
    await waitFor(() => expect(finishOld).toBeDefined())
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Browser back' }))
    await waitFor(() => expect(finishCurrent).toBeDefined())
    expect(oldSignal.aborted).toBe(true)
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    await act(async () => finishOld(json({ ...detail, id: 302, elderId: otherSummary.elderId, periodStart: otherSummary.periodStart, periodEnd: otherSummary.periodEnd })))
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Loading your weekly summary')
    await act(async () => finishCurrent(new Response(null, { status: 403 })))
    expect(await screen.findByRole('heading', { name: 'Report access unavailable' })).toBeInTheDocument()
  })

  it('returns to the landing page on 401 and links there to switch account on 403', async () => {
    let status = 401
    installApi((url) => url.pathname === '/api/elders/21/weekly-summary' ? new Response(null, { status }) : undefined)
    const { unmount } = openSummary('/family/reports/weekly?elderId=21&page=3&weekStart=2026-09-21')
    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Username')).not.toBeInTheDocument()
    unmount()

    status = 403
    openSummary('/family/reports/weekly?elderId=21&page=3&weekStart=2026-09-21')
    expect(await screen.findByRole('link', { name: 'Sign in with another account' })).toHaveAttribute('href', '/')
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })

  it('reloads available elders after a binding is revoked, keeping the chosen week', async () => {
    let revoked = false
    installApi((url) => revoked && url.pathname === '/api/elders' ? json([elders[1]])
      : url.pathname === '/api/elders/22/weekly-summary' ? new Response(null, { status: 404 }) : undefined)
    openSummary()
    await screen.findByRole('article')
    revoked = true
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByRole('heading', { name: 'Report access unavailable' })
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reload available elders' }))
    await screen.findByRole('heading', { name: 'No report for this week' })
    expect(screen.getByRole('combobox', { name: 'Care for' })).toHaveValue('22')
    expect(screen.getByLabelText('Choose a date')).toHaveValue('2026-09-21')
  })
})
