import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import Roster from './Roster'
import * as absencesApi from '../../../features/absences/api'
import * as authApi from '../../../features/auth/api'
import * as incidentsApi from '../../../features/incidents/api'
import * as profileApi from '../../../shared/api/profile'
import * as visitApi from '../../../shared/api/visit'
import type { VisitResponse } from '../../../shared/api/visit'

const visit = (id: number, start: string, minutes: number, caregiverId: number | null): VisitResponse => {
  const end = new Date(Date.parse(`${start}Z`) + minutes * 60_000).toISOString().slice(0, 19)
  return {
    id,
    elderId: 1,
    caregiverId,
    carePlanNodeId: 30,
    absenceId: null,
    serviceType: 'Bathing assistance',
    scheduledStart: start,
    scheduledEnd: end,
    checkedInAt: null,
    checkedOutAt: null,
    status: 'SCHEDULED',
    stateDeadline: null,
    carePlanId: 9,
    version: 0,
    createdAt: null,
    updatedAt: null,
  }
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

beforeEach(() => {
  vi.spyOn(incidentsApi, 'listIncidentQueue').mockImplementation(async ({ size }) => ({ items: [], page: 0, size, totalElements: 0 }))
  vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue({ id: 1, username: 'tml', displayName: 'Tan Mei Ling', roles: ['MANAGER'] })
  vi.spyOn(profileApi, 'fetchElderList').mockResolvedValue([
    {
      id: 1,
      fullName: 'Tan Hock Seng',
      sector: 'S31',
      dateOfBirth: null,
      address: null,
      planStatus: 'published',
      planVersion: 1,
      nextVisitDate: null,
      primaryCaregiverId: null,
      primaryCaregiverName: null,
      primaryCaregiverAssignedAt: null,
    },
  ])
  vi.spyOn(profileApi, 'fetchCaregivers').mockResolvedValue([
    { id: 5, fullName: 'Ong Wei Jie', sector: 'S31', dialects: 'Hokkien,Mandarin', status: 'AVAILABLE', assignable: true },
  ])
  vi.spyOn(absencesApi, 'listAbsences').mockResolvedValue([])
  vi.spyOn(visitApi, 'fetchDayRoster').mockImplementation(async (date) =>
    date === '2026-10-05'
      ? [visit(1, '2026-10-05T09:30:00', 30, 5), visit(2, '2026-10-05T11:00:00', 120, null)]
      : [],
  )
})

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/manager/roster" element={<Roster />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

it('lays a day out by caregiver and hour, leaving out visits nobody has', async () => {
  renderAt('/manager/roster?view=day&date=2026-10-05')

  const caregiverRow = (await screen.findByRole('rowheader', { name: /Ong Wei Jie/ })).closest('[role="row"]') as HTMLElement
  // Half an hour is narrower than an hour, so elder and service sit on a line each.
  expect(within(caregiverRow).getByTitle(/^09:30 · Tan Hock Seng/)).toHaveTextContent('Tan H.S.Bathing assistance')
  expect(screen.getByText('S31 · Hokkien, Mandarin')).toBeInTheDocument()

  expect(screen.queryByRole('rowheader', { name: /Needs cover/ })).not.toBeInTheDocument()
  expect(screen.getAllByTitle(/Tan Hock Seng/)).toHaveLength(1)
  expect(screen.getByText('Mon 5 Oct')).toBeInTheDocument()
})

it('shows the week as daily load and opens a day from a cell', async () => {
  const user = userEvent.setup()
  renderAt('/manager/roster?view=week&date=2026-10-05')

  expect(await screen.findByText('Week 41 · 5–11 Oct')).toBeInTheDocument()
  await user.click(await screen.findByRole('button', { name: /Mon 5: 1 visits, 0\.5 h\. Open in Day view/ }))

  expect(await screen.findByText('Mon 5 Oct')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'DAY' })).toHaveAttribute('aria-pressed', 'true')
})

it('steps to the next week', async () => {
  const user = userEvent.setup()
  renderAt('/manager/roster?view=week&date=2026-10-05')

  await user.click(await screen.findByRole('button', { name: 'Next week' }))

  expect(await screen.findByText('Week 42 · 12–18 Oct')).toBeInTheDocument()
  // The week shown is named in the toolbar; the header keeps the live clock, as on other screens.
  expect(screen.getByRole('banner')).not.toHaveTextContent('Roster')
  expect(screen.getByRole('banner')).not.toHaveTextContent('Week')
})

