import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

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
  { id: 1, groupName: 'Personal care', activityCode: 'GROOMING', name: 'Grooming', visits: [{ day: 'Mon', startTime: '08:30:00', minutes: 15 }, { day: 'Fri', startTime: '09:00:00', minutes: 15 }], evidenceType: 'CHECKLIST', weeklyHours: null },
  { id: 2, groupName: 'Medication support', activityCode: 'MORNING_MEDICATION_REMINDER', name: 'Morning reminder', visits: everyDay('08:00:00', 5), evidenceType: 'CHECKLIST', weeklyHours: null },
  { id: 3, groupName: 'Medication support', activityCode: 'EVENING_MEDICATION_REMINDER', name: 'Evening reminder', visits: everyDay('19:00:00', 5), evidenceType: 'CHECKLIST', weeklyHours: null },
]

const catalog = [
  { code: 'BATHING', label: 'Bathing assistance', category: 'Personal care' },
  { code: 'GROOMING', label: 'Grooming', category: 'Personal care' },
  { code: 'VITALS', label: 'Vital-sign check', category: 'Health monitoring' },
  { code: 'MORNING_MEDICATION_REMINDER', label: 'Morning reminder', category: 'Medication support' },
  { code: 'EVENING_MEDICATION_REMINDER', label: 'Evening reminder', category: 'Medication support' },
  { code: 'COMPANIONSHIP_WALK', label: 'Companionship walk', category: 'Social and mobility' },
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

const draftV5 = {
  ...publishedV4,
  id: 43,
  version: 5,
  status: 'DRAFT',
  supersedesPlanId: 42,
  publishedAt: null,
  totalHours: null,
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
  vi.spyOn(carePlanApi, 'fetchCareActivities').mockResolvedValue(catalog)
  vi.spyOn(profileApi, 'fetchElderCareRequests').mockResolvedValue([])
  vi.spyOn(carePlanApi, 'createCarePlanDraft').mockResolvedValue(draftV5)
  vi.spyOn(carePlanApi, 'saveCarePlanDraft').mockResolvedValue(draftV5)
  vi.spyOn(carePlanApi, 'discardCarePlanDraft').mockResolvedValue(undefined)
  vi.spyOn(carePlanApi, 'publishCarePlan').mockResolvedValue({ ...publishedV4, id: 43, version: 5 })
})

