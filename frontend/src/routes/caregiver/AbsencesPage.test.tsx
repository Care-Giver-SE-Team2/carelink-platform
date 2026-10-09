import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { ReactNode } from 'react'
import CaregiverHome from './index'
import { todayInSingapore, addDays } from './format'

vi.mock('../../shared/components/RoleShell', () => ({ RoleShell: ({ children }: { children: ReactNode }) => children }))
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const today = todayInSingapore()
let records: unknown[]
let read: () => Promise<Response>
let write: () => Promise<Response>
beforeEach(() => {
  records = []
  read = async () => reply(records)
  write = async () => { records = [{ id: 1, type: 'ANNUAL', startDate: today, endDate: today, reason: 'Family time', status: 'PENDING' }]; return reply(records[0], 201) }
  vi.stubGlobal('fetch', vi.fn((url: string, options?: RequestInit) => {
    if (url === '/api/caregivers/me') return Promise.resolve(reply({ id: 1, userId: 10, fullName: 'Caregiver A' }))
    if (url === '/api/auth/csrf') return Promise.resolve(new Response(null, { status: 200 }))
    if (url === '/api/caregivers/me/absences') return options?.method === 'POST' ? write() : read()
    if (url.startsWith('/api/caregivers/me/schedule')) return Promise.resolve(reply({ upcomingVisits: [], certificationAlerts: [], dateFrom: today, dateTo: today, timeZone: 'Asia/Singapore' }))
    throw new Error('Unexpected URL: ' + url)
  }))
})
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })
function mount() {
  return render(<MemoryRouter initialEntries={['/caregiver/absences']}><Routes><Route path="/caregiver/*" element={<CaregiverHome />} /><Route path="/" element={<h1>Sign in</h1>} /></Routes></MemoryRouter>)
}
async function fill(start = today, end = today) {
  await screen.findByRole('heading', { name: 'My leave' })
  fireEvent.change(screen.getByLabelText('Start date'), { target: { value: start } })
  fireEvent.change(screen.getByLabelText('End date'), { target: { value: end } })
  fireEvent.change(screen.getByLabelText('Reason (optional)'), { target: { value: 'Family time' } })
}
describe('caregiver leave self-service', () => {
  it('uses fixed-English date controls for both leave dates', async () => {
    mount(); await screen.findByRole('heading', { name: 'My leave' })
    for (const label of ['Start date', 'End date']) {
      expect(screen.getByLabelText(label)).toHaveAttribute('placeholder', 'YYYY-MM-DD')
      expect(screen.getByLabelText(label)).toHaveAttribute('type', 'text')
      expect(screen.getByRole('button', { name: 'Choose ' + label.toLowerCase() })).toBeInTheDocument()
    }
  })
  it('rejects impossible manually typed dates without posting', async () => {
    mount(); await fill('2026-02-31', today)
    fireEvent.submit(screen.getByRole('form', { name: 'Request leave' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Choose valid dates in YYYY-MM-DD format')
    expect(vi.mocked(fetch).mock.calls.some(([, options]) => options?.method === 'POST')).toBe(false)
  })
  it('opens directly, shows genuine empty state and posts only the own request contract', async () => {
    mount(); await screen.findByText('No leave requests yet.'); await fill()
    fireEvent.click(screen.getByRole('button', { name: 'Submit leave request' }))
    await screen.findByText(/Leave request submitted/)
    const request = await screen.findByRole('article', { name: 'Leave request 1' })
    expect(within(request).getByText('Pending')).toBeInTheDocument()
    const posts = vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'POST')
    expect(posts).toHaveLength(1)
    expect(JSON.parse(String(posts[0][1]?.body))).toEqual({ type: 'ANNUAL', startDate: today, endDate: today, reason: 'Family time' })
    expect(screen.getByLabelText('Reason (optional)')).toHaveValue('')
    expect(screen.getByText(/arranges any roster changes separately/)).toBeInTheDocument()
  })
  it('allows an ongoing absence and an optional blank reason', async () => {
    mount(); await fill(addDays(today, -1), today)
    fireEvent.change(screen.getByLabelText('Reason (optional)'), { target: { value: '' } })
    fireEvent.click(screen.getByRole('button', { name: 'Submit leave request' }))
    await screen.findByText(/Leave request submitted/)
    const post = vi.mocked(fetch).mock.calls.find(([, options]) => options?.method === 'POST')!
    expect(JSON.parse(String(post[1]?.body))).not.toHaveProperty('reason')
  })
  it.each([[today, addDays(today, -1)], [addDays(today, -2), addDays(today, -1)]])('rejects invalid or fully past dates %s to %s', async (start, end) => {
    mount(); await fill(start, end)
    fireEvent.click(screen.getByRole('button', { name: 'Submit leave request' }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(vi.mocked(fetch).mock.calls.some(([, options]) => options?.method === 'POST')).toBe(false)
  })
  it('keeps a rejected draft and explains overlap without displaying server diagnostics', async () => {
    write = async () => reply({ code: 'ABSENCE_OVERLAPS', detail: 'internal-secret-stack' }, 409)
    mount(); await fill(); fireEvent.click(screen.getByRole('button', { name: 'Submit leave request' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('overlap')
    expect(screen.getByLabelText('Reason (optional)')).toHaveValue('Family time')
    expect(screen.queryByText('internal-secret-stack')).not.toBeInTheDocument()
  })
  it('does not confuse a successful write with a failed list refresh', async () => {
    write = async () => { read = async () => reply({}, 503); return reply({ id: 1 }, 201) }
    mount(); await fill(); fireEvent.click(screen.getByRole('button', { name: 'Submit leave request' }))
    expect(await screen.findByText(/list could not be refreshed/)).toHaveTextContent('Do not submit the same request again')
    expect(vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1)
  })
  it('does not automatically retry an unknown result and preserves the draft', async () => {
    write = async () => { throw new TypeError('Failed to fetch') }
    mount(); await fill(); fireEvent.click(screen.getByRole('button', { name: 'Submit leave request' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Refresh the list to check')
    expect(screen.getByLabelText('Reason (optional)')).toHaveValue('Family time')
    expect(vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1)
  })
  it('prevents duplicate clicks and ignores a write completed after leaving the page', async () => {
    let resolve!: (response: Response) => void
    write = () => new Promise(r => { resolve = r })
    mount(); await fill(); fireEvent.click(screen.getByRole('button', { name: 'Submit leave request' }))
    const sending = await screen.findByRole('button', { name: 'Submitting…' })
    expect(sending).toBeDisabled(); fireEvent.click(sending)
    fireEvent.click(screen.getByRole('link', { name: 'My schedule' }))
    await screen.findByRole('heading', { name: 'My schedule' })
    await act(async () => resolve(reply({ id: 1 }, 201)))
    expect(screen.queryByText(/Leave request submitted/)).not.toBeInTheDocument()
    expect(vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1)
  })
  it('refreshes status on return without losing the draft, and filters actual server records', async () => {
    records = [{ id: 1, type: 'SICK', startDate: today, endDate: today, reason: 'Old reason', status: 'PENDING' }]
    mount(); await screen.findByRole('article', { name: 'Leave request 1' }); await fill()
    records = [{ id: 1, type: 'SICK', startDate: today, endDate: today, reason: 'Old reason', status: 'APPROVED' }]
    fireEvent.focus(window)
    await waitFor(() => expect(within(screen.getByRole('article', { name: 'Leave request 1' })).getByText('Approved')).toBeInTheDocument())
    expect(screen.getByLabelText('Reason (optional)')).toHaveValue('Family time')
    fireEvent.change(screen.getByLabelText('Request status'), { target: { value: 'REJECTED' } })
    expect(screen.getByText('No requests with this status.')).toBeInTheDocument()
  })
  it.each([401, 403])('removes records and input on read access loss %s', async status => {
    records = [{ id: 1, type: 'SICK', startDate: today, endDate: today, reason: 'Private reason', status: 'PENDING' }]
    mount(); await screen.findByText('Private reason'); await fill()
    read = async () => reply({}, status)
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByRole('heading', { name: status === 401 ? 'Sign in' : 'Access not permitted' })
    expect(screen.queryByText('Private reason')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Reason (optional)')).not.toBeInTheDocument()
  })
  it('clears protected records when submission loses access', async () => {
    write = async () => reply({}, 403)
    mount(); await fill(); fireEvent.click(screen.getByRole('button', { name: 'Submit leave request' }))
    await screen.findByText('Access not permitted')
    expect(screen.queryByRole('form', { name: 'Request leave' })).not.toBeInTheDocument()
  })
})
