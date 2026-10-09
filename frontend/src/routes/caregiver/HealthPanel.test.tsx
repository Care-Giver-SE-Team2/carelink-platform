import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import WorkPackPage from './WorkPackPage'
import { getWorkPack, type WorkPack } from '../../features/caregiver/api'
import { checkIn, completeTask, getHealthRecords, saveHealthRecord, type HealthRecord, type HealthResult } from '../../features/caregiver-execution/api'
import { ApiError } from '../../shared/api/client'

vi.mock('../../features/caregiver/api', () => ({ getWorkPack: vi.fn() }))
vi.mock('../../features/caregiver-execution/api', () => ({ checkIn: vi.fn(), completeTask: vi.fn(), getHealthRecords: vi.fn(), saveHealthRecord: vi.fn() }))
const pack: WorkPack = {
  visit: { id: 3, elderId: 4, elderName: 'Mei', serviceType: 'Care', scheduledStart: '2026-10-09T12:00:00', scheduledEnd: '2026-10-09T13:00:00', status: 'IN_PROGRESS', version: 1 },
  elder: { elderId: 4, preferredName: 'Mei', serviceAddress: 'Private address', postalSector: 'North', languageNeeds: [], accessNotes: null, emergencyNotes: null },
  carePlanId: 7, carePlanVersion: 1, serviceInstructions: ['Care'], requiredEvidenceKinds: [],
  tasks: [{ id: 9, name: 'Care', status: 'PENDING', outcome: null, caregiverNote: null }],
  healthObservation: { healthFlag: null, healthNote: null },
  execution: { allowedActions: ['TASK_RESULT', 'HEALTH_RECORD'], blockedReason: null, serverNow: '2026-10-09T12:00:00', checkInOpensAt: '2026-10-09T11:30:00', checkInClosesAt: '2026-10-09T13:00:00', checkedInAt: '2026-10-09T12:00:00', checkedOutAt: null, lateArrival: false, locationSource: 'MANUAL_LOCATION_NOTE' },
}
const record: HealthRecord = { id: 8, visitId: 3, healthFlag: 'ATTENTION', healthNote: 'Private observation', recordedAt: '2026-10-09T12:05:00', readings: [
  { metric: 'systolic', value: 123, unit: 'mmHg' }, { metric: 'diastolic', value: 81, unit: 'mmHg' }, { metric: 'pulse', value: 73, unit: 'bpm' }, { metric: 'temperature', value: 36.7, unit: '°C' },
] }
function mount() { render(<MemoryRouter initialEntries={['/caregiver/visits/3']}><Routes><Route path="/caregiver/visits/:visitId" element={<WorkPackPage />} /></Routes></MemoryRouter>) }
async function form() { return within(await screen.findByRole('group', { name: 'Record vital signs and observation' })) }
async function fill() {
  const group = await form()
  for (const [label, value] of [['Systolic · mmHg', '123'], ['Diastolic · mmHg', '81'], ['Pulse · bpm', '73'], ['Temperature · °C', '36.7'], ['Health observation', 'ATTENTION'], ['Health note', 'Private observation']]) {
    fireEvent.change(group.getByLabelText(label), { target: { value } })
  }
  return group
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(getWorkPack).mockResolvedValue(structuredClone(pack))
  vi.mocked(getHealthRecords).mockResolvedValue({ items: [], page: 0, size: 10, total: 0 })
  vi.mocked(saveHealthRecord).mockResolvedValue({ record, visitVersion: 2, replayed: false })
})
it('saves four measured values and the explicit observation as one command, then uses the new parent version', async () => {
  mount(); const group = await fill()
  vi.mocked(getWorkPack).mockResolvedValue({ ...pack, visit: { ...pack.visit, version: 2 }, healthObservation: { healthFlag: 'ATTENTION', healthNote: record.healthNote } })
  vi.mocked(getHealthRecords).mockResolvedValue({ items: [record], page: 0, size: 10, total: 1 })
  fireEvent.click(group.getByRole('button', { name: 'Save health record' }))
  await screen.findByText('Systolic: 123 mmHg')
  expect(saveHealthRecord).toHaveBeenCalledWith(3, expect.objectContaining({ systolic: 123, diastolic: 81, pulse: 73, temperature: 36.7, healthFlag: 'ATTENTION', healthNote: record.healthNote, expectedVersion: 1, clientRequestId: expect.any(String) }), expect.any(AbortSignal))
  expect(screen.queryByRole('button', { name: 'Save health record' })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Record another measurement' }))
  expect((await form()).getByLabelText('Temperature · °C')).toHaveValue(null)
  expect((await form()).getByLabelText('Health observation')).toHaveValue('')
  fireEvent.click(screen.getByRole('button', { name: 'Save task result' }))
  await waitFor(() => expect(completeTask).toHaveBeenCalledWith(3, 9, expect.objectContaining({ expectedVersion: 2 }), expect.any(AbortSignal)))
  expect(checkIn).not.toHaveBeenCalled()
})
it('keeps a measurement draft over refresh and refuses half blood pressure or unexplained concern', async () => {
  mount(); const group = await form()
  fireEvent.change(group.getByLabelText('Health observation'), { target: { value: 'ATTENTION' } })
  fireEvent.change(group.getByLabelText('Systolic · mmHg'), { target: { value: '123' } })
  fireEvent.click(group.getByRole('button', { name: 'Save health record' }))
  await screen.findByText('Enter both blood pressure values or leave both blank.')
  fireEvent.click(screen.getByRole('button', { name: 'Refresh' })); await waitFor(() => expect(getWorkPack).toHaveBeenCalledTimes(2))
  const refreshed = await form(); expect(refreshed.getByLabelText('Systolic · mmHg')).toHaveValue(123)
  fireEvent.change(refreshed.getByLabelText('Diastolic · mmHg'), { target: { value: '81' } })
  fireEvent.click(refreshed.getByRole('button', { name: 'Save health record' }))
  await screen.findByText('Explain the concern or why no readings were measured.')
  expect(saveHealthRecord).not.toHaveBeenCalled()
})
it('retries an unknown result with the original key and payload and blocks task writes during reconciliation', async () => {
  vi.mocked(saveHealthRecord).mockRejectedValue(new TypeError('Network'))
  mount(); const group = await fill(); fireEvent.click(group.getByRole('button', { name: 'Save health record' }))
  await screen.findByText(/Result unknown/)
  expect(screen.getByRole('group', { name: 'Record result · Care' })).toBeDisabled()
  fireEvent.click(screen.getByRole('button', { name: 'Refresh' })); await waitFor(() => expect(getWorkPack).toHaveBeenCalledTimes(2))
  expect(saveHealthRecord).toHaveBeenCalledTimes(1)
  fireEvent.click(await screen.findByRole('button', { name: 'Retry same request' }))
  await waitFor(() => expect(saveHealthRecord).toHaveBeenCalledTimes(2))
  expect(vi.mocked(saveHealthRecord).mock.calls[0][1]).toEqual(vi.mocked(saveHealthRecord).mock.calls[1][1])
})
it('requires explicit review after a version conflict, preserves the draft, and never silently resubmits', async () => {
  vi.mocked(saveHealthRecord).mockRejectedValue(new ApiError('Conflict', 409, { code: 'VISIT_VERSION_CONFLICT' }))
  mount(); const group = await fill(); fireEvent.click(group.getByRole('button', { name: 'Save health record' }))
  await screen.findByText(/The visit or task has changed/)
  vi.mocked(getWorkPack).mockResolvedValue({ ...pack, visit: { ...pack.visit, version: 2 } })
  fireEvent.click(screen.getByRole('button', { name: 'Refresh visit and edit' }))
  const refreshed = await form(); expect(refreshed.getByLabelText('Health note')).toHaveValue(record.healthNote)
  expect(saveHealthRecord).toHaveBeenCalledTimes(1)
  fireEvent.click(refreshed.getByRole('button', { name: 'Save health record' }))
  await waitFor(() => expect(saveHealthRecord).toHaveBeenCalledTimes(2))
  expect(vi.mocked(saveHealthRecord).mock.calls[1][1].expectedVersion).toBe(2)
  expect(vi.mocked(saveHealthRecord).mock.calls[1][1].clientRequestId).not.toBe(vi.mocked(saveHealthRecord).mock.calls[0][1].clientRequestId)
})
it('clears private records and drafts after losing write or history access', async () => {
  vi.mocked(saveHealthRecord).mockRejectedValue(new ApiError('Denied', 403, null))
  mount(); const group = await fill(); fireEvent.click(group.getByRole('button', { name: 'Save health record' }))
  await screen.findByText('Access not permitted')
  expect(screen.queryByText('Private address')).toBeNull(); expect(screen.queryByDisplayValue(record.healthNote!)).toBeNull()
})
it('clears the whole work pack if history access is denied and hides entry controls after an exception', async () => {
  vi.mocked(getHealthRecords).mockRejectedValue(new ApiError('Denied', 403, null))
  mount(); await screen.findByText('Access not permitted'); expect(screen.queryByText('Private address')).toBeNull()
})
it('reads paged health history safely as text and does not permit new entries on paused work', async () => {
  vi.mocked(getWorkPack).mockResolvedValue({ ...pack, visit: { ...pack.visit, status: 'EXCEPTION' }, execution: { ...pack.execution!, allowedActions: [] }, healthObservation: { healthFlag: 'ATTENTION', healthNote: '<script>alert(1)</script>' } })
  vi.mocked(getHealthRecords).mockResolvedValue({ items: [record], page: 0, size: 10, total: 11 })
  mount(); await screen.findByText('Systolic: 123 mmHg')
  expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Save health record' })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Next health records' }))
  await waitFor(() => expect(getHealthRecords).toHaveBeenLastCalledWith(3, 1, expect.any(AbortSignal)))
})
it('holds all write controls while a health command is in flight', async () => {
  let finish!: (value: HealthResult) => void
  vi.mocked(saveHealthRecord).mockImplementation(() => new Promise(resolve => { finish = resolve }))
  mount(); const group = await fill(); fireEvent.click(group.getByRole('button', { name: 'Save health record' }))
  await waitFor(() => expect(group.getByRole('button', { name: 'Save health record' })).toBeDisabled())
  expect(screen.getByRole('group', { name: 'Record result · Care' })).toBeDisabled()
  finish({ record, visitVersion: 2, replayed: false })
  await waitFor(() => expect(getWorkPack).toHaveBeenCalledTimes(2))
})
