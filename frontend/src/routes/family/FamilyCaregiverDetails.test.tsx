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
const profile = { id: 31, fullName: 'Lim Jia Hui', dialects: ['Mandarin', 'Hokkien'] }
const qualification = {
  id: 41, caregiverId: 31, credentialTypeId: 51, credentialTypeName: 'First Aid',
  issuingBody: 'Singapore Red Cross', validFrom: '2025-01-01', expiryDate: '2027-01-01', status: 'PUBLISHED',
}
const profilePath = '/api/caregivers/31'
const qualificationsPath = '/api/caregivers/31/credentials'
const originalShowModal = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'showModal')
const originalClose = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'close')

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
function visitPage(url: URL, items = [visit], totalElements = items.length) {
  return json({ items, page: Number(url.searchParams.get('page')), size: 20, totalElements })
}
function deferred() {
  let resolve!: (response: Response) => void
  const promise = new Promise<Response>((complete) => { resolve = complete })
  return { promise, resolve }
}
function installApi(override?: (url: URL, init: RequestInit) => Response | Promise<Response> | undefined) {
  const fetchMock = vi.fn((path: string, init: RequestInit) => {
    const url = new URL(path, 'http://localhost')
    const response = override?.(url, init)
    if (response !== undefined) return Promise.resolve(response)
    if (url.pathname === '/api/auth/me') return Promise.resolve(json(family))
    if (url.pathname === '/api/elders') return Promise.resolve(json(elders))
    if (url.pathname === '/api/visits') return Promise.resolve(visitPage(url))
    if (url.pathname === profilePath) return Promise.resolve(json(profile))
    if (url.pathname === qualificationsPath) return Promise.resolve(json([qualification]))
    throw new Error(`Unexpected API request: ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
function detailRequests(fetchMock: ReturnType<typeof installApi>) {
  return fetchMock.mock.calls.filter(([path]) => path.startsWith('/api/caregivers/'))
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
async function openDetails(user: ReturnType<typeof userEvent.setup>, visitId = 101) {
  await user.click(await screen.findByRole('button', { name: `View caregiver for visit ${visitId}` }))
  return screen.getByRole('dialog', { name: 'Caregiver details' })
}
async function expectDetailsLoaded() {
  const dialog = screen.getByRole('dialog', { name: 'Caregiver details' })
  expect(await within(dialog).findByRole('heading', { name: profile.fullName })).toBeInTheDocument()
  expect(await within(dialog).findByText(qualification.credentialTypeName)).toBeInTheDocument()
  return dialog
}
function expectProtectedDataCleared() {
  expect(screen.queryByRole('dialog', { name: 'Caregiver details' })).not.toBeInTheDocument()
  expect(screen.queryByText('Tan Mei')).not.toBeInTheDocument()
  expect(screen.queryByText('Lim Wei')).not.toBeInTheDocument()
  expect(screen.queryByRole('list', { name: 'Scheduled visits' })).not.toBeInTheDocument()
  expect(screen.queryByText(/Schedule checked/)).not.toBeInTheDocument()
  expect(screen.queryByText(profile.fullName)).not.toBeInTheDocument()
  expect(screen.queryByText(qualification.credentialTypeName)).not.toBeInTheDocument()
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-09-27T16:05:00Z'))
  vi.stubGlobal('scrollTo', vi.fn())
  // jsdom does not implement the native dialog methods in this test environment.
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
    configurable: true, value: vi.fn(function (this: HTMLDialogElement) { this.setAttribute('open', '') }),
  })
  Object.defineProperty(HTMLDialogElement.prototype, 'close', {
    configurable: true, value: vi.fn(function (this: HTMLDialogElement) {
      this.removeAttribute('open')
      this.dispatchEvent(new Event('close'))
    }),
  })
})
afterEach(() => {
  cleanup()
  if (originalShowModal) Object.defineProperty(HTMLDialogElement.prototype, 'showModal', originalShowModal)
  else Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal')
  if (originalClose) Object.defineProperty(HTMLDialogElement.prototype, 'close', originalClose)
  else Reflect.deleteProperty(HTMLDialogElement.prototype, 'close')
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('Family caregiver details', () => {
  it('loads details only on demand, including repeated visits with the same caregiver, and omits the action for unassigned visits', async () => {
    const user = userEvent.setup()
    const fetchMock = installApi((url) => url.pathname === '/api/visits'
      ? visitPage(url, [visit, { ...visit, id: 102 }, { ...visit, id: 103, caregiverId: null }]) : undefined)
    openSchedule()
    expect(await screen.findByRole('button', { name: 'View caregiver for visit 101' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'View caregiver for visit 102' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'View caregiver for visit 103' })).not.toBeInTheDocument()
    expect(screen.getByText('Caregiver awaiting assignment')).toBeInTheDocument()
    expect(detailRequests(fetchMock)).toEqual([])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await openDetails(user)
    const dialog = await expectDetailsLoaded()
    expect(dialog).toHaveAttribute('open')
    expect(within(dialog).getByRole('heading', { name: 'Languages' })).toBeInTheDocument()
    expect(within(dialog).getByText('Mandarin, Hokkien')).toBeInTheDocument()
    expect(within(dialog).getByRole('heading', { name: 'Qualifications' })).toBeInTheDocument()
    expect(within(dialog).getByText('Singapore Red Cross')).toBeInTheDocument()
    expect(detailRequests(fetchMock).map(([path]) => path)).toEqual([profilePath, qualificationsPath])
    for (const [, init] of detailRequests(fetchMock)) {
      expect(init.credentials).toBe('include')
      expect(init.signal).toBeInstanceOf(AbortSignal)
    }

    await user.click(within(dialog).getByRole('button', { name: 'Close caregiver details' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await openDetails(user, 102)
    await expectDetailsLoaded()
    expect(detailRequests(fetchMock).map(([path]) => path)).toEqual([
      profilePath, qualificationsPath, profilePath, qualificationsPath,
    ])
  })

  it.each(['profile', 'qualifications'] as const)('requests both sections in parallel and displays %s without waiting for the other section', async (firstSection) => {
    const user = userEvent.setup()
    const pendingProfile = deferred()
    const pendingQualifications = deferred()
    const fetchMock = installApi((url) => {
      if (url.pathname === profilePath) return pendingProfile.promise
      if (url.pathname === qualificationsPath) return pendingQualifications.promise
      return undefined
    })
    openSchedule()
    const dialog = await openDetails(user)
    expect(detailRequests(fetchMock).map(([path]) => path)).toEqual([profilePath, qualificationsPath])
    expect(within(dialog).getByText('Loading caregiver profile…')).toBeInTheDocument()
    expect(within(dialog).getByText('Loading qualifications…')).toBeInTheDocument()

    if (firstSection === 'profile') {
      await act(async () => { pendingProfile.resolve(json(profile)) })
      expect(await within(dialog).findByRole('heading', { name: profile.fullName })).toBeInTheDocument()
      expect(within(dialog).getByText('Loading qualifications…')).toBeInTheDocument()
      await act(async () => { pendingQualifications.resolve(json([qualification])) })
    } else {
      await act(async () => { pendingQualifications.resolve(json([qualification])) })
      expect(await within(dialog).findByText(qualification.credentialTypeName)).toBeInTheDocument()
      expect(within(dialog).getByText('Loading caregiver profile…')).toBeInTheDocument()
      await act(async () => { pendingProfile.resolve(json(profile)) })
    }
    await expectDetailsLoaded()
    expect(within(dialog).queryByText(/Loading caregiver profile|Loading qualifications/)).not.toBeInTheDocument()
  })

  it('distinguishes empty public qualifications and languages from failed requests', async () => {
    const user = userEvent.setup()
    installApi((url) => {
      if (url.pathname === profilePath) return json({ ...profile, dialects: [] })
      if (url.pathname === qualificationsPath) return json([])
      return undefined
    })
    openSchedule()
    const dialog = await openDetails(user)
    expect(await within(dialog).findByText('No languages listed')).toBeInTheDocument()
    expect(await within(dialog).findByText('No public qualifications')).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: /Retry/ })).not.toBeInTheDocument()
  })

  it.each([
    { label: 'current published', status: 'PUBLISHED', validFrom: '2025-01-01', expiryDate: '2027-01-01', statusLabel: 'Published', validityLabel: 'Currently valid' },
    { label: 'current expiring', status: 'EXPIRING', validFrom: '2025-01-01', expiryDate: '2026-10-01', statusLabel: 'Expiring soon', validityLabel: 'Currently valid' },
    { label: 'future published', status: 'PUBLISHED', validFrom: '2026-10-01', expiryDate: '2027-01-01', statusLabel: 'Published', validityLabel: 'Not yet valid' },
    { label: 'future expiring', status: 'EXPIRING', validFrom: '2026-10-01', expiryDate: '2026-10-15', statusLabel: 'Expiring soon', validityLabel: 'Not yet valid' },
    { label: 'expired', status: 'EXPIRED', validFrom: '2025-01-01', expiryDate: '2026-09-27', statusLabel: 'Expired', validityLabel: 'Expired' },
    { label: 'revoked', status: 'REVOKED', validFrom: '2026-10-01', expiryDate: '2027-01-01', statusLabel: 'Revoked', validityLabel: 'Revoked' },
    { label: 'permanent', status: 'PUBLISHED', validFrom: null, expiryDate: '9999-12-31', statusLabel: 'Published', validityLabel: 'Currently valid' },
  ])('displays a $label qualification with its public status and date validity', async ({ status, validFrom, expiryDate, statusLabel, validityLabel }) => {
    const user = userEvent.setup()
    installApi((url) => url.pathname === qualificationsPath
      ? json([{ ...qualification, status, validFrom, expiryDate }]) : undefined)
    openSchedule()
    await openDetails(user)
    const dialog = await expectDetailsLoaded()
    expect(within(dialog).getAllByText(statusLabel).length).toBeGreaterThan(0)
    expect(within(dialog).getAllByText(validityLabel).length).toBeGreaterThan(0)
    expect(within(dialog).getByText('Singapore Red Cross')).toBeInTheDocument()
    if (validityLabel !== 'Currently valid') {
      expect(within(dialog).queryByText('Currently valid')).not.toBeInTheDocument()
    }
    if (expiryDate === '9999-12-31') {
      expect(within(dialog).getByText('No expiry')).toBeInTheDocument()
      expect(within(dialog).getByText('Not provided')).toBeInTheDocument()
      expect(dialog).not.toHaveTextContent('9999')
    } else if (validFrom === '2025-01-01') {
      expect(within(dialog).getByText('1 Jan 2025')).toBeInTheDocument()
    }
  })

  it.each([
    { section: 'profile', path: profilePath, retry: 'Retry profile', failure: 404 },
    { section: 'profile', path: profilePath, retry: 'Retry profile', failure: 500 },
    { section: 'profile', path: profilePath, retry: 'Retry profile', failure: 'network' },
    { section: 'qualifications', path: qualificationsPath, retry: 'Retry qualifications', failure: 404 },
    { section: 'qualifications', path: qualificationsPath, retry: 'Retry qualifications', failure: 500 },
    { section: 'qualifications', path: qualificationsPath, retry: 'Retry qualifications', failure: 'network' },
  ])('retries a $failure $section failure independently and preserves the successful section', async ({ section, path, retry, failure }) => {
    const user = userEvent.setup()
    let failed = true
    const fetchMock = installApi((url) => {
      if (url.pathname !== path || !failed) return undefined
      return failure === 'network' ? Promise.reject(new TypeError('Failed to fetch')) : new Response(null, { status: Number(failure) })
    })
    openSchedule()
    const dialog = await openDetails(user)
    expect(await within(dialog).findByRole('button', { name: retry })).toBeInTheDocument()
    const retained = section === 'profile' ? qualification.credentialTypeName : profile.fullName
    expect(await within(dialog).findByText(retained)).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Tan Mei' })).toBeInTheDocument()
    expect(screen.getByText('Bathing assistance')).toBeInTheDocument()
    expect(screen.getByText(/Schedule checked/)).toBeInTheDocument()
    expect(within(dialog).queryByText('No public qualifications')).not.toBeInTheDocument()

    failed = false
    await user.click(within(dialog).getByRole('button', { name: retry }))
    await expectDetailsLoaded()
    expect(within(dialog).getByText(retained)).toBeInTheDocument()
    expect(detailRequests(fetchMock).map(([requestedPath]) => requestedPath)).toEqual([
      profilePath, qualificationsPath, path,
    ])
    expect(within(dialog).queryByRole('button', { name: retry })).not.toBeInTheDocument()
  })

  it('refreshes both sections from their endpoints', async () => {
    const user = userEvent.setup()
    let refreshed = false
    const fetchMock = installApi((url) => {
      if (!refreshed) return undefined
      if (url.pathname === profilePath) return json({ ...profile, fullName: 'Updated caregiver', dialects: ['Cantonese'] })
      if (url.pathname === qualificationsPath) return json([{ ...qualification, credentialTypeName: 'Updated First Aid' }])
      return undefined
    })
    openSchedule()
    await openDetails(user)
    const dialog = await expectDetailsLoaded()
    refreshed = true
    await user.click(within(dialog).getByRole('button', { name: 'Refresh details' }))
    expect(await within(dialog).findByRole('heading', { name: 'Updated caregiver' })).toBeInTheDocument()
    expect(await within(dialog).findByText('Updated First Aid')).toBeInTheDocument()
    expect(within(dialog).getByText('Cantonese')).toBeInTheDocument()
    expect(within(dialog).queryByText('Mandarin')).not.toBeInTheDocument()
    expect(detailRequests(fetchMock).map(([path]) => path)).toEqual([
      profilePath, qualificationsPath, profilePath, qualificationsPath,
    ])
  })

  it.each([
    { path: profilePath, status: 401 },
    { path: profilePath, status: 403 },
    { path: qualificationsPath, status: 401 },
    { path: qualificationsPath, status: 403 },
  ])('clears the whole protected schedule when $path returns $status, even if the other request completes later', async ({ path, status }) => {
    const user = userEvent.setup()
    const pendingProfile = deferred()
    const pendingQualifications = deferred()
    const fetchMock = installApi((url) => {
      if (url.pathname === profilePath) return pendingProfile.promise
      if (url.pathname === qualificationsPath) return pendingQualifications.promise
      return undefined
    })
    openSchedule()
    await openDetails(user)
    const failed = path === profilePath ? pendingProfile : pendingQualifications
    const other = path === profilePath ? pendingQualifications : pendingProfile
    await act(async () => { failed.resolve(new Response(null, { status })) })
    expect(await screen.findByRole('heading', { name: status === 401 ? 'Landing' : 'Schedule access unavailable' })).toBeInTheDocument()
    expectProtectedDataCleared()
    expect(detailRequests(fetchMock).find(([requestedPath]) => requestedPath !== path)?.[1].signal?.aborted).toBe(true)

    await act(async () => { other.resolve(json(path === profilePath ? [qualification] : profile)) })
    expectProtectedDataCleared()
    expect(screen.getByRole('heading', { name: status === 401 ? 'Landing' : 'Schedule access unavailable' })).toBeInTheDocument()
  })

  it.each([401, 403])('removes already visible caregiver details and schedule data after a qualification refresh returns %s', async (status) => {
    const user = userEvent.setup()
    let accessLost = false
    installApi((url) => accessLost && url.pathname === qualificationsPath ? new Response(null, { status }) : undefined)
    openSchedule()
    await openDetails(user)
    const dialog = await expectDetailsLoaded()
    accessLost = true
    await user.click(within(dialog).getByRole('button', { name: 'Refresh details' }))
    expect(await screen.findByRole('heading', { name: status === 401 ? 'Landing' : 'Schedule access unavailable' })).toBeInTheDocument()
    expectProtectedDataCleared()
  })

  it.each([200, 401, 403])('aborts both requests on close and fetches fresh details on reopening, ignoring late %s responses', async (lateStatus) => {
    const user = userEvent.setup()
    const pendingProfile = deferred()
    const pendingQualifications = deferred()
    let firstOpening = true
    const fetchMock = installApi((url) => {
      if (!firstOpening) return undefined
      if (url.pathname === profilePath) return pendingProfile.promise
      if (url.pathname === qualificationsPath) return pendingQualifications.promise
      return undefined
    })
    openSchedule()
    const dialog = await openDetails(user)
    const oldRequests = detailRequests(fetchMock)
    await user.click(within(dialog).getByRole('button', { name: 'Close caregiver details' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(oldRequests).toHaveLength(2)
    for (const [, init] of oldRequests) expect(init.signal?.aborted).toBe(true)

    firstOpening = false
    await openDetails(user)
    await expectDetailsLoaded()
    expect(detailRequests(fetchMock)).toHaveLength(4)
    await act(async () => {
      pendingProfile.resolve(lateStatus === 200
        ? json({ ...profile, fullName: 'Outdated caregiver' }) : new Response(null, { status: lateStatus }))
      pendingQualifications.resolve(lateStatus === 200
        ? json([{ ...qualification, credentialTypeName: 'Outdated qualification' }]) : new Response(null, { status: lateStatus }))
    })
    await expectDetailsLoaded()
    expect(screen.queryByText('Outdated caregiver')).not.toBeInTheDocument()
    expect(screen.queryByText('Outdated qualification')).not.toBeInTheDocument()
  })

  it('handles native dialog cancellation and aborts both pending requests', async () => {
    const user = userEvent.setup()
    const pendingProfile = deferred()
    const pendingQualifications = deferred()
    const fetchMock = installApi((url) => {
      if (url.pathname === profilePath) return pendingProfile.promise
      if (url.pathname === qualificationsPath) return pendingQualifications.promise
      return undefined
    })
    openSchedule()
    const dialog = await openDetails(user)
    fireEvent(dialog, new Event('cancel', { cancelable: true }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(detailRequests(fetchMock)).toHaveLength(2)
    for (const [, init] of detailRequests(fetchMock)) expect(init.signal?.aborted).toBe(true)
    await act(async () => {
      pendingProfile.resolve(json(profile))
      pendingQualifications.resolve(json([qualification]))
    })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByText(profile.fullName)).not.toBeInTheDocument()
  })

  it.each(['week', 'elder', 'page'] as const)('clears details and aborts pending work when the parent %s changes', async (change) => {
    const user = userEvent.setup()
    const pendingProfile = deferred()
    const pendingQualifications = deferred()
    const fetchMock = installApi((url) => {
      if (url.pathname === profilePath) return pendingProfile.promise
      if (url.pathname === qualificationsPath) return pendingQualifications.promise
      if (url.pathname !== '/api/visits') return undefined
      const page = Number(url.searchParams.get('page'))
      const items = page === 0
        ? Array.from({ length: 20 }, (_, index) => ({ ...visit, id: 101 + index }))
        : [{ ...visit, id: 121 }]
      return visitPage(url, items, 21)
    })
    openSchedule()
    await screen.findByRole('button', { name: 'View caregiver for visit 101' })
    const datePicker = screen.getByLabelText('Choose a date')
    const elderPicker = screen.getByRole('combobox', { name: 'Care for' })
    const nextPage = screen.getByRole('button', { name: 'Next page' })
    await openDetails(user)
    // Programmatic parent changes exercise cancellation even while the native dialog is modal.
    if (change === 'week') fireEvent.change(datePicker, { target: { value: '2026-10-05' } })
    if (change === 'elder') fireEvent.change(elderPicker, { target: { value: '22' } })
    if (change === 'page') fireEvent.click(nextPage)
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await screen.findByRole('button', { name: `View caregiver for visit ${change === 'page' ? 121 : 101}` })).toBeInTheDocument()
    expect(detailRequests(fetchMock)).toHaveLength(2)
    for (const [, init] of detailRequests(fetchMock)) expect(init.signal?.aborted).toBe(true)
    await act(async () => {
      pendingProfile.resolve(json(profile))
      pendingQualifications.resolve(json([qualification]))
    })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByText(profile.fullName)).not.toBeInTheDocument()
    expect(screen.queryByText(qualification.credentialTypeName)).not.toBeInTheDocument()
  })

  it('aborts outstanding detail requests when leaving the schedule', async () => {
    const user = userEvent.setup()
    const pendingProfile = deferred()
    const pendingQualifications = deferred()
    const fetchMock = installApi((url) => {
      if (url.pathname === profilePath) return pendingProfile.promise
      if (url.pathname === qualificationsPath) return pendingQualifications.promise
      return undefined
    })
    const { unmount } = openSchedule()
    await openDetails(user)
    unmount()
    for (const [, init] of detailRequests(fetchMock)) expect(init.signal?.aborted).toBe(true)
    await act(async () => {
      pendingProfile.resolve(json(profile))
      pendingQualifications.resolve(json([qualification]))
    })
    expect(detailRequests(fetchMock)).toHaveLength(2)
    expect(screen.queryByText(profile.fullName)).not.toBeInTheDocument()
  })
})
