import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import CarePlan from './CarePlan'
import * as authApi from '../../../features/auth/api'
import * as carePlanApi from '../../../shared/api/careplan'
import * as profileApi from '../../../shared/api/profile'
import type { CarePlanNodeResponse, CarePlanResponse } from '../../../shared/api/careplan'
import type { ElderResponse } from '../../../shared/api/profile'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

const everyDay = (startTime: string, minutes: number) =>
  ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((day) => ({ day, startTime, minutes }))

const nodes: CarePlanNodeResponse[] = [
  { id: 1, groupName: 'Personal care', name: 'Grooming', visits: [{ day: 'Mon', startTime: '08:30:00', minutes: 15 }, { day: 'Fri', startTime: '09:00:00', minutes: 15 }], evidenceType: 'CHECKLIST', weeklyHours: null },
  { id: 2, groupName: 'Medication support', name: 'Morning reminder', visits: everyDay('08:00:00', 5), evidenceType: 'CHECKLIST', weeklyHours: null },
  { id: 3, groupName: 'Medication support', name: 'Evening reminder', visits: everyDay('19:00:00', 5), evidenceType: 'CHECKLIST', weeklyHours: null },
]

const publishedV4 = {
  id: 42,
  version: 4,
  status: 'PUBLISHED',
  totalHours: 1.67,
  publishedAt: '2026-08-26T10:00:00',
  startDate: '2024-04-11',
  updatedAt: '2026-08-26T10:00:00',
  stopEffectiveDate: null,
  stopReason: null,
} as CarePlanResponse

const supersededV3 = {
  id: 41,
  version: 3,
  status: 'SUPERSEDED',
  totalHours: 0.5,
  publishedAt: '2026-07-02T09:00:00',
  startDate: '2024-04-11',
} as CarePlanResponse

function renderPlan() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/manager/elders/7']}>
        <Routes>
          <Route path="/manager/elders/:elderId" element={<CarePlan />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue({ id: 1, username: 'tml', displayName: 'Tan Mei Ling', roles: ['MANAGER'] })
  vi.spyOn(console, 'info').mockImplementation(() => {})
  vi.spyOn(profileApi, 'fetchElder').mockResolvedValue({
    id: 7,
    fullName: 'Chan Bee Choo',
    dateOfBirth: '1943-01-01',
    address: 'Bishan St 23',
    sector: 'S31',
    livesAlone: true,
    preferredDialects: 'Malay,Hokkien',
    mobilityLevel: 'ASSISTIVE_CANE',
    continuityPreference: 'PREFERRED',
  } as ElderResponse)
  vi.spyOn(profileApi, 'fetchElderFamily').mockResolvedValue([
    { fullName: 'Wei Ling', relationship: 'DAUGHTER', primaryContact: true },
  ])
  vi.spyOn(carePlanApi, 'fetchLatestCarePlan').mockResolvedValue(publishedV4)
  vi.spyOn(carePlanApi, 'fetchCarePlanVersions').mockResolvedValue([publishedV4, supersededV3])
  vi.spyOn(carePlanApi, 'fetchCarePlanNodes').mockResolvedValue(nodes)
})

async function renderPlanInDraft() {
  const user = userEvent.setup()
  renderPlan()
  await screen.findByText('Grooming')
  // A published plan opens read-only; editing starts a draft.
  expect(screen.queryByRole('button', { name: 'Remove task from sub-plan' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Edit plan' }))
  return user
}

it('shows each task’s schedule as per-day tags, collapsed to one Daily tag when every day matches', async () => {
  await renderPlanInDraft()
  const grooming = screen.getByText('Grooming').parentElement!
  expect(within(grooming).getByText('Mon 8:30–8:45 AM · 15 m')).toBeInTheDocument()
  expect(within(grooming).getByText('Fri 9:00–9:15 AM · 15 m')).toBeInTheDocument()
  expect(screen.getByText('Daily 8:00–8:05 AM · 5 m')).toBeInTheDocument()
  expect(screen.getByText('Daily 7:00–7:05 PM · 5 m')).toBeInTheDocument()
  expect(screen.queryByText('Per visit')).not.toBeInTheDocument()
  // Weekly sums each task's per-day minutes: 2 × 15 m = 0.50 h, 7 × 5 m = 0.58 h.
  const groomingRow = screen.getByText('Grooming').closest('div')!
  expect(groomingRow).toHaveTextContent('0.50 h')
  expect(screen.getByText('Morning reminder').closest('div')!).toHaveTextContent('0.58 h')
})

it('asks before deleting a sub-plan, and Cancel keeps it', async () => {
  const user = await renderPlanInDraft()

  await user.click(screen.getByRole('button', { name: 'Delete sub-plan Medication support and its tasks' }))
  const dialog = screen.getByRole('dialog', { name: 'Delete "Medication support"?' })
  expect(dialog).toHaveTextContent('This sub-plan has 2 tasks totalling 1.17 h/week. Deleting it removes both tasks')

  await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Medication support' })).toBeInTheDocument()
})

it('deletes the sub-plan and its tasks on confirm, and recomputes the total', async () => {
  const user = await renderPlanInDraft()
  expect(screen.getByText('1.67 h')).toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: 'Delete sub-plan Medication support and its tasks' }))
  await user.click(screen.getByRole('button', { name: 'Delete sub-plan' }))

  expect(screen.queryByRole('button', { name: 'Medication support' })).not.toBeInTheDocument()
  expect(screen.queryByText('Morning reminder')).not.toBeInTheDocument()
  // Plan total (and the Personal care row) now read 0.50 h.
  expect(screen.getAllByText('0.50 h').length).toBeGreaterThanOrEqual(2)
  expect(screen.getByRole('status')).toHaveTextContent('changes weekly effort 1.67 h → 0.50 h')
})

