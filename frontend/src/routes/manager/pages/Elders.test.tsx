import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import Elders from './Elders'
import * as authApi from '../../../features/auth/api'
import * as carePlanApi from '../../../shared/api/careplan'
import * as profileApi from '../../../shared/api/profile'
import type { ElderListItem } from '../../../shared/api/profile'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.useRealTimers()
})

const unassigned = { primaryCaregiverId: null, primaryCaregiverName: null, primaryCaregiverAssignedAt: null }

const elders: ElderListItem[] = [
  { id: 1, fullName: 'Chan Bee Choo', dateOfBirth: '1943-01-01', address: 'Bishan St 23', sector: 'S31', planStatus: 'published', planVersion: 4, nextVisitDate: null, ...unassigned },
  { id: 2, fullName: 'Goh Bee Lian', dateOfBirth: '1938-01-01', address: 'Bishan St 11', sector: 'S31', planStatus: 'none', planVersion: null, nextVisitDate: null, ...unassigned },
  { id: 3, fullName: 'Kamala Devi Rajan', dateOfBirth: '1950-01-01', address: 'Toa Payoh Lor 4', sector: 'S34', planStatus: 'draft', planVersion: 5, nextVisitDate: null, ...unassigned },
]

beforeEach(() => {
  vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue({ id: 1, username: 'tml', displayName: 'Tan Mei Ling', roles: ['MANAGER'] })
  vi.spyOn(profileApi, 'fetchElderList').mockResolvedValue(elders)
  vi.spyOn(carePlanApi, 'fetchLatestCarePlan').mockResolvedValue(null)
})

function renderElders() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/manager/elders']}>
        <Routes>
          <Route path="/manager/elders" element={<Elders />} />
          <Route path="/manager/elders/:elderId" element={<p>care plan page</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

it('previews the selected elder in the rail, offering Create care plan when there is none', async () => {
  renderElders()
  expect(screen.getByText('Select an elder to preview their care plan.')).toBeInTheDocument()

  fireEvent.click(await screen.findByRole('option', { name: /Goh Bee Lian/ }))

  expect(screen.getByRole('option', { name: /Goh Bee Lian/ })).toHaveAttribute('aria-selected', 'true')
  expect(screen.getByText('No plan published yet.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Create care plan' })).toBeInTheDocument()
})

it('moves the selection with the arrow keys and opens the plan on Enter', async () => {
  renderElders()
  const list = await screen.findByRole('listbox', { name: 'Elders' })
  await screen.findByRole('option', { name: /Chan Bee Choo/ })

  // No-plan elders float to the top: Goh Bee Lian, then Chan Bee Choo, then Kamala Devi Rajan.
  fireEvent.keyDown(list, { key: 'ArrowDown' })
  fireEvent.keyDown(list, { key: 'ArrowDown' })
  expect(screen.getByRole('option', { name: /Chan Bee Choo/ })).toHaveAttribute('aria-selected', 'true')

  fireEvent.keyDown(list, { key: 'Enter' })
  expect(await screen.findByText('care plan page')).toBeInTheDocument()
})

it('filters by name after the search debounce', async () => {
  renderElders()
  await screen.findByRole('option', { name: /Kamala/ })
  vi.useFakeTimers()

  fireEvent.change(screen.getByRole('searchbox', { name: 'Search elders by name or ID' }), { target: { value: 'bee' } })
  expect(screen.getByRole('option', { name: /Kamala/ })).toBeInTheDocument()

  act(() => vi.advanceTimersByTime(200))
  expect(screen.queryByRole('option', { name: /Kamala/ })).not.toBeInTheDocument()
  expect(screen.getByText('2 of 3 shown · matching "bee"')).toBeInTheDocument()
})

const caregivers: profileApi.CaregiverOption[] = [
  { id: 3, fullName: 'Aisyah N.', sector: 'S31', status: 'AVAILABLE', assignable: true },
  { id: 4, fullName: 'New Hire', sector: 'S31', status: 'ONBOARDING', assignable: false },
]

