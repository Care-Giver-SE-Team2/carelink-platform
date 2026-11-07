import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import ExtraServices from './ExtraServices'
import * as authApi from '../../../features/auth/api'
import * as incidentsApi from '../../../features/incidents/api'
import * as valueAddedApi from '../../../features/value-added-services/api'
import type { CatalogueService } from '../../../features/value-added-services/types'
import * as profileApi from '../../../shared/api/profile'
import * as eldersData from '../data/elders'

let catalogue: CatalogueService[]

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

beforeEach(() => {
  catalogue = [
    {
      id: 1,
      name: 'Hospital escort',
      description: 'To a clinic appointment.',
      durationMinutes: 180,
      status: 'AVAILABLE',
      requestCount: 3,
      removable: false,
    },
    { id: 2, name: 'Laundry', description: null, durationMinutes: 60, status: 'UNAVAILABLE', requestCount: 0, removable: true },
  ]
  vi.spyOn(incidentsApi, 'listIncidentQueue').mockImplementation(async ({ size }) => ({ items: [], page: 0, size, totalElements: 0 }))
  vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue({ id: 1, username: 'tml', displayName: 'Tan Mei Ling', roles: ['MANAGER'] })
  vi.spyOn(profileApi, 'fetchCredentialRegister').mockResolvedValue([])
  vi.spyOn(profileApi, 'fetchCaregivers').mockResolvedValue([])
  vi.spyOn(eldersData, 'fetchElders').mockResolvedValue([])
  vi.spyOn(valueAddedApi, 'fetchManagedValueAddedServiceRequests').mockResolvedValue([])
  vi.spyOn(valueAddedApi, 'fetchServiceCatalogue').mockImplementation(async () => [...catalogue])
})