it('removes a task at once, without a confirmation', async () => {
  const user = await renderPlanInDraft()
  const row = screen.getByText('Morning reminder').closest('div')!

  await user.click(within(row).getByRole('button', { name: 'Remove task from sub-plan' }))

  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(screen.queryByText('Morning reminder')).not.toBeInTheDocument()
  expect(screen.getByText('Evening reminder')).toBeInTheDocument()
})

it('adds a sub-plan from the grouped activity picker and a per-day schedule', async () => {
  const user = await renderPlanInDraft()
  await user.click(screen.getByRole('button', { name: 'Add sub-plan' }))

  const submit = screen.getByRole('button', { name: /^Add sub-plan \(/ })
  expect(submit).toBeDisabled()

  await user.selectOptions(screen.getByLabelText('Step 1 · select sub-plan'), 'Companionship walk')
  await user.click(screen.getByRole('button', { name: 'Tuesday' }))
  await user.click(screen.getByRole('button', { name: 'Saturday' }))
  expect(submit).toHaveTextContent('Add sub-plan (2 activities)')
  await user.click(submit)

  expect(screen.getByRole('button', { name: 'Social and mobility' })).toBeInTheDocument()
  const walk = screen.getByText('Companionship walk').parentElement!
  expect(within(walk).getByText('Tue 8:00–8:15 AM · 15 m')).toBeInTheDocument()
  expect(within(walk).getByText('Sat 8:00–8:15 AM · 15 m')).toBeInTheDocument()
})

it('shows the elder’s profile and bound family from the backend', async () => {
  renderPlan()
  await screen.findByText('Wei Ling (daughter)')
  expect(screen.getByText('Malay, Hokkien')).toBeInTheDocument()
  expect(screen.getByText('alone')).toBeInTheDocument()
  expect(screen.getByText('cane or walker')).toBeInTheDocument()
  expect(screen.getByText('preferred')).toBeInTheDocument()
})

it('lists every issued version, newest first, with its weekly effort', async () => {
  renderPlan()
  await screen.findByText('v4 · 26 Aug 2026')
  expect(screen.getByText('1.67 h/week · from 11 Apr 2024')).toBeInTheDocument()
  expect(screen.getByText('v3 · 2 Jul 2026')).toBeInTheDocument()
  expect(screen.getByText('0.50 h/week · from 11 Apr 2024')).toBeInTheDocument()
})

it('compares a saved draft against the version it supersedes', async () => {
  vi.mocked(carePlanApi.fetchLatestCarePlan).mockResolvedValue({
    ...publishedV4,
    id: 43,
    version: 5,
    status: 'DRAFT',
    supersedesPlanId: 42,
    publishedAt: null,
  })
  vi.mocked(carePlanApi.fetchCarePlanVersions).mockResolvedValue([
    { ...publishedV4, id: 43, version: 5, status: 'DRAFT', supersedesPlanId: 42, publishedAt: null, totalHours: null },
    publishedV4,
  ])
  renderPlan()
  expect(await screen.findByRole('button', { name: 'Publish v5' })).toBeInTheDocument()
  expect(await screen.findByRole('status')).toHaveTextContent('Draft v5 changes weekly effort 1.67 h →')
})
