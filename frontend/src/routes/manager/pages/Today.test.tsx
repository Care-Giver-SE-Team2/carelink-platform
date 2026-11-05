import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import Today from './Today'
import * as authApi from '../../../features/auth/api'
import * as incidentsApi from '../../../features/incidents/api'
import * as profileApi from '../../../shared/api/profile'
import type { ElderListItem } from '../../../shared/api/profile'
import * as visitApi from '../../../shared/api/visit'
import type { VisitResponse } from '../../../shared/api/visit'

const elder = (id: number, fullName: string, sector: string): ElderListItem => ({
  id,
  fullName,
  sector,
  dateOfBirth: null,
  address: null,
  planStatus: 'none',
  planVersion: null,
  nextVisitDate: null,
  primaryCaregiverId: null,
  primaryCaregiverName: null,
  primaryCaregiverAssignedAt: null,
  hasPublishedPlan: false, hasCareRequest: true,
})

const visit = (
  id: number,
  time: string,
  elderId: number,
  caregiverId: number | null,
  serviceType: string,
  status: VisitResponse['status'],
): VisitResponse => ({
  id,
  elderId,
  caregiverId,
  serviceType,
  status,
  scheduledStart: `2026-09-27T${time}:00`,
  scheduledEnd: null,
  checkedInAt: null,
  checkedOutAt: null,
  stateDeadline: null,
  carePlanNodeId: null,
  absenceId: null,
  carePlanId: null,
  version: 0,
  createdAt: null,
  updatedAt: null,
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.useRealTimers()
})

beforeEach(() => {
  // 10:00 in Singapore. Only Date is faked, so the queries' own timers still run.
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-09-27T02:00:00Z'))
  // The queue's own total: 7 still need attention.
  vi.spyOn(incidentsApi, 'listIncidentQueue').mockImplementation(async ({ size }) => ({
    items: [],
    page: 0,
    size,
    totalElements: 7,
  }))
  vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue({ id: 1, username: 'tml', displayName: 'Tan Mei Ling', roles: ['MANAGER'] })
  vi.spyOn(profileApi, 'fetchElderList').mockResolvedValue([
    elder(1, 'Lim Ah Kow', 'S45'),
    elder(2, 'Chan Bee Choo', 'S31'),
    elder(3, 'Mohd Yusof', 'S52'),
    elder(4, 'Goh Siew Lan', 'S45'),
    elder(5, 'Tan Hock Seng', 'S31'),
  ])
  vi.spyOn(profileApi, 'fetchCaregivers').mockResolvedValue([
    { id: 10, fullName: 'Siti Rahmah', sector: 'S45', status: 'BUSY', assignable: false },
    { id: 11, fullName: 'Kamala Devi', sector: 'S52', status: 'AVAILABLE', assignable: true },
    { id: 12, fullName: 'Hafiz Rahman', sector: 'S52', status: 'BUSY', assignable: false },
  ])
  vi.spyOn(visitApi, 'fetchDayRoster').mockResolvedValue([
    visit(1, '08:00', 1, 10, 'Bathing assist', 'VERIFIED'),
    visit(2, '09:00', 2, 11, 'Vital-sign check', 'IN_PROGRESS'),
    visit(4, '09:00', 3, 12, 'Medication reminder', 'EXCEPTION'),
    visit(5, '11:00', 5, null, 'Companionship', 'SCHEDULED'),
    visit(6, '13:00', 1, 11, 'Companionship', 'SCHEDULED'),
  ])
})

