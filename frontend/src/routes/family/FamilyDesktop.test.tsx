import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { FamilyVisit } from '../../features/schedule/types'
import FamilyHome from './index'

// The navigation's waiting counts are tested with FamilyLayout and on Home; here they stay at zero so
// only this page's own requests are made.
vi.mock('./components/usePendingDecisions', () => ({
  usePendingDecisions: () => ({ changes: [], spotChecks: [], requests: [], total: 0 }),
}))

const family = { id: 11, username: 'wei_ling', displayName: 'Lim Wei Ling', roles: ['FAMILY'] }
const elders = [
  { id: 21, fullName: 'Tan Mei', dateOfBirth: '1948-03-02', address: null, sector: 'S31', planStatus: 'published', planVersion: 1, nextVisitDate: null, primaryCaregiverId: 31, primaryCaregiverName: 'Siti Rahmah', primaryCaregiverAssignedAt: null },
  { id: 22, fullName: 'Lim Wei', dateOfBirth: null, address: null, sector: null, planStatus: 'none', planVersion: null, nextVisitDate: null, primaryCaregiverId: null, primaryCaregiverName: null, primaryCaregiverAssignedAt: null },
]
// Monday 28 Sep 2026, 10:30 in Singapore.
const live: FamilyVisit = {
  id: 101, elderId: 21, caregiverId: 31, serviceType: 'BATHING',
  scheduledStart: '2026-09-28T02:00:00Z', scheduledEnd: '2026-09-28T03:00:00Z',
  checkedInAt: '2026-09-28T02:04:00Z', checkedOutAt: null, status: 'IN_PROGRESS', asOf: '2026-09-28T02:30:00Z',
}
const afternoon: FamilyVisit = { ...live, id: 102, serviceType: 'VITALS', scheduledStart: '2026-09-28T06:00:00Z', scheduledEnd: '2026-09-28T06:30:00Z', checkedInAt: null, status: 'SCHEDULED' }
const tomorrow: FamilyVisit = { ...afternoon, id: 103, scheduledStart: '2026-09-29T01:00:00Z', scheduledEnd: '2026-09-29T01:30:00Z' }
const summary = { reportId: 301, elderId: 21, periodStart: '2026-09-21', periodEnd: '2026-09-27', generatedBy: 'TEMPLATE', summaryText: 'A calm week.', disclaimer: 'Not medical advice.' }
const detail = {
  id: 301, elderId: 21, periodStart: '2026-09-21', periodEnd: '2026-09-27', generatedBy: 'TEMPLATE', status: 'PUBLISHED',
  dataComplete: true, missingItems: [], sections: [], amendments: [], disclaimer: 'Not medical advice.', createdAt: '2026-09-28T01:00:00Z', archivedAt: null,
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
function installApi() {
  const fetchMock = vi.fn((path: string) => {
    const url = new URL(path, 'http://localhost')
    const elderId = url.searchParams.get('elderId')
    const responses: Record<string, () => Response> = {
      '/api/auth/me': () => json(family),
      '/api/elders': () => json(elders),
      '/api/visits': () => {
        const items = elderId === '21' ? [live, afternoon, tomorrow] : [{ ...tomorrow, elderId: 22, caregiverId: null }]
        return json({ items, page: 0, size: 20, totalElements: items.length })
      },
      '/api/caregivers/31': () => json({ id: 31, fullName: 'Siti Rahmah', dialects: ['Malay'] }),
      '/api/caregivers/31/credentials': () => json([]),
      '/api/visits/101/tasks': () => json([]),
      '/api/elders/21/weekly-summary': () => json(summary),
      '/api/reports/301': () => json(detail),
    }
    const response = responses[url.pathname]
    if (!response) throw new Error(`Unexpected API request: ${path}`)
    return Promise.resolve(response())
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
function Location() {
  const { pathname, search } = useLocation()
  return <output aria-label="Current URL">{pathname + search}</output>
}
function openFamily(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes><Route path="/family/*" element={<><FamilyHome /><Location /></>} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}
function visitQueries(fetchMock: ReturnType<typeof installApi>) {
  return fetchMock.mock.calls.filter(([path]) => path.startsWith('/api/visits?'))
    .map(([path]) => new URL(path, 'http://localhost').searchParams.get('elderId'))
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-09-28T02:30:00Z'))
  vi.stubGlobal('scrollTo', vi.fn())
  // A 1280px-wide screen: the family app's desktop layout starts at 900px.
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query === '(min-width: 900px)', media: query,
    addEventListener: vi.fn(), removeEventListener: vi.fn(),
  }))
})
afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('Family desktop layout', () => {
  it('replaces the tab bar with a side rail: sections, the elder followed, and Account at the foot', async () => {
    installApi()
    openFamily('/family/home')

    const nav = screen.getByRole('navigation', { name: 'Family pages' })
    expect(within(nav).getAllByRole('link').map((link) => link.textContent)).toEqual(['Home', 'Schedule', 'Care plan', 'Reports', 'Visit changes', 'Spot checks', 'Extra services', 'Care applications', 'Caregiver review'])
    expect(within(nav).getByRole('link', { name: 'Home' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getAllByRole('paragraph').map((heading) => heading.textContent)).toEqual(['Care', 'Needs your answer', 'Your service'])
    expect(await screen.findByRole('link', { name: /Lim Wei Ling.*Account/ })).toHaveAttribute('href', '/family/account')
    expect(screen.getByRole('link', { name: 'CareLink' })).toHaveAttribute('href', '/family/home')
    expect(screen.getByRole('combobox', { name: 'Following' })).toHaveValue('21')
    expect(screen.queryByRole('combobox', { name: 'Care for' })).not.toBeInTheDocument()
  })

  it('shows Home as two columns, with the date in the title and the rest of today listed', async () => {
    installApi()
    openFamily('/family/home')

    expect(await screen.findByRole('heading', { level: 1, name: 'Tan Mei, Monday 28 September' })).toBeInTheDocument()
    expect(screen.getByText('Home', { selector: 'p' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Happening now' })).toBeInTheDocument()
    const later = screen.getByRole('heading', { name: 'Later today' }).parentElement!
    expect(within(later).getAllByRole('listitem')).toHaveLength(1)
    expect(within(later).getByText('14:00 – 14:30')).toBeInTheDocument()
    expect(within(later).getByText('Vital signs monitoring')).toBeInTheDocument()
    expect(within(later).getByText('Siti Rahmah')).toBeInTheDocument()
  })

  it('switches the followed elder from the rail, and Schedule keeps that elder', async () => {
    const user = userEvent.setup()
    const fetchMock = installApi()
    openFamily('/family/home')

    await screen.findByRole('heading', { level: 1, name: /^Tan Mei/ })
    await user.selectOptions(screen.getByRole('combobox', { name: 'Following' }), '22')
    expect(await screen.findByRole('heading', { level: 1, name: /^Lim Wei/ })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Schedule' }))
    expect(await screen.findByText('Schedule · Lim Wei')).toBeInTheDocument()
    expect(visitQueries(fetchMock).at(-1)).toBe('22')
    expect(screen.getByRole('link', { name: 'Reports' })).toHaveAttribute('href', '/family/reports/weekly?elderId=22')
  })

  it('lays out the weekly summary beside the week and its visits, with the actions in the header', async () => {
    installApi()
    openFamily('/family/reports/weekly?elderId=21&weekStart=2026-09-21')

    expect(await screen.findByRole('article', { name: 'Weekly care summary' })).toBeInTheDocument()
    expect(screen.getByText('Reports · Tan Mei')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'All reports' })).toHaveAttribute('href', '/family/reports?elderId=21')
    expect(screen.getAllByRole('link', { name: 'Read full report' })).toHaveLength(1)
    const visits = await screen.findByRole('list', { name: 'Visits this week' })
    expect(within(visits).getAllByRole('listitem')).toHaveLength(3)
    expect(screen.getByRole('heading', { name: 'Visits · week 39' })).toBeInTheDocument()
  })

  it('reloads a report page for the elder chosen in the rail', async () => {
    const user = userEvent.setup()
    installApi()
    openFamily('/family/reports/weekly?elderId=21&weekStart=2026-09-21')

    await screen.findByRole('article', { name: 'Weekly care summary' })
    await user.selectOptions(screen.getByRole('combobox', { name: 'Following' }), '22')

    expect(screen.getByLabelText('Current URL')).toHaveTextContent('/family/reports/weekly?elderId=22&weekStart=2026-09-21')
  })
})
