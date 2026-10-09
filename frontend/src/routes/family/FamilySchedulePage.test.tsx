import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { FamilyVisit } from '../../features/schedule/types'
import FamilyHome from './index'

const family = { id: 11, username: 'family_test', displayName: 'Family Test', roles: ['FAMILY'] }
const elders = [
  { id: 21, fullName: 'Tan Mei', dateOfBirth: null, address: null, sector: null, planStatus: 'published', planVersion: 1, nextVisitDate: null },
  { id: 22, fullName: 'Lim Wei', dateOfBirth: null, address: null, sector: null, planStatus: 'none', planVersion: null, nextVisitDate: null },
]
const visit: FamilyVisit = {
  id: 101, elderId: 21, caregiverId: 31, serviceType: 'BATHING',
  scheduledStart: '2026-09-28T01:00:00Z', scheduledEnd: '2026-09-28T02:00:00Z',
  checkedInAt: null, checkedOutAt: null, status: 'SCHEDULED', asOf: '2026-09-27T16:05:00Z',
}

function json(body: unknown, status = 200, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } })
}
function visitPage(params: URLSearchParams, items = [visit], totalElements = items.length) {
  return json({ items, page: Number(params.get('page')), size: 20, totalElements })
}
function installApi(override?: (url: URL, init: RequestInit) => Response | Promise<Response> | undefined) {
  const fetchMock = vi.fn((path: string, init: RequestInit) => {
    const url = new URL(path, 'http://localhost')
    const response = override?.(url, init)
    if (response !== undefined) return Promise.resolve(response)
    if (url.pathname === '/api/auth/me') return Promise.resolve(json(family))
    if (url.pathname === '/api/elders') return Promise.resolve(json(elders))
    if (url.pathname === '/api/visits') return Promise.resolve(visitPage(url.searchParams))
    throw new Error(`Unexpected API request: ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
function openSchedule() {
  return render(
    <MemoryRouter initialEntries={['/family/schedule']}>
      <Routes>
        <Route path="/" element={<h1>Landing</h1>} />
        <Route path="/family/*" element={<FamilyHome />} />
      </Routes>
    </MemoryRouter>,
  )
}
function queries(fetchMock: ReturnType<typeof installApi>) {
  return fetchMock.mock.calls.filter(([path]) => path.startsWith('/api/visits?'))
    .map(([path]) => Object.fromEntries(new URL(path, 'http://localhost').searchParams))
}
function paginatedVisits(url: URL) {
  if (url.pathname !== '/api/visits') return undefined
  const page = Number(url.searchParams.get('page'))
  const items = page === 0 ? Array.from({ length: 20 }, (_, index) => ({ ...visit, id: 101 + index })) : [{ ...visit, id: 121 }]
  return visitPage(url.searchParams, items, 21)
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-09-27T16:05:00Z'))
  vi.stubGlobal('scrollTo', vi.fn())
})
afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('Family weekly schedule', () => {
  it('checks the session, loads visible elders, and requests their current Singapore week', async () => {
    const fetchMock = installApi((url) => url.pathname === '/api/visits'
      ? visitPage(url.searchParams, [visit, { ...visit, id: 102, serviceType: 'HOME_COMPANIONSHIP', caregiverId: null, scheduledEnd: null, status: 'CANCELLED' }]) : undefined)
    openSchedule()
    expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([path]) => path.split('?')[0])).toEqual(['/api/auth/me', '/api/elders', '/api/visits'])
    expect(queries(fetchMock)).toEqual([{ elderId: '21', dateFrom: '2026-09-28', dateTo: '2026-10-04', page: '0', size: '20' }])
    for (const [, init] of fetchMock.mock.calls) {
      expect(init.credentials).toBe('include')
      expect(init.signal).toBeInstanceOf(AbortSignal)
    }
    expect(screen.getByRole('combobox', { name: 'Care for' })).toHaveValue('21')
    expect(screen.getByText('2 visits this week')).toBeInTheDocument()
    expect(screen.getByText('Showing 1–2 of 2')).toBeInTheDocument()
    expect(screen.getByText('10:00')).toHaveAttribute('datetime', visit.scheduledEnd)
    expect(screen.getByRole('heading', { name: 'Home companionship' })).toBeInTheDocument()
    expect(screen.getByText('Cancelled')).toBeInTheDocument()
    expect(screen.getByText('Caregiver awaiting assignment')).toBeInTheDocument()
    expect(screen.getByText(/End time to be confirmed/)).toBeInTheDocument()
    expect(screen.getByText(/Schedule checked/)).toHaveTextContent('00:05')
    expect(screen.queryByRole('navigation', { name: 'Schedule pages' })).not.toBeInTheDocument()
  })

  it('explains absent bindings without requesting visits', async () => {
    const fetchMock = installApi((url) => url.pathname === '/api/elders' ? json([]) : undefined)
    openSchedule()
    expect(await screen.findByRole('heading', { name: 'No linked elders yet' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View my applications' })).toHaveAttribute('href', '/family/service-applications')
    expect(queries(fetchMock)).toEqual([])
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })

  it('rejects a manager session before requesting elders or visits and offers another account', async () => {
    const fetchMock = installApi((url) => url.pathname === '/api/auth/me' ? json({ ...family, roles: ['MANAGER'] }) : undefined)
    openSchedule()
    expect(await screen.findByRole('heading', { name: 'Schedule access unavailable' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/auth/me'])
    expect(screen.getByRole('link', { name: 'Sign in with another account' })).toHaveAttribute('href', '/')
  })

  it('uses total counts for pagination and resets to the first page when the week changes', async () => {
    const user = userEvent.setup()
    const fetchMock = installApi(paginatedVisits)
    openSchedule()
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    expect(screen.getByText('Showing 1–20 of 21')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument()
    expect(screen.getByText('Showing 21–21 of 21')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
    expect(within(screen.getByRole('list', { name: 'Scheduled visits' })).getAllByRole('listitem')).toHaveLength(1)
    expect(queries(fetchMock).at(-1)).toMatchObject({ page: '1' })
    await user.click(screen.getByRole('button', { name: 'Previous page' }))
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Next week/ }))
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    expect(queries(fetchMock).at(-1)).toMatchObject({ dateFrom: '2026-10-05', dateTo: '2026-10-11', page: '0' })
    await user.click(screen.getByRole('button', { name: 'This week' }))
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    expect(queries(fetchMock).at(-1)).toMatchObject({ dateFrom: '2026-09-28', page: '0' })
    await user.click(screen.getByRole('button', { name: /Previous week/ }))
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    expect(queries(fetchMock).at(-1)).toMatchObject({ dateFrom: '2026-09-21', dateTo: '2026-09-27', page: '0' })
  })

  it('opens the selected date’s Monday-to-Sunday week while retaining the elder and resetting pagination', async () => {
    const user = userEvent.setup()
    const fetchMock = installApi(paginatedVisits)
    openSchedule()
    await user.selectOptions(await screen.findByRole('combobox', { name: 'Care for' }), '22')
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument()

    const datePicker = screen.getByLabelText('Choose a date')
    expect(datePicker).toHaveAttribute('type', 'date')
    expect(datePicker).toHaveValue('2026-09-28')
    fireEvent.change(datePicker, { target: { value: '2026-10-15' } })

    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '12 Oct – 18 Oct 2026' })).toBeInTheDocument()
    expect(datePicker).toHaveValue('2026-10-15')
    expect(screen.getByRole('combobox', { name: 'Care for' })).toHaveValue('22')
    expect(queries(fetchMock).at(-1)).toEqual({ elderId: '22', dateFrom: '2026-10-12', dateTo: '2026-10-18', page: '0', size: '20' })
  })

  it('keeps the date picker in sync with week navigation and returns to today in Singapore', async () => {
    const user = userEvent.setup()
    const fetchMock = installApi()
    openSchedule()
    expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
    const datePicker = screen.getByLabelText('Choose a date')
    fireEvent.change(datePicker, { target: { value: '2026-10-15' } })
    expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Previous week/ }))
    expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
    expect(datePicker).toHaveValue('2026-10-08')
    expect(screen.getByRole('heading', { name: '5 Oct – 11 Oct 2026' })).toBeInTheDocument()
    expect(queries(fetchMock).at(-1)).toMatchObject({ dateFrom: '2026-10-05', dateTo: '2026-10-11', page: '0' })

    await user.click(screen.getByRole('button', { name: /Next week/ }))
    expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
    expect(datePicker).toHaveValue('2026-10-15')
    expect(queries(fetchMock).at(-1)).toMatchObject({ dateFrom: '2026-10-12', dateTo: '2026-10-18', page: '0' })

    await user.click(screen.getByRole('button', { name: 'This week' }))
    expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
    expect(datePicker).toHaveValue('2026-09-28')
    expect(screen.getByRole('heading', { name: '28 Sept – 4 Oct 2026' })).toBeInTheDocument()
    expect(queries(fetchMock).at(-1)).toMatchObject({ dateFrom: '2026-09-28', dateTo: '2026-10-04', page: '0' })
  })

  it('keeps the selected week when the date picker is cleared without sending an invalid request', async () => {
    const user = userEvent.setup()
    const fetchMock = installApi()
    openSchedule()
    expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
    const datePicker = screen.getByLabelText('Choose a date')
    fireEvent.change(datePicker, { target: { value: '2026-10-15' } })
    expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
    const requestsBeforeClear = queries(fetchMock)

    await user.clear(datePicker)

    expect(datePicker).toHaveValue('2026-10-15')
    expect(screen.getByRole('heading', { name: '12 Oct – 18 Oct 2026' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
    expect(queries(fetchMock)).toEqual(requestsBeforeClear)
  })

  it.each(['9999-12-31', '9999-12-27', '1000-01-01', '0999-12-31', '2026-02-30', '10000-01-01'])(
    'keeps the displayed week when a date is invalid or outside the supported calendar: %s', async (date) => {
      const fetchMock = installApi()
      openSchedule()
      expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
      const requestsBeforeChange = queries(fetchMock)
      const datePicker = screen.getByLabelText('Choose a date')

      fireEvent.change(datePicker, { target: { value: date } })

      expect(datePicker).toHaveValue('2026-09-28')
      expect(screen.getByRole('heading', { name: '28 Sept – 4 Oct 2026' })).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
      expect(queries(fetchMock)).toEqual(requestsBeforeChange)
    },
  )

  it.each([
    {
      date: '1000-01-12', from: '1000-01-06', to: '1000-01-12',
      blocked: /Previous week/, available: /Next week/,
      nextDate: '1000-01-19', nextFrom: '1000-01-13', nextTo: '1000-01-19',
    },
    {
      date: '9999-12-20', from: '9999-12-20', to: '9999-12-26',
      blocked: /Next week/, available: /Previous week/,
      nextDate: '9999-12-13', nextFrom: '9999-12-13', nextTo: '9999-12-19',
    },
  ])('allows the complete boundary week containing $date and prevents navigation outside it', async ({ date, from, to, blocked, available, nextDate, nextFrom, nextTo }) => {
    const user = userEvent.setup()
    const fetchMock = installApi()
    openSchedule()
    expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
    const datePicker = screen.getByLabelText('Choose a date')

    fireEvent.change(datePicker, { target: { value: date } })

    expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
    expect(datePicker).toHaveValue(date)
    expect(queries(fetchMock).at(-1)).toMatchObject({ dateFrom: from, dateTo: to, page: '0' })
    const blockedNavigation = screen.getByRole('button', { name: blocked })
    expect(blockedNavigation).toBeDisabled()
    const requestsAtBoundary = queries(fetchMock)
    await user.click(blockedNavigation)
    expect(datePicker).toHaveValue(date)
    expect(queries(fetchMock)).toEqual(requestsAtBoundary)

    await user.click(screen.getByRole('button', { name: available }))
    expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
    expect(datePicker).toHaveValue(nextDate)
    expect(queries(fetchMock).at(-1)).toMatchObject({ dateFrom: nextFrom, dateTo: nextTo })
  })

  it('resets the page when selecting another available elder', async () => {
    const user = userEvent.setup()
    const fetchMock = installApi(paginatedVisits)
    openSchedule()
    await user.click(await screen.findByRole('button', { name: 'Next page' }))
    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument()
    await user.selectOptions(screen.getByRole('combobox', { name: 'Care for' }), '22')
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Care for' })).toHaveValue('22')
    expect(queries(fetchMock).at(-1)).toMatchObject({ elderId: '22', page: '0', dateFrom: '2026-09-28' })
  })

  it('distinguishes a successfully loaded empty week from a failed request', async () => {
    const user = userEvent.setup()
    let failed = false
    installApi((url) => {
      if (url.pathname !== '/api/visits') return undefined
      return failed ? new Response(null, { status: 500 }) : visitPage(url.searchParams, [])
    })
    openSchedule()
    expect(await screen.findByRole('heading', { name: 'No visits this week' })).toBeInTheDocument()
    expect(screen.getByText('0 visits this week')).toBeInTheDocument()
    expect(screen.queryByText(/Schedule checked/)).not.toBeInTheDocument()
    failed = true
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByRole('heading', { name: 'Unable to load this schedule' })).toBeInTheDocument()
    expect(screen.queryByText('0 visits this week')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'No visits this week' })).not.toBeInTheDocument()
    failed = false
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'No visits this week' })).toBeInTheDocument()
  })

  it('returns to the first page if the schedule shrinks while paging', async () => {
    const user = userEvent.setup()
    const fetchMock = installApi((url) => {
      if (url.pathname !== '/api/visits') return undefined
      return url.searchParams.get('page') === '0' ? paginatedVisits(url) : visitPage(url.searchParams, [], 1)
    })
    openSchedule()
    await user.click(await screen.findByRole('button', { name: 'Next page' }))
    expect(await screen.findByRole('heading', { name: 'No visits on this page' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Back to first page' }))
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    expect(queries(fetchMock).at(-1)).toMatchObject({ page: '0' })
  })

  it.each([401, 403])('clears names, visit cards and snapshot when refreshing returns %s', async (status) => {
    const user = userEvent.setup()
    let accessLost = false
    installApi((url) => url.pathname === '/api/visits' && accessLost ? new Response(null, { status }) : undefined)
    openSchedule()
    expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
    expect(screen.getByText(/Schedule checked/)).toBeInTheDocument()
    accessLost = true
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByRole('heading', { name: status === 401 ? 'Landing' : 'Schedule access unavailable' })).toBeInTheDocument()
    expect(screen.queryByText('Tan Mei')).not.toBeInTheDocument()
    expect(screen.queryByText('Lim Wei')).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Scheduled visits' })).not.toBeInTheDocument()
    expect(screen.queryByText(/Schedule checked/)).not.toBeInTheDocument()
  })

  it('reloads available elders after a binding is revoked without querying the old elder', async () => {
    const user = userEvent.setup()
    let revoked = false
    const fetchMock = installApi((url) => url.pathname === '/api/elders' && revoked ? json([elders[1]]) : undefined)
    openSchedule()
    expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
    revoked = true
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByRole('heading', { name: 'Schedule access unavailable' })).toBeInTheDocument()
    expect(queries(fetchMock)).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Reload available elders' }))
    expect(await screen.findByRole('combobox', { name: 'Care for' })).toHaveValue('22')
    expect(queries(fetchMock).at(-1)).toMatchObject({ elderId: '22', page: '0' })
    expect(screen.queryByText('Tan Mei')).not.toBeInTheDocument()
  })

  it('returns to the landing page without requesting elders or visits when there is no session', async () => {
    const fetchMock = installApi((url) => url.pathname === '/api/auth/me' ? new Response(null, { status: 401 }) : undefined)
    openSchedule()
    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/auth/me'])
    expect(screen.queryByLabelText('Username')).not.toBeInTheDocument()
  })

  it('shows a readable validation error and recovers after a network failure', async () => {
    const user = userEvent.setup()
    let attempt = 0
    installApi((url) => {
      if (url.pathname !== '/api/visits') return undefined
      attempt += 1
      if (attempt === 1) return json({ detail: 'Both date boundaries are required.' }, 400, 'application/problem+json')
      if (attempt === 2) return Promise.reject(new TypeError('Failed to fetch'))
      return undefined
    })
    openSchedule()
    expect(await screen.findByText('Both date boundaries are required.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Unable to load this schedule' })).toBeInTheDocument()
    expect(screen.queryByText('Both date boundaries are required.')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Bathing assistance' })).toBeInTheDocument()
  })

  it('ignores a late response from a week that the family has left', async () => {
    const user = userEvent.setup()
    let resolveOld!: (response: Response) => void
    let oldSignal: AbortSignal | null | undefined
    installApi((url, init) => {
      if (url.pathname !== '/api/visits') return undefined
      if (url.searchParams.get('dateFrom') === '2026-09-28') {
        oldSignal = init.signal
        return new Promise<Response>((resolve) => { resolveOld = resolve })
      }
      return visitPage(url.searchParams, [{ ...visit, serviceType: 'NEW_WEEK_CARE' }])
    })
    openSchedule()
    await waitFor(() => expect(oldSignal).toBeDefined())
    await user.click(screen.getByRole('button', { name: /Next week/ }))
    expect(await screen.findByRole('heading', { name: 'New week care' })).toBeInTheDocument()
    expect(oldSignal?.aborted).toBe(true)
    await act(async () => { resolveOld(json({ items: [{ ...visit, serviceType: 'OUTDATED_VISIT' }], page: 0, size: 20, totalElements: 1 })) })
    expect(screen.getByRole('heading', { name: 'New week care' })).toBeInTheDocument()
    expect(screen.queryByText('Outdated visit')).not.toBeInTheDocument()
  })

  it('cancels an outstanding request when leaving the schedule', async () => {
    let finishRequest!: (response: Response) => void
    let signal: AbortSignal | null | undefined
    const fetchMock = installApi((url, init) => {
      if (url.pathname !== '/api/visits') return undefined
      signal = init.signal
      return new Promise<Response>((resolve) => { finishRequest = resolve })
    })
    const { unmount } = openSchedule()
    await waitFor(() => expect(signal).toBeDefined())
    unmount()
    expect(signal?.aborted).toBe(true)
    await act(async () => { finishRequest(json({ items: [visit], page: 0, size: 20, totalElements: 1 })) })
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(screen.queryByText('Bathing assistance')).not.toBeInTheDocument()
  })
})
