import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import WorkSettingsPage from './WorkSettingsPage'
import type { WorkSettingsView } from '../../features/caregiver/workSettings'

const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Singapore', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const initial: WorkSettingsView = {
  settings: { caregiverId: 1, version: 0, updatedAt: null, dateFrom: date, dateTo: date,
    preferences: { serviceCodes: [], sectors: [], weeklyWindows: [], maxVisitsPerDay: null, maxHoursPerDay: null },
    availability: [], dataIssues: [], timeZone: 'Asia/Singapore', uncoveredTimeMeaning: 'UNSPECIFIED', consumptionStatus: 'NOT_APPLIED_TO_ROSTERING' },
  catalog: { serviceCodes: [], sectors: ['01', 'North'], maxVisitsPerDay: 8, maxHoursPerDay: 8, serviceCatalogConfigured: false },
  today: date, editableUntil: '2099-12-31', bookings: [], leave: [], canEdit: true,
}
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
let view: WorkSettingsView
let write: (body: Record<string, unknown>) => Promise<Response>
beforeEach(() => {
  view = structuredClone(initial)
  write = async () => reply({ ...view.settings, version: 1 })
  vi.stubGlobal('fetch', vi.fn((_url: string, options?: RequestInit) => options?.method === 'PUT' ? write(JSON.parse(String(options.body))) : Promise.resolve(reply(view))))
  vi.spyOn(window, 'confirm').mockReturnValue(true)
})
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })
const mount = (mode: 'availability' | 'preferences' = 'availability') => render(<MemoryRouter><WorkSettingsPage mode={mode} /></MemoryRouter>)

it('distinguishes no declaration, soft preference and actual roster, and shows assigned visits and approved leave', async () => {
  view.bookings = [{ visitId: 9, start: date + 'T09:00:00', end: date + 'T10:00:00' }]
  view.leave = [{ id: 1, startDate: date, endDate: date, status: 'APPROVED' }]
  mount()
  expect(await screen.findByText('Unspecified — no windows declared.')).toBeInTheDocument()
  expect(screen.getByText(/Availability and preferences are declarations/)).toBeInTheDocument()
  expect(screen.getByText(/Visit 9/)).toBeInTheDocument()
  expect(screen.getByText(new RegExp(date + '–' + date + ' · APPROVED'))).toBeInTheDocument()
})
it('saves structured windows with a version and retains drafts across focus', async () => {
  mount(); fireEvent.click(await screen.findByRole('button', { name: 'Edit availability' }))
  fireEvent.click(screen.getByRole('button', { name: 'Add window' }))
  fireEvent.change(screen.getByLabelText('Status 1'), { target: { value: 'UNAVAILABLE' } })
  fireEvent.change(screen.getByLabelText('Start time 1'), { target: { value: '10:00' } })
  act(() => window.dispatchEvent(new Event('focus')))
  expect(screen.getByLabelText('Start time 1')).toHaveValue('10:00')
  fireEvent.click(screen.getByRole('button', { name: 'Save settings' }))
  await screen.findByText(/Settings saved/)
  const call = vi.mocked(fetch).mock.calls.find(([, options]) => options?.method === 'PUT')!
  expect(JSON.parse(String(call[1]?.body))).toMatchObject({ windows: [{ start: '10:00', end: '12:00', status: 'UNAVAILABLE' }], expectedSettingsVersion: 0 })
})
it('does not turn a read failure into an empty editable day', async () => {
  vi.mocked(fetch).mockResolvedValue(reply({}, 503)); mount()
  await screen.findByRole('alert')
  expect(screen.queryByRole('button', { name: 'Edit availability' })).not.toBeInTheDocument()
  expect(screen.queryByText('Unspecified — no windows declared.')).not.toBeInTheDocument()
})
it('retains the exact payload and key after an unknown save result', async () => {
  write = async () => { throw new TypeError('offline') }
  mount(); fireEvent.click(await screen.findByRole('button', { name: 'Edit availability' }))
  fireEvent.click(screen.getByRole('button', { name: 'Add window' }))
  fireEvent.click(screen.getByRole('button', { name: 'Save settings' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Retry unchanged request' }))
  await waitFor(() => expect(vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'PUT')).toHaveLength(2))
  const calls = vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'PUT')
  expect(calls[0][1]?.body).toEqual(calls[1][1]?.body)
  expect(screen.getByLabelText('Start time 1')).toBeDisabled()
})
it('blocks stale-version resubmission until the editor is closed and refreshed', async () => {
  write = async () => reply({ detail: 'Settings changed. Refresh and review.', code: 'WORK_SETTINGS_VERSION_CONFLICT' }, 409)
  mount(); fireEvent.click(await screen.findByRole('button', { name: 'Edit availability' }))
  fireEvent.click(screen.getByRole('button', { name: 'Save settings' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Settings changed')
  expect(screen.getByRole('button', { name: 'Save settings' })).toBeDisabled()
  fireEvent.click(screen.getByRole('button', { name: 'Close editor' }))
  expect(screen.getByRole('button', { name: 'Refresh' })).toBeEnabled()
})
it('saves soft preferences without inventing a service directory and preserves area zeroes', async () => {
  mount('preferences'); fireEvent.click(await screen.findByRole('button', { name: 'Edit preferences' }))
  expect(screen.getByText(/Service choices await an agreed/)).toBeInTheDocument()
  fireEvent.click(screen.getByLabelText('01'))
  fireEvent.click(screen.getByRole('button', { name: 'Add preferred window' }))
  fireEvent.change(screen.getByLabelText('Preferred daily hours'), { target: { value: '4.5' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save settings' }))
  await screen.findByText(/Settings saved/)
  const call = vi.mocked(fetch).mock.calls.find(([, options]) => options?.method === 'PUT')!
  expect(JSON.parse(String(call[1]?.body)).preferences).toEqual({ serviceCodes: [], sectors: ['01'], weeklyWindows: [{ weekday: 'MONDAY', start: '09:00', end: '12:00' }], maxVisitsPerDay: null, maxHoursPerDay: 4.5 })
})
it('warns about legacy issues and requires explicit confirmation before replacement', async () => {
  view.settings.dataIssues = ['LEGACY_PREFERENCES_REQUIRE_REVIEW']
  view.settings.preferences.serviceCodes = ['OLD_CODE']
  mount('preferences'); fireEvent.click(await screen.findByRole('button', { name: 'Edit preferences' }))
  expect(screen.getByLabelText(/OLD_CODE/)).toBeChecked()
  fireEvent.click(screen.getByRole('button', { name: 'Save settings' }))
  expect(screen.getAllByRole('alert').some(el => el.textContent?.includes('Confirm that you reviewed'))).toBe(true)
  expect(vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'PUT')).toHaveLength(0)
})
