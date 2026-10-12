import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import Providers from './Providers'
import * as authApi from '../../../features/auth/api'
import * as incidentsApi from '../../../features/incidents/api'
import * as store from '../data/providerApplications'
import type { Provider, ProviderApplication } from '../data/providerApplications'

let pending: ProviderApplication[]
let approved: Provider[]

function application(overrides: Partial<ProviderApplication>): ProviderApplication {
  return {
    id: 14,
    organisationName: 'Kampung Care Escorts Pte Ltd',
    uen: '202312345K',
    contactName: 'Siti Aminah',
    contactEmail: 'ops@kampungcare.sg',
    contactPhone: '+65 6123 4501',
    services: ['Hospital escort', 'Companionship'],
    serviceArea: ['AMK', 'BSH'],
    webhookUrl: 'https://api.kampungcare.sg/carelink/webhooks',
    submittedAt: '2026-10-11T02:12:00Z',
    ...overrides,
  }
}

function provider(overrides: Partial<Provider>): Provider {
  return {
    ...application({
      id: 9,
      organisationName: 'BrightHome Services',
      uen: '201998765M',
      contactName: 'Ravi Kumar',
      contactEmail: 'partners@brighthome.sg',
      services: ['Light housekeeping'],
      serviceArea: ['TPY'],
      webhookUrl: 'https://brighthome.sg/api/carelink-jobs',
    }),
    providerId: 109,
    clientId: 'mock-client-0009',
    approvedAt: '2026-10-04T01:00:00Z',
    ...overrides,
  }
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

beforeEach(() => {
  pending = [
    application({}),
    application({
      id: 13,
      organisationName: 'FreshCart Errands',
      contactEmail: 'daniel@freshcart.com.sg',
      contactPhone: null,
      services: ['Grocery assistance'],
      serviceArea: ['TPY'],
      submittedAt: '2026-10-10T01:00:00Z',
    }),
  ]
  approved = [provider({})]
  vi.spyOn(incidentsApi, 'listIncidentQueue').mockImplementation(async ({ size }) => ({ items: [], page: 0, size, totalElements: 0 }))
  vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue({ id: 1, username: 'tml', displayName: 'Tan Mei Ling', roles: ['MANAGER'] })
  vi.spyOn(store, 'fetchProviderApplications').mockImplementation(async () => [...pending])
  vi.spyOn(store, 'fetchProviders').mockImplementation(async () => [...approved])
})

function renderPage(entry = '/manager/providers') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/manager/providers" element={<Providers />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

it('lists pending applications above every provider and opens the newest application', async () => {
  renderPage()

  const applications = await screen.findByRole('table', { name: 'Provider applications' })
  const appRows = within(applications).getAllByRole('row').slice(1)
  expect(appRows).toHaveLength(2)
  expect(appRows[0]).toHaveTextContent('Kampung Care Escorts Pte Ltd')
  expect(appRows[0]).toHaveTextContent('ops@kampungcare.sg')
  expect(appRows[0]).toHaveTextContent('Hospital escort, Companionship')
  expect(appRows[0]).toHaveTextContent('AMK, BSH')
  expect(appRows[0]).toHaveAttribute('aria-selected', 'true')
  expect(screen.getByText('2 waiting')).toBeInTheDocument()

  const providers = await screen.findByRole('table', { name: 'Providers' })
  const providerRows = within(providers).getAllByRole('row').slice(1)
  expect(providerRows[0]).toHaveTextContent('BrightHome Services')
  expect(providerRows[0]).toHaveTextContent('PRV-0109')
  expect(screen.getByText('1 approved')).toBeInTheDocument()

  const nav = screen.getByRole('link', { name: /Providers/ })
  expect(nav).toHaveAttribute('aria-current', 'page')
  expect(within(nav).getByText('2')).toBeInTheDocument()

  const detail = screen.getByRole('complementary', { name: 'Application detail' })
  expect(within(detail).getByRole('heading', { name: 'Kampung Care Escorts Pte Ltd' })).toBeInTheDocument()
  expect(within(detail).getByText('Provider application · #14')).toBeInTheDocument()
  expect(within(detail).getByText('202312345K')).toBeInTheDocument()
  expect(within(detail).getByText('https://api.kampungcare.sg/carelink/webhooks')).toBeInTheDocument()
  expect(within(detail).getByText(/can then be dispatched Hospital escort, Companionship/)).toBeInTheDocument()
})

it('shows what an applicant left out', async () => {
  renderPage()
  await userEvent.click(await screen.findByText('FreshCart Errands'))

  const detail = screen.getByRole('complementary', { name: 'Application detail' })
  expect(within(detail).getByText('not given')).toBeInTheDocument() // no phone
})

it('approves an application, then opens the next one and confirms what changed', async () => {
  const approve = vi.spyOn(store, 'approveProviderApplication').mockImplementation(async (id) => {
    const [answered] = pending.filter((a) => a.id === id)
    pending = pending.filter((a) => a.id !== id)
    approved = [
      ...approved,
      { ...answered, providerId: 100 + id, clientId: 'mock-client-0014', approvedAt: '2026-10-12T05:00:00Z' },
    ]
    return { providerId: 100 + id }
  })
  renderPage()

  const detail = await screen.findByRole('complementary', { name: 'Application detail' })
  await userEvent.type(within(detail).getByRole('textbox'), 'Welcome aboard')
  await userEvent.click(within(detail).getByRole('button', { name: 'Approve' }))

  expect(approve).toHaveBeenCalledWith(14, 'Welcome aboard')
  expect(await screen.findByRole('status')).toHaveTextContent(
    'Kampung Care Escorts Pte Ltd approved. They can now collect their Open API credentials from the workbench.',
  )
  const next = await screen.findByRole('heading', { name: 'FreshCart Errands' })
  expect(next.closest('aside')).toHaveAccessibleName('Application detail')

  const providers = screen.getByRole('table', { name: 'Providers' })
  expect(await within(providers).findByText('Kampung Care Escorts Pte Ltd')).toBeInTheDocument()
})

it('opens the new provider once the last application is approved', async () => {
  pending = [pending[0]]
  vi.spyOn(store, 'approveProviderApplication').mockImplementation(async (id) => {
    const [answered] = pending
    pending = []
    approved = [
      ...approved,
      { ...answered, providerId: 100 + id, clientId: 'mock-client-0014', approvedAt: '2026-10-12T05:00:00Z' },
    ]
    return { providerId: 100 + id }
  })
  renderPage()

  const detail = await screen.findByRole('complementary', { name: 'Application detail' })
  await userEvent.click(within(detail).getByRole('button', { name: 'Approve' }))

  const panel = await screen.findByRole('complementary', { name: 'Provider detail' })
  expect(await within(panel).findByRole('heading', { name: 'Kampung Care Escorts Pte Ltd' })).toBeInTheDocument()
  expect(within(panel).getByText('Provider · PRV-0114')).toBeInTheDocument()
  expect(within(panel).getByText('mock-client-0014')).toBeInTheDocument()
  expect(screen.getByText('No applications waiting. New ones from the provider workbench appear here.')).toBeInTheDocument()
})

it('needs a message to decline', async () => {
  const decline = vi.spyOn(store, 'declineProviderApplication').mockImplementation(async (id) => {
    pending = pending.filter((a) => a.id !== id)
  })
  renderPage()

  const detail = await screen.findByRole('complementary', { name: 'Application detail' })
  await userEvent.click(within(detail).getByRole('button', { name: 'Decline' }))
  expect(decline).not.toHaveBeenCalled()
  expect(within(detail).getByText('Tell Kampung Care Escorts Pte Ltd why')).toBeInTheDocument()

  await userEvent.type(within(detail).getByRole('textbox'), 'We do not cover BSH yet')
  await userEvent.click(within(detail).getByRole('button', { name: 'Decline' }))

  expect(decline).toHaveBeenCalledWith(14, 'We do not cover BSH yet')
  expect(await screen.findByRole('status')).toHaveTextContent("Kampung Care Escorts Pte Ltd's application declined.")
})

it('keeps the application open and shows the error when a decision fails', async () => {
  vi.spyOn(store, 'approveProviderApplication').mockRejectedValue(new Error('This application has already been answered.'))
  renderPage()

  const detail = await screen.findByRole('complementary', { name: 'Application detail' })
  await userEvent.click(within(detail).getByRole('button', { name: 'Approve' }))

  expect(await within(detail).findByRole('alert')).toHaveTextContent('This application has already been answered.')
  expect(within(detail).getByRole('button', { name: 'Approve' })).toBeEnabled()
})

it('shows a provider with its API access, and no secrets', async () => {
  renderPage('/manager/providers?provider=109')

  await screen.findByRole('heading', { name: 'BrightHome Services' })
  const panel = screen.getByRole('complementary', { name: 'Provider detail' })
  expect(within(panel).getByText('ACTIVE')).toBeInTheDocument()
  expect(within(panel).getByText('mock-client-0009')).toBeInTheDocument()
  expect(within(panel).getByText('https://brighthome.sg/api/carelink-jobs')).toBeInTheDocument()
  expect(within(panel).getByText(/shown only to the provider/)).toBeInTheDocument()

  const applications = screen.getByRole('table', { name: 'Provider applications' })
  for (const row of within(applications).getAllByRole('row').slice(1)) {
    expect(row).not.toHaveAttribute('aria-selected', 'true')
  }
})

it('says when the lists cannot be loaded', async () => {
  vi.spyOn(store, 'fetchProviderApplications').mockRejectedValue(new Error('down'))
  vi.spyOn(store, 'fetchProviders').mockRejectedValue(new Error('down'))
  renderPage()

  expect(await screen.findByText('Could not load applications.')).toBeInTheDocument()
  expect(await screen.findByText('Could not load providers.')).toBeInTheDocument()
})
