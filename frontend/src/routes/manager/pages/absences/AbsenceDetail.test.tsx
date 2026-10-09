import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import AbsenceDetail from './AbsenceDetail'
import AbsenceList from './AbsenceList'
import * as absencesApi from '../../../../features/absences/api'
import type { AbsenceCase } from '../../../../features/absences/types'
import * as authApi from '../../../../features/auth/api'
import * as incidentsApi from '../../../../features/incidents/api'
import * as profileApi from '../../../../shared/api/profile'
import * as rosteringApi from '../../../../shared/api/rostering'

/**
 * The manager's UC-MG04 screens against a stubbed API: what is on screen for an
 * absence part-way through, and what the buttons send.
 *
 * @author Wang Ziyu
 */

const awaiting: AbsenceCase = {
  absence: {
    id: 4,
    caregiverId: 5,
    caregiverName: 'Aisha',
    type: 'SICK',
    startDate: '2026-10-08',
    endDate: '2026-10-08',
    reason: 'flu',
    status: 'APPROVED',
    reviewedByUserId: 11,
    coverageConfirmedAt: null,
    notYetRerostered: 1,
    awaitingFamily: 1,
    uncovered: 1,
    settled: 0,
  },
  notYetRerostered: [
    { visitId: 33, elderId: 7, elderName: 'Mdm Tan', serviceType: 'Meals', start: '2026-10-08T17:00:00', end: null },
  ],
  changes: [
    {
      id: 100,
      visitId: 30,
      elderId: 7,
      elderName: 'Mdm Tan',
      visitStart: '2026-10-08T09:00:00',
      visitEnd: '2026-10-08T09:45:00',
      status: 'AWAITING_FAMILY',
      outcome: null,
      decidedBy: null,
      decidedAt: null,
      respondBy: '2026-10-07T11:00:00',
      note: null,
      absentCaregiver: { caregiverId: 5, name: 'Aisha' },
      proposedCaregiver: { caregiverId: 9, name: 'Farah' },
      assignedCaregiver: null,
      rescheduledVisitId: null,
      rescheduledStart: null,
      incidentId: null,
      rosteringRunId: 1,
      objective: 'CONTINUITY',
      managerMayAssign: false,
      candidates: [
        {
          caregiverId: 9,
          name: 'Farah',
          rank: 1,
          score: 81,
          reason: 'Has visited this elder 3 times before',
          outcome: 'SUGGESTED',
          excludedBy: null,
          checks: [{ code: 'CONTINUITY', name: 'Has cared for this elder before', kind: 'SOFT', result: 'PASS', detail: '3 earlier visits to this elder' }],
        },
        {
          caregiverId: 5,
          name: 'Aisha',
          rank: null,
          score: null,
          reason: 'On leave 2026-10-08 to 2026-10-08',
          outcome: 'EXCLUDED',
          excludedBy: 'NOT_ON_LEAVE',
          checks: [{ code: 'NOT_ON_LEAVE', name: 'Not on approved leave that day', kind: 'HARD', result: 'FAIL', detail: 'On leave 2026-10-08 to 2026-10-08' }],
        },
      ],
    },
    {
      id: 101,
      visitId: 32,
      elderId: 8,
      elderName: 'Mr Ong',
      visitStart: '2026-10-08T14:00:00',
      visitEnd: null,
      status: 'UNCOVERED',
      outcome: null,
      decidedBy: null,
      decidedAt: null,
      respondBy: null,
      note: 'Nobody was free to cover this visit',
      absentCaregiver: { caregiverId: 5, name: 'Aisha' },
      proposedCaregiver: null,
      assignedCaregiver: null,
      rescheduledVisitId: null,
      rescheduledStart: null,
      incidentId: 900,
      rosteringRunId: 1,
      objective: 'CONTINUITY',
      managerMayAssign: false,
      candidates: [],
    },
  ],
}

