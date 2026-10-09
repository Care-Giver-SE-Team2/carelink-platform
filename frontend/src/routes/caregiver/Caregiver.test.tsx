import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import CaregiverHome from './index'
import type { Schedule, WorkPack } from '../../features/caregiver/api'

const day = '2026-09-24'
const visit = { id: 41, elderId: 7, elderName: 'Demo Elder Mei', serviceType: 'MORNING_CARE',
  scheduledStart: day + 'T09:00:00', scheduledEnd: day + 'T10:00:00', status: 'SCHEDULED', version: 0 }
const schedule: Schedule = { dateFrom: day, dateTo: day, timeZone: 'Asia/Singapore', upcomingVisits: [visit],
  certificationAlerts: [{ id: 1, name: 'First aid', certificateNo: null, expiryDate: '2026-09-23', status: 'EXPIRED', warning: 'EXPIRED' }] }
const pack: WorkPack = { visit, elder: { elderId: 7, preferredName: 'Demo Elder Mei', serviceAddress: 'Demo address',
  postalSector: 'North', languageNeeds: ['English'], accessNotes: null, emergencyNotes: null },
  carePlanId: 10, carePlanVersion: 1, serviceInstructions: ['Hygiene'],
  tasks: [{ id: 1, name: 'Hygiene', status: 'PENDING', outcome: null, caregiverNote: null }], requiredEvidenceKinds: ['CHECKLIST'] }
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
let scheduleResponse: () => Promise<Response>
let packResponse: () => Promise<Response>
let profileResponse: () => Promise<Response>

