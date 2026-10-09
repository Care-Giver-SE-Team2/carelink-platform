import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import ExtraServices from './ExtraServices'
import * as authApi from '../../../features/auth/api'
import * as incidentsApi from '../../../features/incidents/api'
import * as valueAddedApi from '../../../features/value-added-services/api'
import type { ManagedValueAddedServiceRequest } from '../../../features/value-added-services/types'
import * as profileApi from '../../../shared/api/profile'
import * as eldersData from '../data/elders'
import type { ElderRow } from '../data/elders'

let requests: ManagedValueAddedServiceRequest[]

function request(overrides: Partial<ManagedValueAddedServiceRequest>): ManagedValueAddedServiceRequest {
  return {
    id: 1,
    elderId: 7,
    valueAddedServiceId: 2,
    serviceName: 'Hospital escort',
    requestedSchedule: '2026-10-10T10:00:00',
    specialInstructions: 'Bring the wheelchair',
    status: 'DISPATCHED',
    decidedAt: '2026-10-08T09:00:00',
    createdAt: '2026-10-07T17:00:00',
    visitId: 40,
    visitStatus: 'SCHEDULED',
    caregiverId: null,
    needsCaregiver: true,
    ...overrides,
  }
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

beforeEach(() => {
  requests = [
    request({}),
    request({ id: 2, serviceName: 'Grocery assistance', visitId: 41, caregiverId: 9, needsCaregiver: false }),
    request({
      id: 3,
      serviceName: 'Companionship',
      status: 'PENDING_APPROVAL',
      decidedAt: null,
      visitId: null,
      visitStatus: null,
      needsCaregiver: false,
    }),
  ]
  vi.spyOn(incidentsApi, 'listIncidentQueue').mockImplementation(async ({ size }) => ({ items: [], page: 0, size, totalElements: 0 }))
  vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue({ id: 1, username: 'tml', displayName: 'Tan Mei Ling', roles: ['MANAGER'] })
  vi.spyOn(profileApi, 'fetchCredentialRegister').mockResolvedValue([])
  vi.spyOn(profileApi, 'fetchIntakeReviews').mockResolvedValue([])
  vi.spyOn(profileApi, 'fetchCaregivers').mockResolvedValue([
    { id: 9, fullName: 'Farah Aziz', sector: 'S31', status: 'AVAILABLE', assignable: true },
  ])
  vi.spyOn(eldersData, 'fetchElders').mockResolvedValue([{ id: '7', name: 'Tan Bee Choo' } as ElderRow])
  vi.spyOn(valueAddedApi, 'fetchManagedValueAddedServiceRequests').mockImplementation(async () => [...requests])
})

function renderPage(path = '/manager/extra-services') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/manager/extra-services" element={<ExtraServices />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

it('opens on the visits needing a caregiver, with names instead of ids', async () => {
  renderPage()

  const table = await screen.findByRole('table', { name: 'Extra service requests' })
  expect(within(table).getByText('Hospital escort')).toBeTruthy()
  expect(within(table).queryByText('Grocery assistance')).toBeNull()
  expect(screen.getByRole('button', { name: 'NEEDS CAREGIVER · 1' }).getAttribute('aria-pressed')).toBe('true')

  expect(await screen.findByText(/for Tan Bee Choo · Sat 10 Oct, 10:00/)).toBeTruthy()
  const panel = screen.getByRole('complementary', { name: 'Extra service detail' })
  expect(within(panel).getByText('Bring the wheelchair')).toBeTruthy()
})

it('assigns a caregiver from the ranked options and refreshes the list', async () => {
  const user = userEvent.setup()
  vi.spyOn(valueAddedApi, 'fetchCaregiverCoverOptions').mockResolvedValue([
    { caregiverId: 9, name: 'Farah Aziz', rank: 1, reason: 'Has visited before', eligible: true },
    { caregiverId: 5, name: 'Aisha Lim', rank: null, reason: 'Busy with another visit then', eligible: false },
  ])
  const assign = vi.spyOn(valueAddedApi, 'assignValueAddedServiceCaregiver').mockImplementation(async () => {
    requests = requests.map((row) => (row.id === 1 ? { ...row, caregiverId: 9, needsCaregiver: false } : row))
    return requests[0]
  })
  renderPage('/manager/extra-services?filter=all&id=1')

  await user.click(await screen.findByRole('button', { name: 'Assign caregiver' }))
  const dialog = await screen.findByRole('dialog')
  expect(await within(dialog).findByText('Busy with another visit then')).toBeTruthy()
  expect(within(dialog).getAllByRole('button', { name: 'Assign' })).toHaveLength(1)

  await user.click(within(dialog).getByRole('button', { name: 'Assign' }))

  expect(assign).toHaveBeenCalledWith(1, 9)
  expect(screen.queryByRole('dialog')).toBeNull()
  const panel = screen.getByRole('complementary', { name: 'Extra service detail' })
  expect(await within(panel).findByText('Farah Aziz')).toBeTruthy()
  expect(within(panel).queryByRole('button', { name: 'Assign caregiver' })).toBeNull()
})

it('shows why an assignment was refused', async () => {
  const user = userEvent.setup()
  vi.spyOn(valueAddedApi, 'fetchCaregiverCoverOptions').mockResolvedValue([
    { caregiverId: 9, name: 'Farah Aziz', rank: 1, reason: null, eligible: true },
  ])
  vi.spyOn(valueAddedApi, 'assignValueAddedServiceCaregiver').mockRejectedValue(
    new Error('This caregiver cannot take the visit: on leave'),
  )
  renderPage()

  await user.click(await screen.findByRole('button', { name: 'Assign caregiver' }))
  await user.click(await within(await screen.findByRole('dialog')).findByRole('button', { name: 'Assign' }))

  expect((await screen.findByRole('alert')).textContent).toContain('on leave')
})

it('cancels a request after confirming', async () => {
  const user = userEvent.setup()
  const cancel = vi.spyOn(valueAddedApi, 'cancelValueAddedServiceRequest').mockImplementation(async () => {
    requests = requests.map((row) => (row.id === 3 ? { ...row, status: 'CANCELLED' } : row))
    return requests[2]
  })
  renderPage('/manager/extra-services?filter=awaiting')

  await user.click(await screen.findByRole('button', { name: 'Cancel request' }))
  const dialog = await screen.findByRole('dialog')
  await user.click(within(dialog).getByRole('button', { name: 'Cancel request' }))

  expect(cancel).toHaveBeenCalledWith(3)
  expect(await screen.findByText('No requests are waiting for a family’s answer.')).toBeTruthy()
})

it('opens the request a manager-bell link names by its visit, with its row in the table', async () => {
  renderPage('/manager/extra-services?visit=41')

  expect(await screen.findByRole('heading', { name: 'Grocery assistance' })).toBeTruthy()
  expect(screen.getByRole('button', { name: 'ALL · 3' }).getAttribute('aria-pressed')).toBe('true')
  const table = screen.getByRole('table', { name: 'Extra service requests' })
  expect(within(table).getByRole('row', { selected: true }).textContent).toContain('Grocery assistance')
  const panel = screen.getByRole('complementary', { name: 'Extra service detail' })
  expect(within(panel).queryByRole('button', { name: 'Assign caregiver' })).toBeNull()
})
