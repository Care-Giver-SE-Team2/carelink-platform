import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import SpotChecks from './SpotChecks'
import * as authApi from '../../../../features/auth/api'
import * as incidentsApi from '../../../../features/incidents/api'
import * as spotChecksApi from '../../../../features/spot-checks/api'
import type { SpotCheck } from '../../../../features/spot-checks/types'
import * as profileApi from '../../../../shared/api/profile'
import * as rosteringApi from '../../../../shared/api/rostering'

/** The manager's UC-MG08 screen against a stubbed API. @author Wang Ziyu */

const scheduled: SpotCheck = {
  id: 100,
  elderId: 7,
  elderName: 'Mdm Tan',
  caregiverId: 5,
  caregiverName: 'Aisha',
  visitId: 30,
  visitTime: '2026-10-09T10:00:00',
  purpose: 'Follow-up on a missed medication',
  stage: 'SCHEDULED',
  decidedAt: '2026-10-07T09:30:00',
  result: null,
  notes: null,
  checkedAt: null,
  closingReason: null,
  incidentId: null,
  caregiverResponse: null,
}

const noShow: SpotCheck = {
  ...scheduled,
  id: 101,
  visitId: 31,
  visitTime: '2026-10-02T10:00:00',
  stage: 'CAREGIVER_NO_SHOW',
  incidentId: 900,
}

beforeEach(() => {
  vi.spyOn(incidentsApi, 'listIncidentQueue').mockImplementation(async ({ size }) => ({ items: [], page: 0, size, totalElements: 0 }))
  vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue({ id: 11, username: 'alice', displayName: 'Alice Tan', roles: ['MANAGER'] })
  vi.spyOn(profileApi, 'fetchCredentialRegister').mockResolvedValue([])
  vi.spyOn(rosteringApi, 'fetchVisitsAtRisk').mockResolvedValue([])
  vi.spyOn(profileApi, 'fetchElderList').mockResolvedValue([
    { id: 7, fullName: 'Mdm Tan', dateOfBirth: null, address: null, sector: null, planStatus: 'published' } as never,
  ])
  vi.spyOn(spotChecksApi, 'listVisitsToCheck').mockResolvedValue([
    { visitId: 30, start: '2026-10-09T10:00:00', serviceType: 'Personal care', caregiverId: 5, caregiverName: 'Aisha' },
    { visitId: 32, start: '2026-10-16T10:00:00', serviceType: 'Personal care', caregiverId: 6, caregiverName: 'Ben' },
  ])
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function renderScreen() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <SpotChecks />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

it('records a conclusion, a no-show or a move for a check the family agreed to', async () => {
  vi.spyOn(spotChecksApi, 'listSpotChecks').mockResolvedValue([scheduled, noShow])
  const conclude = vi.spyOn(spotChecksApi, 'concludeSpotCheck').mockResolvedValue(scheduled)
  const missed = vi.spyOn(spotChecksApi, 'reportNoShow').mockResolvedValue(noShow)
  const move = vi.spyOn(spotChecksApi, 'moveSpotCheck').mockResolvedValue(scheduled)

  renderScreen()

  const card = await screen.findByRole('listitem', { name: 'Spot check of Mdm Tan on Fri 9 Oct, 10:00' })
  await userEvent.selectOptions(within(card).getByLabelText('Conclusion'), 'NEEDS_IMPROVEMENT')
  await userEvent.type(within(card).getByLabelText('Notes'), 'Gloves not worn')
  await userEvent.click(within(card).getByRole('button', { name: 'Record conclusion' }))
  expect(conclude).toHaveBeenCalledWith(100, 'NEEDS_IMPROVEMENT', 'Gloves not worn')

  await userEvent.click(within(card).getByRole('button', { name: 'Caregiver did not come' }))
  expect(missed).toHaveBeenCalledWith(100, 'Gloves not worn')

  await within(card).findByRole('option', { name: /Fri 16 Oct, 10:00 · Ben/ })
  await userEvent.selectOptions(within(card).getByLabelText('Elder was out: move to'), '32')
  await userEvent.click(within(card).getByRole('button', { name: 'Move and ask again' }))
  expect(move).toHaveBeenCalledWith(100, 32)

  expect(screen.getByRole('link', { name: 'Exception EXC-900' })).toHaveAttribute('href', '/manager/exceptions/900')
})

it('asks the family about a visit chosen from the elder', async () => {
  vi.spyOn(spotChecksApi, 'listSpotChecks').mockResolvedValue([])
  const request = vi.spyOn(spotChecksApi, 'requestSpotCheck').mockResolvedValue(scheduled)

  renderScreen()

  const form = await screen.findByRole('form', { name: 'Ask for a spot check' })
  await within(form).findByRole('option', { name: 'Mdm Tan' })
  await userEvent.selectOptions(within(form).getByLabelText('Elder'), '7')
  await within(form).findByRole('option', { name: /Fri 9 Oct, 10:00 · Aisha/ })
  await userEvent.selectOptions(within(form).getByLabelText('Visit'), '30')
  await userEvent.type(within(form).getByLabelText('Why this visit is being checked'), 'Routine')
  await userEvent.click(within(form).getByRole('button', { name: 'Ask the family' }))

  expect(request).toHaveBeenCalledWith(30, 'Routine')
  expect(await within(form).findByText(/The family has been sent the request/)).toBeInTheDocument()
  expect(screen.getByText(/No spot checks/)).toBeInTheDocument()
})

it('withdraws an open check with a reason and shows why a step was refused', async () => {
  vi.spyOn(spotChecksApi, 'listSpotChecks').mockResolvedValue([{ ...scheduled, stage: 'AWAITING_FAMILY' }])
  vi.spyOn(spotChecksApi, 'withdrawSpotCheck').mockRejectedValue(new Error('offline'))

  renderScreen()

  const card = await screen.findByRole('listitem', { name: /Spot check of Mdm Tan/ })
  expect(within(card).queryByRole('button', { name: 'Record conclusion' })).not.toBeInTheDocument()
  await userEvent.type(within(card).getByLabelText('Reason to withdraw'), 'Aisha moved')
  await userEvent.click(within(card).getByRole('button', { name: 'Withdraw' }))

  expect(await within(card).findByRole('alert')).toHaveTextContent('The request could not be completed')
})