function renderPage(path = '/manager/extra-services?view=catalogue') {
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

it('switches between the requests and the catalogue', async () => {
  const user = userEvent.setup()
  renderPage('/manager/extra-services')

  expect(await screen.findByRole('region', { name: 'Extra service requests' })).toBeTruthy()
  await user.click(screen.getByRole('button', { name: 'CATALOGUE' }))

  const table = await screen.findByRole('table', { name: 'Extra service catalogue' })
  expect(within(table).getByText('Hospital escort')).toBeTruthy()
  expect(within(table).getByText('3 h')).toBeTruthy()
  expect(within(table).getByText('UNAVAILABLE')).toBeTruthy()
})

it('adds a service, which opens once saved', async () => {
  const user = userEvent.setup()
  const add = vi.spyOn(valueAddedApi, 'addValueAddedService').mockImplementation(async (create) => {
    const added: CatalogueService = { id: 3, ...create, status: 'AVAILABLE', requestCount: 0, removable: true }
    catalogue = [...catalogue, added]
    return added
  })
  renderPage()

  await user.click(await screen.findByRole('button', { name: '+ Add service' }))
  const panel = screen.getByRole('complementary', { name: 'New extra service' })
  await user.type(within(panel).getByLabelText('Name'), '  Medication collection ')
  await user.type(within(panel).getByLabelText('Description'), 'Collect prescriptions.')
  await user.clear(within(panel).getByLabelText('Duration'))
  await user.type(within(panel).getByLabelText('Duration'), '45')
  await user.click(within(panel).getByRole('button', { name: 'Add service' }))

  expect(add).toHaveBeenCalledWith({ name: 'Medication collection', description: 'Collect prescriptions.', durationMinutes: 45 })
  const opened = await screen.findByRole('complementary', { name: 'Extra service in the catalogue' })
  expect(within(opened).getByRole('heading', { name: 'Medication collection' })).toBeTruthy()
  expect(within(screen.getByRole('table', { name: 'Extra service catalogue' })).getByText('Medication collection')).toBeTruthy()
})

it('refuses a name already in the catalogue before sending it', async () => {
  const user = userEvent.setup()
  const add = vi.spyOn(valueAddedApi, 'addValueAddedService')
  renderPage('/manager/extra-services?view=catalogue&service=new')

  const panel = await screen.findByRole('complementary', { name: 'New extra service' })
  await user.type(within(panel).getByLabelText('Name'), 'laundry')
  await user.click(within(panel).getByRole('button', { name: 'Add service' }))

  expect(within(panel).getByText(/"Laundry" is in the catalogue but unavailable/)).toBeTruthy()
  expect(add).not.toHaveBeenCalled()
})

it('shows why the server refused a service', async () => {
  const user = userEvent.setup()
  vi.spyOn(valueAddedApi, 'addValueAddedService').mockRejectedValue(new Error('There is already a service called "Massage".'))
  renderPage('/manager/extra-services?view=catalogue&service=new')

  const panel = await screen.findByRole('complementary', { name: 'New extra service' })
  await user.type(within(panel).getByLabelText('Name'), 'Massage')
  await user.click(within(panel).getByRole('button', { name: 'Add service' }))

  expect((await within(panel).findByRole('alert')).textContent).toContain('already a service called "Massage"')
})

it('changes the length but never the name, and withdraws a service', async () => {
  const user = userEvent.setup()
  const update = vi.spyOn(valueAddedApi, 'updateValueAddedService').mockImplementation(async (id, change) => {
    catalogue = catalogue.map((s) => (s.id === id ? { ...s, ...change } : s))
    return catalogue.find((s) => s.id === id)!
  })
  renderPage('/manager/extra-services?view=catalogue&service=1')

  const panel = await screen.findByRole('complementary', { name: 'Extra service in the catalogue' })
  expect(within(panel).queryByLabelText('Name')).toBeNull()
  await user.clear(within(panel).getByLabelText('Duration'))
  await user.type(within(panel).getByLabelText('Duration'), '240')
  await user.click(within(panel).getByRole('button', { name: 'Save changes' }))
  expect(update).toHaveBeenCalledWith(1, { description: 'To a clinic appointment.', durationMinutes: 240, status: 'AVAILABLE' })

  await user.click(await within(panel).findByRole('button', { name: 'Mark unavailable' }))
  expect(update).toHaveBeenLastCalledWith(1, { description: 'To a clinic appointment.', durationMinutes: 240, status: 'UNAVAILABLE' })
  expect(await within(panel).findByRole('button', { name: 'Make available' })).toBeTruthy()
})

it('removes a service nobody asked for, after confirming', async () => {
  const user = userEvent.setup()
  const remove = vi.spyOn(valueAddedApi, 'removeValueAddedService').mockImplementation(async (id) => {
    catalogue = catalogue.filter((s) => s.id !== id)
  })
  renderPage('/manager/extra-services?view=catalogue&service=2')

  const panel = await screen.findByRole('complementary', { name: 'Extra service in the catalogue' })
  await user.click(within(panel).getByRole('button', { name: 'Remove service' }))
  const dialog = await screen.findByRole('dialog')
  expect(within(dialog).getByText('Remove Laundry?')).toBeTruthy()
  await user.click(within(dialog).getByRole('button', { name: 'Remove service' }))

  expect(remove).toHaveBeenCalledWith(2)
  const table = screen.getByRole('table', { name: 'Extra service catalogue' })
  await vi.waitFor(() => expect(within(table).queryByText('Laundry')).toBeNull())
  expect(screen.queryByRole('complementary', { name: 'Extra service in the catalogue' })).toBeNull()
})

it('keeps a service that was asked for, and says why', async () => {
  renderPage('/manager/extra-services?view=catalogue&service=1')

  const panel = await screen.findByRole('complementary', { name: 'Extra service in the catalogue' })
  expect(within(panel).queryByRole('button', { name: 'Remove service' })).toBeNull()
  expect(within(panel).getByText(/Asked for 3 times, so it stays in the catalogue/)).toBeTruthy()
})