function renderToday() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/manager']}>
        <Routes>
          <Route path="/manager" element={<Today />} />
          <Route path="/manager/exceptions" element={<p>exceptions tab</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

it('counts visits from the roster, and shows the same open-exceptions figure as the nav from one query', async () => {
  renderToday()
  const kpis = screen.getByLabelText('Today at a glance')
  const scheduled = within(kpis).getByText('Visits scheduled').closest('div')
  await vi.waitFor(() => expect(scheduled).toHaveTextContent('Visits scheduled5'))
  // The elder-confirmed visit counts as completed, but its badge says it was verified.
  expect(within(kpis).getByText('Completed').closest('div')).toHaveTextContent('Completed1')
  expect(screen.getByText('VERIFIED')).toBeInTheDocument()
  expect(within(kpis).getByText('Unassigned').closest('div')).toHaveTextContent('Unassigned1')
  expect(within(kpis).getByText('Open exceptions').closest('div')).toHaveTextContent('Open exceptions7')
  expect(within(kpis).queryByText('Escalated')).not.toBeInTheDocument()

  const nav = screen.getByRole('navigation', { name: 'Manager console' })
  expect(within(nav).getByRole('link', { name: /Exceptions/ })).toHaveTextContent('Exceptions7')
  const unfiltered = vi.mocked(incidentsApi.listIncidentQueue).mock.calls.filter(([query]) => !query.status)
  expect(unfiltered).toEqual([[{ page: 0, size: 1 }, expect.anything()]])
  expect(within(nav).getByRole('link', { name: /Today/ })).toHaveAttribute('aria-current', 'page')
})

it('leaves exceptions to the Exceptions tab: no queue or escalation chain on the board', async () => {
  renderToday()
  await screen.findByRole('table', { name: 'Visit roster' })
  expect(screen.queryByRole('region', { name: 'Exception queue' })).not.toBeInTheDocument()
  expect(screen.queryByRole('region', { name: /Escalation chain/ })).not.toBeInTheDocument()
  expect(screen.queryByRole('article')).not.toBeInTheDocument()
})

it('opens the Exceptions tab from the Open exceptions figure', async () => {
  renderToday()
  await userEvent.click(await screen.findByRole('link', { name: 'Open exceptions 7' }))
  expect(await screen.findByText('exceptions tab')).toBeInTheDocument()
})

it('opens the Exceptions tab from the nav count', async () => {
  renderToday()
  const nav = screen.getByRole('navigation', { name: 'Manager console' })
  await userEvent.click(within(nav).getByRole('link', { name: /Exceptions/ }))
  expect(await screen.findByText('exceptions tab')).toBeInTheDocument()
})

it('lists today’s visits by name, flagging the unassigned one and the exception', async () => {
  renderToday()
  const table = await screen.findByRole('table', { name: 'Visit roster' })
  await within(table).findByText('Tan Hock Seng')
  const rows = within(table).getAllByRole('row')
  expect(rows).toHaveLength(6)
  const unassigned = rows.find((row) => row.textContent?.includes('Tan Hock Seng'))!
  expect(within(unassigned).getByText('Unassigned')).toBeInTheDocument()
  expect(within(unassigned).getByText('NEEDS COVER')).toBeInTheDocument()
  const missed = rows.find((row) => row.textContent?.includes('Mohd Yusof'))!
  expect(within(missed).getByText('Hafiz Rahman')).toBeInTheDocument()
  expect(within(missed).getByText('EXCEPTION')).toBeInTheDocument()
  expect(screen.getByText('5 visits today')).toBeInTheDocument()
})

it('shows when each caregiver checked in, and flags an assigned visit past its start with no check-in', async () => {
  vi.mocked(visitApi.fetchDayRoster).mockResolvedValue([
    { ...visit(2, '09:00', 2, 11, 'Vital-sign check', 'IN_PROGRESS'), checkedInAt: '2026-09-27T09:12:00' },
    visit(3, '09:30', 4, 10, 'Bathing assist', 'SCHEDULED'),
    visit(6, '13:00', 1, 11, 'Companionship', 'SCHEDULED'),
  ])
  renderToday()
  const table = await screen.findByRole('table', { name: 'Visit roster' })
  await within(table).findByText('Goh Siew Lan')
  const rows = within(table).getAllByRole('row')
  const row = (elder: string) => rows.find((r) => r.textContent?.includes(elder))!

  expect(row('Chan Bee Choo')).toHaveTextContent('in 09:12 +12m')
  expect(within(row('Chan Bee Choo')).getByText('IN VISIT')).toBeInTheDocument()
  expect(within(row('Goh Siew Lan')).getByText('NOT CHECKED IN')).toBeInTheDocument()
  expect(within(row('Lim Ah Kow')).getByText('SCHEDULED')).toBeInTheDocument()
})