async function renderPlanInDraft() {
  const user = userEvent.setup()
  renderPlan()
  await screen.findByRole('button', { name: 'Edit plan' })
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

describe('an elder with no care plan yet', () => {
  beforeEach(() => {
    vi.mocked(carePlanApi.fetchLatestCarePlan).mockResolvedValue(null)
    vi.mocked(carePlanApi.fetchCarePlanVersions).mockResolvedValue([])
    vi.mocked(profileApi.fetchElderCareRequests).mockResolvedValue([
      { source: 'SERVICE_APPLICATION', applicationId: 9, careNeeds: ['GROOMING', 'Help with the cat'], notes: 'Afternoons please', submittedAt: '2026-10-01T09:00:00', outcome: 'SUBMITTED', needs: [], declineReason: null },
      { source: 'INTAKE', applicationId: 3, careNeeds: ['VITALS', 'BATHING'], notes: null, submittedAt: '2026-09-16T09:00:00', outcome: 'SUBMITTED', needs: [], declineReason: null },
    ])
  })

  it('starts the first draft with exactly the catalog activities the family applied for', async () => {
    renderPlan()
    await screen.findByRole('button', { name: 'Personal care' })
    expect(screen.getByRole('button', { name: 'Health monitoring' })).toBeInTheDocument()
    expect(taskRow('Bathing assistance')).toBeInTheDocument()
    expect(taskRow('Grooming')).toBeInTheDocument()
    expect(taskRow('Vital-sign check')).toBeInTheDocument()
    // A free-text need from an older application is shown in the rail only, never planned.
    expect(screen.getAllByText('Help with the cat')).toHaveLength(1)
    expect(screen.queryByText('Morning reminder')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Publish v1' })).toBeInTheDocument()
    expect(fetchedNodes()).toBe(false)
  })

  it('keeps Publish off until every requested activity has days and times', async () => {
    const user = userEvent.setup()
    renderPlan()
    await screen.findByRole('button', { name: 'Personal care' })
    await user.type(screen.getByLabelText('Starts'), '2026-11-02')
    const publish = screen.getByRole('button', { name: 'Publish v1' })
    expect(publish).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent('Set days and times for Bathing assistance, Grooming, Vital-sign check')

    for (const name of ['Bathing assistance', 'Grooming', 'Vital-sign check']) {
      await user.click(within(taskRow(name)).getByRole('button', { name: 'Edit task' }))
      await user.click(screen.getByRole('button', { name: 'Monday' }))
      await user.click(screen.getByRole('button', { name: 'Save changes' }))
    }

    expect(screen.queryByText(/Set days and times/)).not.toBeInTheDocument()
    expect(publish).toBeEnabled()
  })

  it('lists what the family asked for in the rail, with how the plan answers each', async () => {
    renderPlan()
    await screen.findByRole('button', { name: 'Personal care' })
    const rail = within(screen.getByText('Requested by family').parentElement!)
    expect(rail.getByText('Vital-sign check').parentElement).toHaveTextContent('Set days')
    expect(rail.getByText('Help with the cat').parentElement).toHaveTextContent('Own words')
    expect(rail.getByText('“Afternoons please”')).toBeInTheDocument()
  })
})

/** The plan tree's row for a task (the rail lists the same names, without row actions). */
function taskRow(name: string): HTMLElement {
  const rail = screen.getByRole('complementary', { name: 'Elder' })
  const row = screen.getAllByText(name).find((el) => !rail.contains(el))?.closest('div')
  if (!row) throw new Error(`No task row for ${name}`)
  return row
}

function fetchedNodes() {
  return vi.mocked(carePlanApi.fetchCarePlanNodes).mock.calls.length > 0
}

it('does not reseed an elder who already has a plan', async () => {
  vi.mocked(profileApi.fetchElderCareRequests).mockResolvedValue([
    { source: 'INTAKE', applicationId: 3, careNeeds: ['BATHING'], notes: null, submittedAt: '2026-09-16T09:00:00', outcome: 'SUBMITTED', needs: [], declineReason: null },
  ])
  renderPlan()
  await screen.findByText('Grooming')
  const rail = within(screen.getByText('Requested by family').parentElement!)
  expect(rail.getByText('Bathing assistance').parentElement).toHaveTextContent('Not in plan')
  expect(screen.getAllByText('Bathing assistance')).toHaveLength(1)
})

describe('saving the draft', () => {
  it('saves the whole draft, with activity codes, once the manager pauses after an edit', async () => {
    const user = await renderPlanInDraft()
    await user.click(within(screen.getByText('Morning reminder').closest('div')!).getByRole('button', { name: 'Remove task from sub-plan' }))

    await waitFor(() => expect(carePlanApi.saveCarePlanDraft).toHaveBeenCalledTimes(1), { timeout: 2000 })
    expect(carePlanApi.createCarePlanDraft).toHaveBeenCalledTimes(1)
    const [id, startDate, saved] = vi.mocked(carePlanApi.saveCarePlanDraft).mock.calls[0]
    expect(id).toBe(43)
    expect(startDate).toBe('2024-04-11')
    expect(saved.map((n) => [n.activityCode, n.name])).toEqual([
      ['GROOMING', 'Grooming'],
      ['EVENING_MEDICATION_REMINDER', 'Evening reminder'],
    ])
    expect(await screen.findByText('Draft saved')).toBeInTheDocument()
  })

  it('does not save just because a plan was opened or Edit plan was pressed', async () => {
    await renderPlanInDraft()
    await new Promise((resolve) => setTimeout(resolve, 1000))
    expect(carePlanApi.saveCarePlanDraft).not.toHaveBeenCalled()
    expect(carePlanApi.createCarePlanDraft).not.toHaveBeenCalled()
  })

  it('keeps saving to the draft the page opened with', async () => {
    vi.mocked(carePlanApi.fetchLatestCarePlan).mockResolvedValue(draftV5)
    const user = userEvent.setup()
    renderPlan()
    await screen.findByRole('button', { name: 'Publish v5' })
    await user.click(within(screen.getByText('Morning reminder').closest('div')!).getByRole('button', { name: 'Remove task from sub-plan' }))

    await waitFor(() => expect(carePlanApi.saveCarePlanDraft).toHaveBeenCalled(), { timeout: 2000 })
    expect(vi.mocked(carePlanApi.saveCarePlanDraft).mock.calls[0][0]).toBe(43)
    expect(carePlanApi.createCarePlanDraft).not.toHaveBeenCalled()
  })

  it('publishes the draft the edits were saved to', async () => {
    const user = await renderPlanInDraft()
    await user.click(within(screen.getByText('Morning reminder').closest('div')!).getByRole('button', { name: 'Remove task from sub-plan' }))
    await user.click(screen.getByRole('button', { name: 'Publish v5' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Publish' }))

    await waitFor(() => expect(carePlanApi.publishCarePlan).toHaveBeenCalled())
    expect(vi.mocked(carePlanApi.publishCarePlan).mock.calls[0][0]).toBe(43)
    expect(carePlanApi.createCarePlanDraft).toHaveBeenCalledTimes(1)
  })

  it('discards a saved draft and goes back to the version in force', async () => {
    vi.mocked(carePlanApi.fetchLatestCarePlan).mockResolvedValueOnce(draftV5).mockResolvedValue(publishedV4)
    const user = userEvent.setup()
    renderPlan()
    await screen.findByRole('button', { name: 'Publish v5' })

    await user.click(screen.getByRole('button', { name: 'Discard draft' }))
    const dialog = screen.getByRole('dialog', { name: 'Discard draft v5?' })
    expect(dialog).toHaveTextContent('v4 stays in force')
    await user.click(within(dialog).getByRole('button', { name: 'Discard draft' }))

    expect(carePlanApi.discardCarePlanDraft).toHaveBeenCalledWith(43)
    expect(await screen.findByRole('button', { name: 'Edit plan' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Discard draft' })).not.toBeInTheDocument()
  })

  it('discards unpublished edits to a published plan without a saved draft', async () => {
    const user = await renderPlanInDraft()
    await user.click(within(screen.getByText('Morning reminder').closest('div')!).getByRole('button', { name: 'Remove task from sub-plan' }))
    await user.click(screen.getByRole('button', { name: 'Discard draft' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Discard draft' }))

    expect(await screen.findByRole('button', { name: 'Edit plan' })).toBeInTheDocument()
    expect(screen.getByText('Morning reminder')).toBeInTheDocument()
    await new Promise((resolve) => setTimeout(resolve, 1000))
    expect(carePlanApi.saveCarePlanDraft).not.toHaveBeenCalled()
  })
})

describe('a first plan built from the family’s applications', () => {
  beforeEach(() => {
    vi.mocked(carePlanApi.fetchLatestCarePlan).mockResolvedValue(null)
    vi.mocked(carePlanApi.fetchCarePlanVersions).mockResolvedValue([])
    vi.mocked(carePlanApi.createCarePlanDraft).mockResolvedValue({ ...draftV5, version: 1, supersedesPlanId: null })
    vi.mocked(profileApi.fetchElderCareRequests).mockResolvedValue([
      { source: 'INTAKE', applicationId: 3, careNeeds: ['BATHING', 'VITALS'], notes: null, submittedAt: '2026-09-16T09:00:00', outcome: 'SUBMITTED', needs: [], declineReason: null },
    ])
  })

  it('saves the requested activities, unscheduled, once the manager starts editing', async () => {
    const user = userEvent.setup()
    renderPlan()
    await screen.findByRole('button', { name: 'Personal care' })
    await user.type(screen.getByLabelText('Starts'), '2026-11-02')

    await waitFor(() => expect(carePlanApi.saveCarePlanDraft).toHaveBeenCalled(), { timeout: 2000 })
    const [, startDate, saved] = vi.mocked(carePlanApi.saveCarePlanDraft).mock.calls.at(-1)!
    expect(startDate).toBe('2026-11-02')
    expect(saved).toEqual([
      { groupName: 'Personal care', activityCode: 'BATHING', name: 'Bathing assistance', visits: [], evidenceType: 'CHECKLIST' },
      { groupName: 'Health monitoring', activityCode: 'VITALS', name: 'Vital-sign check', visits: [], evidenceType: 'CHECKLIST' },
    ])
  })

  it('fills in a first draft that was saved with nothing in it', async () => {
    vi.mocked(carePlanApi.fetchLatestCarePlan).mockResolvedValue({ ...draftV5, version: 1, supersedesPlanId: null })
    vi.mocked(carePlanApi.fetchCarePlanNodes).mockResolvedValue([])
    renderPlan()
    expect(await screen.findByRole('button', { name: 'Personal care' })).toBeInTheDocument()
    expect(taskRow('Vital-sign check')).toBeInTheDocument()
  })

  it('still counts a renamed task as the activity the family asked for', async () => {
    const user = userEvent.setup()
    renderPlan()
    await screen.findByRole('button', { name: 'Personal care' })
    await user.click(within(taskRow('Bathing assistance')).getByRole('button', { name: 'Edit task' }))
    await user.clear(screen.getByLabelText('Name'))
    await user.type(screen.getByLabelText('Name'), 'Shower help')
    await user.click(screen.getByRole('button', { name: 'Monday' }))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    const rail = within(screen.getByText('Requested by family').parentElement!)
    expect(rail.getByText('Bathing assistance').parentElement).toHaveTextContent('In plan')
  })
})

it('names what the family asked for that a version leaves out, before publishing', async () => {
  vi.mocked(profileApi.fetchElderCareRequests).mockResolvedValue([
    { source: 'INTAKE', applicationId: 3, careNeeds: ['GROOMING', 'BATHING', 'Help with the cat'], notes: null, submittedAt: '2026-09-16T09:00:00', outcome: 'SUBMITTED', needs: [], declineReason: null },
  ])
  const user = await renderPlanInDraft()
  await user.click(screen.getByRole('button', { name: 'Publish v5' }))

  const dialog = screen.getByRole('dialog')
  expect(dialog).toHaveTextContent('The family asked for Bathing assistance, which is not in this version. You can still publish.')
  expect(within(dialog).getByRole('button', { name: 'Publish' })).toBeEnabled()
})

describe('answering the family', () => {
  it('declines an open service application with a reason for the family', async () => {
    vi.mocked(profileApi.fetchElderCareRequests).mockResolvedValue([
      { source: 'SERVICE_APPLICATION', applicationId: 9, careNeeds: ['COMPANIONSHIP_WALK'], notes: null, submittedAt: '2026-10-01T09:00:00', outcome: 'SUBMITTED', needs: [], declineReason: null },
    ])
    const decline = vi.spyOn(profileApi, 'declineServiceApplication').mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderPlan()
    const application = await screen.findByRole('region', { name: 'Service application #9' })
    expect(within(application).getByText('Open')).toBeInTheDocument()

    await user.click(within(application).getByRole('button', { name: 'Decline' }))
    const dialog = screen.getByRole('dialog', { name: 'Decline service application #9?' })
    const confirm = within(dialog).getByRole('button', { name: 'Decline' })
    expect(confirm).toBeDisabled()
    await user.type(within(dialog).getByLabelText('Reason for the family'), 'No walking companion in your sector yet')
    vi.mocked(profileApi.fetchElderCareRequests).mockResolvedValue([
      { source: 'SERVICE_APPLICATION', applicationId: 9, careNeeds: ['COMPANIONSHIP_WALK'], notes: null, submittedAt: '2026-10-01T09:00:00', outcome: 'DECLINED', needs: [], declineReason: 'No walking companion in your sector yet' },
    ])
    await user.click(confirm)

    expect(decline).toHaveBeenCalledWith(9, 'No walking companion in your sector yet')
    expect(await screen.findByText('Declined: No walking companion in your sector yet')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Service application #9' })).queryByRole('button', { name: 'Decline' })).not.toBeInTheDocument()
  })

  it('shows an application the plan has answered as planned, with nothing to decline', async () => {
    vi.mocked(profileApi.fetchElderCareRequests).mockResolvedValue([
      { source: 'SERVICE_APPLICATION', applicationId: 9, careNeeds: ['GROOMING'], notes: null, submittedAt: '2026-10-01T09:00:00', outcome: 'PLANNED', needs: [{ need: 'GROOMING', plannedVersion: 4, plannedFrom: '2024-04-11' }], declineReason: null },
    ])
    renderPlan()
    const application = await screen.findByRole('region', { name: 'Service application #9' })
    expect(within(application).getByText('Planned')).toBeInTheDocument()
    expect(within(application).queryByRole('button', { name: 'Decline' })).not.toBeInTheDocument()
  })
})