it('assigns a primary caregiver through the API and shows it from the refreshed elder list', async () => {
  const forElder = vi.spyOn(profileApi, 'fetchCaregiversForElder').mockResolvedValue(caregivers)
  const assign = vi
    .spyOn(profileApi, 'assignPrimaryCaregiver')
    .mockResolvedValue({ caregiverId: 3, fullName: 'Aisyah N.', assignedAt: '2026-09-26T10:30:00' })
  renderElders()

  fireEvent.click(await screen.findByRole('option', { name: /Chan Bee Choo/ }))
  vi.mocked(profileApi.fetchElderList).mockResolvedValue([
    { ...elders[0], primaryCaregiverId: 3, primaryCaregiverName: 'Aisyah N.', primaryCaregiverAssignedAt: '2026-09-26T10:30:00' },
    ...elders.slice(1),
  ])
  fireEvent.click(screen.getByRole('button', { name: 'Assign caregiver' }))

  const picker = await screen.findByRole('listbox', { name: 'Caregivers' })
  const newHire = within(picker).getByRole('option', { name: /New Hire/ })
  expect(within(newHire).queryByRole('button', { name: 'Assign' })).not.toBeInTheDocument()
  fireEvent.click(within(within(picker).getByRole('option', { name: /Aisyah N\./ })).getByRole('button', { name: 'Assign' }))

  expect(forElder).toHaveBeenCalledWith('1')
  expect(assign).toHaveBeenCalledWith('1', 3)
  expect(await screen.findByText(/^since 26 Sept? 2026$/)).toBeInTheDocument()
  expect(screen.queryByRole('listbox', { name: 'Caregivers' })).not.toBeInTheDocument()
})

it('rules out, before any click, a caregiver whose certificates or timetable don’t fit this elder', async () => {
  vi.spyOn(profileApi, 'fetchCaregiversForElder').mockResolvedValue([
    caregivers[0],
    {
      id: 5, fullName: 'Devi Raman', sector: 'S31', status: 'AVAILABLE', assignable: false,
      refusalCode: 'CAREGIVER_TIME_CLASH',
      refusal: 'Devi Raman is already primary caregiver of Tan Ah Kow, whose Bathing on Mon 08:00–08:30 clashes with Meals on Mon 08:00–08:30',
    },
  ])
  renderElders()

  fireEvent.click(await screen.findByRole('option', { name: /Chan Bee Choo/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Assign caregiver' }))

  const picker = await screen.findByRole('listbox', { name: 'Caregivers' })
  const devi = within(picker).getByRole('option', { name: /Devi Raman/ })
  expect(within(devi).queryByRole('button', { name: 'Assign' })).not.toBeInTheDocument()
  expect(within(devi).getByText(/timetable clash/)).toBeInTheDocument()
  expect(within(devi).getByText(/already primary caregiver of Tan Ah Kow/)).toBeInTheDocument()
  expect(within(within(picker).getByRole('option', { name: /Aisyah N\./ })).getByRole('button', { name: 'Assign' }))
    .toBeInTheDocument()
})

it('removes the primary caregiver through the API', async () => {
  vi.mocked(profileApi.fetchElderList).mockResolvedValue([
    { ...elders[0], primaryCaregiverId: 3, primaryCaregiverName: 'Aisyah N.', primaryCaregiverAssignedAt: '2026-09-20T09:00:00' },
    ...elders.slice(1),
  ])
  const remove = vi.spyOn(profileApi, 'removePrimaryCaregiver').mockResolvedValue(undefined)
  renderElders()

  fireEvent.click(await screen.findByRole('option', { name: /Chan Bee Choo/ }))
  expect(screen.getByText(/^since 20 Sept? 2026$/)).toBeInTheDocument()
  vi.mocked(profileApi.fetchElderList).mockResolvedValue(elders)
  fireEvent.click(screen.getByRole('button', { name: 'Remove' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Remove caregiver' }))

  expect(remove).toHaveBeenCalledWith('1')
  expect(await screen.findByRole('button', { name: 'Assign caregiver' })).toBeInTheDocument()
})