beforeEach(() => {
  scheduleResponse = async () => reply(schedule)
  packResponse = async () => reply(pack)
  profileResponse = async () => reply({ id: 1, fullName: 'Caregiver A' })
  vi.stubGlobal('fetch', vi.fn((url: string) => {
    if (url === '/api/auth/me') return Promise.resolve(reply({ username: 'a', displayName: 'Caregiver A', roles: ['CAREGIVER'] }))
    if (url === '/api/auth/csrf') return Promise.resolve(new Response(null, { status: 200 }))
    if (url === '/api/caregivers/me') return profileResponse()
    if (url.startsWith('/api/caregivers/me/schedule')) return scheduleResponse()
    if (url.endsWith('/work-pack')) return packResponse()
    if (url === '/api/auth/logout') return Promise.resolve(new Response(null, { status: 204 }))
    throw new Error('Unexpected URL: ' + url)
  }))
})
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })
function SwitchVisit() {
  const navigate = useNavigate()
  return <button onClick={() => navigate('/caregiver/visits/42')}>Other visit</button>
}
function mount(path = '/caregiver?dateFrom=' + day + '&dateTo=' + day) {
  return render(<MemoryRouter initialEntries={[path]}><SwitchVisit /><Routes>
    <Route path="/caregiver/*" element={<CaregiverHome />} />
    <Route path="/" element={<h1>Sign in</h1>} />
  </Routes></MemoryRouter>)
}
describe('caregiver read workflow', () => {
  it('shows assigned cards and alerts; work pack keeps the return date range', async () => {
    mount()
    expect(await screen.findByText('Demo Elder Mei')).toBeInTheDocument()
    expect(screen.getByText('First aid · Expired')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: /View work pack/ }))
    expect(await screen.findByText('Version 1')).toBeInTheDocument()
    expect(screen.getByText(/Execution controls are unavailable/)).toBeInTheDocument()
    expect(screen.getByText('Checklist')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '← My schedule' })).toHaveAttribute('href', '/caregiver?dateFrom=' + day + '&dateTo=' + day)
    expect(screen.queryByRole('button', { name: /Check.in|Submit/ })).not.toBeInTheDocument()
  })
  it('opens a work pack directly after profile validation', async () => {
    mount('/caregiver/visits/41')
    expect(await screen.findByText('Version 1')).toBeInTheDocument()
    expect(screen.getByText('Demo address')).toBeInTheDocument()
  })
  it('shows a genuine empty schedule', async () => {
    scheduleResponse = async () => reply({ ...schedule, upcomingVisits: [] })
    mount()
    expect(await screen.findByText('No assigned visits in this period')).toBeInTheDocument()
  })
  it('rejects a reversed or excessively long date range without a new query', async () => {
    mount()
    await screen.findByText('Demo Elder Mei')
    const calls = vi.mocked(fetch).mock.calls.length
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-09-01' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply dates' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Choose both dates')
    expect(vi.mocked(fetch).mock.calls).toHaveLength(calls)
  })
  it('clears the previous work pack immediately during retry and failure', async () => {
    mount('/caregiver/visits/41')
    await screen.findByText('Demo address')
    packResponse = async () => reply({}, 503)
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(screen.queryByText('Demo address')).not.toBeInTheDocument()
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load')
    packResponse = async () => reply(pack)
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('Demo address')).toBeInTheDocument()
  })
  it('does not reuse previous visit data on 403 after changing visit id', async () => {
    mount('/caregiver/visits/41')
    await screen.findByText('Demo address')
    packResponse = async () => reply({}, 403)
    fireEvent.click(screen.getByRole('button', { name: 'Other visit' }))
    expect(screen.queryByText('Demo address')).not.toBeInTheDocument()
    expect(await screen.findByRole('alert')).toHaveTextContent('Access not permitted')
  })
  it('redirects an expired session and removes protected details', async () => {
    mount('/caregiver/visits/41')
    await screen.findByText('Demo address')
    packResponse = async () => reply({}, 401)
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByText('Demo address')).not.toBeInTheDocument()
  })
  it('shows missing profile and never requests clinical data', async () => {
    profileResponse = async () => reply({}, 404)
    mount()
    expect(await screen.findByRole('alert')).toHaveTextContent('Caregiver profile not linked')
    expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url).includes('/schedule'))).toBe(false)
  })
  it('ignores a late response from the previously viewed visit', async () => {
    let complete!: (response: Response) => void
    packResponse = () => new Promise(resolve => { complete = resolve })
    mount('/caregiver/visits/41')
    await waitFor(() => expect(complete).toBeDefined())
    packResponse = async () => reply({}, 404)
    fireEvent.click(screen.getByRole('button', { name: 'Other visit' }))
    await screen.findByText('Visit not found')
    await act(async () => complete(reply(pack)))
    expect(screen.queryByText('Demo address')).not.toBeInTheDocument()
  })
  it('sign out removes the work pack', async () => {
    mount('/caregiver/visits/41')
    await screen.findByText('Demo address')
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByText('Demo address')).not.toBeInTheDocument()
  })
  it('keeps cancelled summaries but offers no work pack link', async () => {
    scheduleResponse = async () => reply({ ...schedule, upcomingVisits: [{ ...visit, status: 'CANCELLED' }] })
    mount()
    expect(await screen.findByText('Cancelled — summary only. Work pack unavailable.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /View work pack/ })).not.toBeInTheDocument()
    expect(screen.getByText(/Last successfully fetched:/)).toHaveTextContent(/not the roster modification time/)
  })
  it('shows changes only after a successful refresh in the same range', async () => {
    mount()
    await screen.findByText('Demo Elder Mei')
    expect(screen.queryByText('Schedule updated')).not.toBeInTheDocument()
    scheduleResponse = async () => reply({ ...schedule, upcomingVisits: [{ ...visit, scheduledStart: day + 'T11:00:00' }] })
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByText('Visit #41 time changed.')).toBeInTheDocument()
    scheduleResponse = async () => reply({ ...schedule, upcomingVisits: [] })
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-09-25' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply dates' }))
    await screen.findByText('No assigned visits in this period')
    expect(screen.queryByText('Schedule updated')).not.toBeInTheDocument()
  })
  it.each([
    [403, {}, 'Access not permitted'],
    [409, { code: 'VISIT_CANCELLED' }, 'Visit cancelled'],
    [409, { code: 'VISIT_TASK_PLAN_MISMATCH' }, 'Unable to load this page'],
  ])('clears old details on return with status %s and preserves the return range', async (status, body, heading) => {
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
    mount('/caregiver/visits/41?dateFrom=' + day + '&dateTo=' + day)
    await screen.findByText('Demo address')
    packResponse = async () => reply(body, status)
    fireEvent(window, new Event('focus'))
    expect(screen.queryByText('Demo address')).not.toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
    expect(within(screen.getByRole('alert')).getByRole('link', { name: 'My schedule' })).toHaveAttribute('href', '/caregiver?dateFrom=' + day + '&dateTo=' + day)
  })
  it('coalesces return events, ignores hidden events and refreshes after network recovery', async () => {
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
    const now = vi.spyOn(Date, 'now').mockReturnValue(1000)
    mount()
    await screen.findByText('Demo Elder Mei')
    const count = () => vi.mocked(fetch).mock.calls.filter(([url]) => String(url).includes('/schedule')).length
    expect(count()).toBe(1)
    visibility.mockReturnValue('hidden')
    fireEvent(document, new Event('visibilitychange'))
    fireEvent(window, new Event('focus'))
    expect(count()).toBe(1)
    visibility.mockReturnValue('visible')
    fireEvent(document, new Event('visibilitychange'))
    fireEvent(window, new Event('focus'))
    fireEvent(window, new Event('online'))
    await waitFor(() => expect(count()).toBe(2))
    await screen.findByText('Demo Elder Mei')
    now.mockReturnValue(1600)
    scheduleResponse = async () => reply({ ...schedule, upcomingVisits: [] })
    fireEvent(window, new Event('online'))
    expect(await screen.findByText(/1 visit moved out/)).toBeInTheDocument()
    expect(count()).toBe(3)
  })
  it('ignores an older refresh even if it finishes after a newer failure', async () => {
    mount('/caregiver/visits/41')
    await screen.findByText('Demo address')
    let complete!: (response: Response) => void
    packResponse = () => new Promise(resolve => { complete = resolve })
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await waitFor(() => expect(complete).toBeDefined())
    // Loading removes the page refresh button; returning to the page supersedes the pending request.
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
    vi.spyOn(Date, 'now').mockReturnValue(Date.now() + 1000)
    packResponse = async () => reply({}, 403)
    fireEvent(window, new Event('focus'))
    await screen.findByText('Access not permitted')
    await act(async () => complete(reply(pack)))
    expect(screen.queryByText('Demo address')).not.toBeInTheDocument()
  })
  it('removes return listeners on unmount', async () => {
    const view = mount()
    await screen.findByText('Demo Elder Mei')
    view.unmount()
    const count = vi.mocked(fetch).mock.calls.length
    fireEvent(window, new Event('focus'))
    fireEvent(window, new Event('online'))
    expect(vi.mocked(fetch).mock.calls).toHaveLength(count)
  })
})