it('shows ten caregivers a page and keeps the page when switching to Week', async () => {
  const user = userEvent.setup()
  vi.mocked(profileApi.fetchCaregivers).mockResolvedValue(
    ['Aaron', 'Bala', 'Chen', 'Devi', 'Eng', 'Farah', 'Gopal', 'Hui', 'Imran', 'Jia', 'Kumar', 'Lina'].map((name, i) => ({
      id: 10 + i,
      fullName: name,
      sector: 'S31',
      dialects: null,
      status: 'AVAILABLE' as const,
      assignable: true,
    })),
  )
  renderAt('/manager/roster?view=day&date=2026-10-13')

  expect(await screen.findByText('1–10 of 12 caregivers')).toBeInTheDocument()
  expect(screen.queryByRole('rowheader', { name: /Kumar/ })).not.toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: 'Page 2' }))
  expect(await screen.findByText('11–12 of 12 caregivers')).toBeInTheDocument()
  expect(screen.getByRole('rowheader', { name: /Kumar/ })).toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: 'WEEK' }))
  expect(await screen.findByText('11–12 of 12 caregivers')).toBeInTheDocument()
  expect(screen.getByRole('rowheader', { name: /Lina/ })).toBeInTheDocument()
})

it('marks a caregiver on approved leave, linking to the absence, in Day and Week', async () => {
  const user = userEvent.setup()
  vi.mocked(absencesApi.listAbsences).mockResolvedValue([
    {
      id: 4,
      caregiverId: 5,
      caregiverName: 'Ong Wei Jie',
      type: 'SICK',
      startDate: '2026-10-05',
      endDate: '2026-10-06',
      reason: null,
      status: 'APPROVED',
      reviewedByUserId: 1,
      coverageConfirmedAt: null,
      notYetRerostered: 1,
      awaitingFamily: 0,
      uncovered: 0,
      settled: 0,
    },
  ])
  renderAt('/manager/roster?view=day&date=2026-10-05')

  const link = await screen.findByRole('link', { name: 'On leave' })
  const away = screen.getByRole('rowheader', { name: /Ong Wei Jie/ }).closest('[role="row"]') as HTMLElement
  expect(within(away).getByTitle(/^09:30 · Tan Hock Seng/)).toHaveTextContent('Bathing assistance')
  expect(link).toHaveAttribute('href', '/manager/absences/4')
  expect(absencesApi.listAbsences).toHaveBeenCalledWith('APPROVED', expect.anything())

  await user.click(screen.getByRole('button', { name: 'WEEK' }))
  expect(await screen.findByRole('button', { name: /Mon 5: on leave, 1 visits, 0\.5 h\. Open in Day view/ })).toBeInTheDocument()
  const row = screen.getByRole('rowheader', { name: /Ong Wei Jie/ }).closest('[role="row"]') as HTMLElement
  // Tue 6 has no visits, but they are still away.
  expect(within(row).getByText('On leave')).toBeInTheDocument()
})

it('places back-to-back visits on the half hour side by side, by the quarter hour', async () => {
  vi.mocked(visitApi.fetchDayRoster).mockResolvedValue([
    visit(1, '2026-10-05T08:00:00', 150, 5),
    visit(2, '2026-10-05T10:30:00', 150, 5),
  ])
  renderAt('/manager/roster?view=day&date=2026-10-05')

  const row = (await screen.findByRole('rowheader', { name: /Ong Wei Jie/ })).closest('[role="row"]') as HTMLElement
  const [first, second] = within(row).getAllByTitle(/Tan Hock Seng/).map((block) => block.parentElement as HTMLElement)
  // 08:00 is the first quarter after the label column; 10:30 is ten quarters later, where the first ends.
  expect(first.style.gridColumn).toBe('2 / span 10')
  expect(second.style.gridColumn).toBe('12 / span 10')
})

it('narrows the roster to caregivers whose name matches the search, in Day and Week', async () => {
  const user = userEvent.setup()
  vi.mocked(profileApi.fetchCaregivers).mockResolvedValue(
    ['Aaron Tan', 'Bala Krishnan', 'Chen Li'].map((name, i) => ({
      id: 10 + i,
      fullName: name,
      sector: 'S31',
      dialects: null,
      status: 'AVAILABLE' as const,
      assignable: true,
    })),
  )
  renderAt('/manager/roster?view=day&date=2026-10-13')

  await user.type(await screen.findByRole('searchbox', { name: 'Search caregivers by name' }), 'bala')
  expect(await screen.findByText('1–1 of 1 caregivers')).toBeInTheDocument()
  expect(screen.getByRole('rowheader', { name: /Bala Krishnan/ })).toBeInTheDocument()
  expect(screen.queryByRole('rowheader', { name: /Aaron Tan/ })).not.toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: 'WEEK' }))
  expect(await screen.findByRole('rowheader', { name: /Bala Krishnan/ })).toBeInTheDocument()
  expect(screen.queryByRole('rowheader', { name: /Chen Li/ })).not.toBeInTheDocument()

  await user.clear(screen.getByRole('searchbox'))
  await user.type(screen.getByRole('searchbox'), 'zzz')
  expect(await screen.findByText('No caregivers match “zzz”.')).toBeInTheDocument()
})