beforeEach(() => {
  vi.spyOn(incidentsApi, 'listIncidentQueue').mockImplementation(async ({ size }) => ({ items: [], page: 0, size, totalElements: 0 }))
  vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue({ id: 11, username: 'alice', displayName: 'Alice Tan', roles: ['MANAGER'] })
  vi.spyOn(profileApi, 'fetchIntakeReviews').mockResolvedValue([])
  vi.spyOn(profileApi, 'fetchCredentialRegister').mockResolvedValue([])
  vi.spyOn(rosteringApi, 'fetchVisitsAtRisk').mockResolvedValue([])
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/manager/absences" element={<AbsenceList />} />
          <Route path="/manager/absences/:id" element={<AbsenceDetail />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

it('shows each vacated visit with its candidates and why the excluded were excluded', async () => {
  vi.spyOn(absencesApi, 'getAbsence').mockResolvedValue(awaiting)

  renderAt('/manager/absences/4')

  expect(await screen.findByRole('heading', { name: 'Aisha — 8 Oct' })).toBeInTheDocument()
  const visits = screen.getByRole('region', { name: 'Vacated visits' })
  expect(within(visits).getByText(/Suggested Farah/)).toBeInTheDocument()
  expect(within(visits).getByText('Excluded: On leave 2026-10-08 to 2026-10-08')).toBeInTheDocument()
  expect(within(visits).getByRole('link', { name: 'Exception EXC-900' })).toHaveAttribute('href', '/manager/exceptions/900')
  expect(screen.getByRole('region', { name: 'Not yet re-rostered' })).toHaveTextContent('Mdm Tan · Meals')
  expect(screen.getByRole('button', { name: 'Confirm coverage' })).toBeDisabled()
})

it('re-rosters with the chosen objective and says what it did', async () => {
  vi.spyOn(absencesApi, 'getAbsence').mockResolvedValue(awaiting)
  const reroster = vi.spyOn(absencesApi, 'rerosterAbsence').mockResolvedValue({
    outcome: { absenceId: 4, runId: 2, searched: 1, offered: 1, settled: 0, uncovered: 0 },
    absence: awaiting,
  })

  renderAt('/manager/absences/4')
  await screen.findByRole('heading', { name: 'Aisha — 8 Oct' })
  await userEvent.selectOptions(screen.getByLabelText('Rank replacements by'), 'EVEN_WORKLOAD')
  await userEvent.click(screen.getByRole('button', { name: 'Re-roster now' }))

  expect(reroster).toHaveBeenCalledWith(4, 'EVEN_WORKLOAD')
  expect(await screen.findByText('Searched 1: 1 offered to families, 0 settled at once, 0 uncovered.')).toBeInTheDocument()
})

it('shows the server sentence when coverage cannot be confirmed yet', async () => {
  const settled: AbsenceCase = {
    ...awaiting,
    absence: { ...awaiting.absence, notYetRerostered: 0, awaitingFamily: 0, uncovered: 0, settled: 1 },
    notYetRerostered: [],
    changes: [{ ...awaiting.changes[0], status: 'RESOLVED', outcome: 'REPLACED', decidedBy: 'DEFAULT_PLAN', assignedCaregiver: { caregiverId: 9, name: 'Farah' } }],
  }
  vi.spyOn(absencesApi, 'getAbsence').mockResolvedValue(settled)
  vi.spyOn(absencesApi, 'confirmCoverage').mockRejectedValue(new Error('offline'))

  renderAt('/manager/absences/4')
  expect(await screen.findByText('Another caregiver (Farah), decided by the default plan')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Confirm coverage' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('The request could not be completed')
})

it('lets the manager hand-pick over the default plan, and only where they may', async () => {
  const settled: AbsenceCase = {
    ...awaiting,
    changes: [
      {
        ...awaiting.changes[0],
        status: 'RESOLVED',
        outcome: 'REPLACED',
        decidedBy: 'DEFAULT_PLAN',
        assignedCaregiver: { caregiverId: 9, name: 'Farah' },
        managerMayAssign: true,
        candidates: [
          { ...awaiting.changes[0].candidates[0], outcome: 'SELECTED' },
          { ...awaiting.changes[0].candidates[0], caregiverId: 10, name: 'Siti', rank: 2 },
          awaiting.changes[0].candidates[1],
        ],
      },
      awaiting.changes[1],
    ],
  }
  vi.spyOn(absencesApi, 'getAbsence').mockResolvedValue(settled)
  const assign = vi.spyOn(absencesApi, 'assignCaregiver').mockResolvedValue(settled)

  renderAt('/manager/absences/4')
  await screen.findByRole('heading', { name: 'Aisha — 8 Oct' })

  expect(screen.getAllByRole('button', { name: /^Assign / })).toHaveLength(1)
  expect(screen.queryByRole('button', { name: 'Assign Farah' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Assign Aisha' })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Assign Siti' }))
  expect(assign).toHaveBeenCalledWith(4, 100, 10)
})

it('lists absences and approves one a caregiver asked for', async () => {
  vi.spyOn(absencesApi, 'listAbsences').mockResolvedValue([
    { ...awaiting.absence, id: 5, status: 'PENDING', caregiverName: 'Siti', type: 'ANNUAL' },
    { ...awaiting.absence },
  ])
  const approve = vi.spyOn(absencesApi, 'approveAbsence').mockResolvedValue(awaiting.absence)

  renderAt('/manager/absences')

  const table = await screen.findByRole('table')
  expect(within(table).getByText('1 to re-roster · 1 waiting for families · 1 uncovered')).toBeInTheDocument()
  await userEvent.click(within(table).getByRole('button', { name: 'Approve' }))
  expect(approve).toHaveBeenCalledWith(5)
})

it('records an absence a caregiver rang in, then opens it', async () => {
  vi.spyOn(absencesApi, 'listAbsences').mockResolvedValue([])
  vi.spyOn(profileApi, 'fetchCaregivers').mockResolvedValue([
    { id: 5, fullName: 'Aisha', sector: null, status: 'AVAILABLE', assignable: true },
    { id: 6, fullName: 'Gone', sector: null, status: 'INACTIVE', assignable: false },
  ])
  const record = vi.spyOn(absencesApi, 'recordAbsence').mockResolvedValue(awaiting.absence)
  vi.spyOn(absencesApi, 'getAbsence').mockResolvedValue(awaiting)

  renderAt('/manager/absences')
  expect(await screen.findByText(/No absences/)).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Record an absence' }))
  const form = screen.getByRole('form', { name: 'Record an absence' })
  await within(form).findByRole('option', { name: 'Aisha' })
  expect(within(form).queryByRole('option', { name: 'Gone' })).not.toBeInTheDocument()
  await userEvent.selectOptions(within(form).getByLabelText('Caregiver'), '5')
  await userEvent.click(within(form).getByRole('button', { name: 'Record and open' }))

  expect(record).toHaveBeenCalledWith(expect.objectContaining({ caregiverId: 5, type: 'SICK' }))
  expect(await screen.findByRole('heading', { name: 'Aisha — 8 Oct' })).toBeInTheDocument()
})
