import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import {
  createFamilyValueAddedServiceRequest,
  fetchFamilyValueAddedServices,
} from '../../../features/value-added-services/api'
import { ApiError } from '../../../shared/api/client'
import { FamilyValueAddedRequestForm } from './FamilyValueAddedRequestForm'

vi.mock('../../../features/value-added-services/api', () => ({
  createFamilyValueAddedServiceRequest: vi.fn(),
  fetchFamilyValueAddedServices: vi.fn(),
}))

const catalogue = [
  { id: 1, name: 'Hospital escort', description: 'Escort to appointments', durationMinutes: 180, status: 'AVAILABLE' as const },
  { id: 2, name: 'Companionship', description: 'Company at home', durationMinutes: 120, status: 'AVAILABLE' as const },
]

// The picker's earliest time is "now" plus the notice period, and jsdom refuses to submit a value
// below it, so pin the clock before the times the tests type in. Only Date is faked; user-event
// still needs real timers.
beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 9, 1, 9, 0))
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  vi.useRealTimers()
})

function renderForm(onCreated = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <FamilyValueAddedRequestForm elderId={10} onCreated={onCreated} />
    </QueryClientProvider>,
  )
  return onCreated
}

it('books the chosen service for the elder and hands the new request back', async () => {
  const user = userEvent.setup()
  vi.mocked(fetchFamilyValueAddedServices).mockResolvedValue(catalogue)
  const created = {
    id: 9, elderId: 10, valueAddedServiceId: 2, serviceName: 'Companionship', requestedByFamilyMemberId: 20,
    approvingFamilyMemberId: 20, visitId: 77, requestedSchedule: '2026-10-12T10:00:00', specialInstructions: 'Bring cards',
    status: 'DISPATCHED' as const, decidedAt: '2026-10-10T09:00:00', createdAt: '2026-10-10T09:00:00',
  }
  vi.mocked(createFamilyValueAddedServiceRequest).mockResolvedValue(created)
  const onCreated = renderForm()

  await user.selectOptions(await screen.findByLabelText('Service'), '2')
  expect(screen.getByText(/Company at home · about 2 h/)).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Date and time'), { target: { value: '2026-10-12T10:00' } })
  await user.type(screen.getByLabelText(/Instructions for the caregiver/), '  Bring cards ')
  await user.click(screen.getByRole('button', { name: 'Book service' }))

  expect(createFamilyValueAddedServiceRequest).toHaveBeenCalledWith({
    elderId: 10, valueAddedServiceId: 2, requestedSchedule: '2026-10-12T10:00', specialInstructions: 'Bring cards',
  })
  expect(onCreated).toHaveBeenCalledWith(created)
  expect(await screen.findByRole('status')).toHaveTextContent('Companionship is booked. The care team has been told.')
})

it('asks for a time before sending anything', async () => {
  const user = userEvent.setup()
  vi.mocked(fetchFamilyValueAddedServices).mockResolvedValue(catalogue)
  renderForm()

  await user.click(await screen.findByRole('button', { name: 'Book service' }))

  expect(screen.getByText('Choose a date and time.')).toBeInTheDocument()
  expect(screen.getByLabelText('Date and time')).toHaveAttribute('aria-invalid', 'true')
  expect(createFamilyValueAddedServiceRequest).not.toHaveBeenCalled()
})

it('keeps a booking within service hours, so a 3-hour escort cannot start after 5 pm', async () => {
  const user = userEvent.setup()
  vi.mocked(fetchFamilyValueAddedServices).mockResolvedValue(catalogue)
  renderForm()

  expect(await screen.findByText(/Start between 8 am and 5 pm, on the hour or half hour/)).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Date and time'), { target: { value: '2026-10-12T17:30' } })
  await user.click(screen.getByRole('button', { name: 'Book service' }))

  expect(screen.getByText('Choose a start between 8 am and 5 pm, so the visit ends by 8 pm.')).toBeInTheDocument()
  expect(createFamilyValueAddedServiceRequest).not.toHaveBeenCalled()
})

it('shows the server\'s answer about the time under the date and time', async () => {
  const user = userEvent.setup()
  vi.mocked(fetchFamilyValueAddedServices).mockResolvedValue(catalogue)
  vi.mocked(createFamilyValueAddedServiceRequest).mockRejectedValue(
    new ApiError('Choose a time within the next 90 days.', 409, { code: 'VALUE_ADDED_SERVICE_TOO_FAR' }),
  )
  renderForm()

  fireEvent.change(await screen.findByLabelText('Date and time'), { target: { value: '2026-10-12T10:00' } })
  await user.click(screen.getByRole('button', { name: 'Book service' }))

  expect(await screen.findByText('Choose a time within the next 90 days.')).toBeInTheDocument()
  expect(screen.queryByRole('alert')).toBeNull()
})

it('shows why the server refused, e.g. read-only access or too little notice', async () => {
  const user = userEvent.setup()
  vi.mocked(fetchFamilyValueAddedServices).mockResolvedValue(catalogue)
  vi.mocked(createFamilyValueAddedServiceRequest).mockRejectedValue(
    new ApiError('Choose a time at least 2 hours from now.', 409),
  )
  const onCreated = renderForm()

  fireEvent.change(await screen.findByLabelText('Date and time'), { target: { value: '2026-10-10T10:00' } })
  await user.click(screen.getByRole('button', { name: 'Book service' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('Choose a time at least 2 hours from now.')
  expect(onCreated).not.toHaveBeenCalled()
})
