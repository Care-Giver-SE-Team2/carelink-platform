import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { FamilyVisit } from '../../features/schedule/types'
import FamilyHome from './index'

const family = { id: 11, username: 'family_test', displayName: 'Family Test', roles: ['FAMILY'] }
const elders = [
  { id: 21, fullName: 'Tan Mei', dateOfBirth: '1948-03-02', address: null, sector: 'S31', planStatus: 'published', planVersion: 1, nextVisitDate: null },
  { id: 22, fullName: 'Lim Wei', dateOfBirth: null, address: null, sector: null, planStatus: 'none', planVersion: null, nextVisitDate: null },
]
// Monday 28 Sep 2026, Singapore time; "now" is 10:30 that morning.
const visit: FamilyVisit = {
  id: 101, elderId: 21, caregiverId: 31, serviceType: 'BATHING',
  scheduledStart: '2026-09-28T02:00:00Z', scheduledEnd: '2026-09-28T03:00:00Z',
  checkedInAt: '2026-09-28T02:04:00Z', checkedOutAt: null, status: 'IN_PROGRESS', asOf: '2026-09-28T02:30:00Z',
}
const closed: FamilyVisit = { ...visit, id: 100, scheduledStart: '2026-09-28T00:00:00Z', scheduledEnd: '2026-09-28T01:00:00Z', status: 'VERIFIED' }
const later: FamilyVisit = { ...visit, id: 102, serviceType: 'VITALS', scheduledStart: '2026-09-29T01:00:00Z', scheduledEnd: '2026-09-29T01:30:00Z', checkedInAt: null, status: 'SCHEDULED' }
const caregiver = { id: 31, fullName: 'Siti Rahmah', dialects: ['Malay', 'English'] }
const credentials = [
  { id: 1, caregiverId: 31, credentialTypeId: 1, credentialTypeName: 'First aid', issuingBody: null, validFrom: null, expiryDate: '2027-03-01', status: 'PUBLISHED' },
  { id: 2, caregiverId: 31, credentialTypeId: 2, credentialTypeName: 'Manual handling', issuingBody: null, validFrom: null, expiryDate: '2026-01-01', status: 'EXPIRED' },
]
const tasks = [
  { id: 1, visitId: 101, name: 'Wash', status: 'DONE', completedAt: null },
  { id: 2, visitId: 101, name: 'Dress', status: 'PENDING', completedAt: null },
]

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
function installApi(items: FamilyVisit[], override?: (url: URL) => Response | undefined) {
  const fetchMock = vi.fn((path: string) => {
    const url = new URL(path, 'http://localhost')
    const response = override?.(url)
    if (response !== undefined) return Promise.resolve(response)
    if (url.pathname === '/api/auth/me') return Promise.resolve(json(family))
    if (url.pathname === '/api/elders') return Promise.resolve(json(elders))
    if (url.pathname === '/api/visits') {
      const elderItems = items.filter((item) => String(item.elderId) === url.searchParams.get('elderId'))
      return Promise.resolve(json({ items: elderItems, page: 0, size: 20, totalElements: elderItems.length }))
    }
    if (url.pathname === '/api/caregivers/31') return Promise.resolve(json(caregiver))
    if (url.pathname === '/api/caregivers/31/credentials') return Promise.resolve(json(credentials))
    if (url.pathname === '/api/visits/101/tasks') return Promise.resolve(json(tasks))
    throw new Error(`Unexpected API request: ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
function Location() {
  const { pathname, search } = useLocation()
  return <output aria-label="Current URL">{pathname + search}</output>
}
function openFamily(path = '/family') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/" element={<h1>Landing</h1>} />
          <Route path="/family/*" element={<><FamilyHome /><Location /></>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-09-28T02:30:00Z'))
  vi.stubGlobal('scrollTo', vi.fn())
})
afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('Family home', () => {
  it('opens from /family with the linked elder, the visit under way and this week', async () => {
    const fetchMock = installApi([closed, visit, later, { ...later, id: 103, status: 'EXCEPTION' }])
    openFamily()

    expect(await screen.findByRole('heading', { level: 1, name: 'Tan Mei' })).toBeInTheDocument()
    expect(screen.getByLabelText('Current URL')).toHaveTextContent('/family/home')
    expect(screen.getByText('78 · S31')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Home' })).toHaveAttribute('aria-current', 'page')

    const live = screen.getByRole('article', { name: 'Bathing assistance' })
    expect(screen.getByRole('heading', { name: 'Happening now' })).toBeInTheDocument()
    expect(within(live).getByText('In progress')).toBeInTheDocument()
    expect(within(live).getByText('10:00 – 11:00')).toBeInTheDocument()
    expect(await within(live).findByText('Siti Rahmah')).toBeInTheDocument()
    expect(within(live).getByText('4 visits this week')).toBeInTheDocument()
    expect(await within(live).findByText('First aid · valid')).toBeInTheDocument()
    expect(within(live).queryByText(/Manual handling/)).not.toBeInTheDocument()
    expect(within(live).getByText('Malay · English')).toBeInTheDocument()
    expect(within(live).getByText('10:04')).toBeInTheDocument()
    expect(await within(live).findByText('1 of 2')).toBeInTheDocument()
    expect(within(live).getByRole('link', { name: /View visit progress/ })).toHaveAttribute('href', '/family/visits/101')

    const week = screen.getByRole('heading', { name: 'This week' }).parentElement!
    expect(within(week).getByText('Visits completed').nextSibling).toHaveTextContent('1 of 4')
    expect(within(week).getByText('Exceptions raised').nextSibling).toHaveTextContent('1')
    expect(within(week).getByRole('link', { name: 'read →' })).toHaveAttribute('href', '/family/reports/weekly?elderId=21')
    expect(new URL(fetchMock.mock.calls.find(([path]) => path.startsWith('/api/visits?'))![0], 'http://localhost').searchParams.get('dateFrom'))
      .toBe('2026-09-28')
  })

  it('shows the next visit when none is under way, without task counts', async () => {
    const fetchMock = installApi([closed, later])
    openFamily('/family/home')

    expect(await screen.findByRole('heading', { name: 'Next visit' })).toBeInTheDocument()
    const next = screen.getByRole('article', { name: 'Vital signs monitoring' })
    expect(within(next).getByText('Tue 09:00 – 09:30')).toBeInTheDocument()
    expect(within(next).queryByText('arrived')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([path]) => path.endsWith('/tasks'))).toBe(false)
  })

  it('says so when no more visits remain this week', async () => {
    installApi([closed])
    openFamily('/family/home')
    expect(await screen.findByText('No more visits this week.')).toBeInTheDocument()
  })

  it('switches between linked elders', async () => {
    const user = userEvent.setup()
    installApi([visit, { ...later, elderId: 22, caregiverId: null }])
    openFamily('/family/home')

    await screen.findByRole('heading', { level: 1, name: 'Tan Mei' })
    await user.selectOptions(screen.getByRole('combobox', { name: 'Care for' }), '22')

    expect(await screen.findByRole('heading', { level: 1, name: 'Lim Wei' })).toBeInTheDocument()
    expect(await screen.findByText('Caregiver awaiting assignment')).toBeInTheDocument()
  })

  it('prompts for an application when no elder is linked yet', async () => {
    installApi([], (url) => url.pathname === '/api/elders' ? json([]) : undefined)
    openFamily('/family/home')

    expect(await screen.findByRole('heading', { name: 'No linked elders yet' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View my applications' })).toHaveAttribute('href', '/family/service-applications')
  })

  it('returns to the landing page when the session has expired', async () => {
    installApi([], (url) => url.pathname === '/api/auth/me' ? new Response(null, { status: 401 }) : undefined)
    openFamily('/family/home')
    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
  })
})
