import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import CaregiverHome from './index'
import type { SpotCheck } from '../../features/spot-checks/types'

vi.mock('../../shared/components/RoleShell', () => ({ RoleShell: ({ children }: { children: ReactNode }) => children }))
const initial: SpotCheck = { id: 8, elderId: 7, elderName: 'Demo Elder', caregiverId: 1, caregiverName: 'Caregiver A', visitId: 42,
  visitTime: '2026-10-08T09:00:00', purpose: 'Routine check', stage: 'COMPLETED', decidedAt: null, result: 'NEEDS_IMPROVEMENT', notes: 'Explain the routine clearly', checkedAt: '2026-10-08T09:15:00', closingReason: null, incidentId: null, caregiverResponse: null }
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
let records: SpotCheck[]
let read: () => Promise<Response>
let write: (options?: RequestInit) => Promise<Response>
beforeEach(() => {
  records = [{ ...initial }]
  read = async () => reply(records)
  write = async options => { records[0] = { ...records[0], caregiverResponse: JSON.parse(String(options?.body)).response }; return reply(records[0]) }
  vi.stubGlobal('fetch', vi.fn((url: string, options?: RequestInit) => {
    if (url === '/api/caregivers/me') return Promise.resolve(reply({ id: 1, userId: 10, fullName: 'Caregiver A' }))
    if (url === '/api/auth/csrf') return Promise.resolve(new Response(null, { status: 200 }))
    if (url === '/api/spot-checks') return read()
    if (url === '/api/spot-checks/8/response' && options?.method === 'POST') return write(options)
    throw new Error('Unexpected URL: ' + url)
  }))
})
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })
function mount(path = '/caregiver/spot-checks') {
  return render(<MemoryRouter initialEntries={[path]}><Routes><Route path="/caregiver/*" element={<CaregiverHome />} /><Route path="/" element={<h1>Sign in</h1>} /></Routes></MemoryRouter>)
}
function input(text = 'I will explain each step.') { fireEvent.change(screen.getByLabelText('Your response'), { target: { value: text } }) }
describe('caregiver spot-check conclusions', () => {
  it('uses the own collection and sends only a trimmed response', async () => {
    mount(); await screen.findByText('Demo Elder'); input('  I will explain each step.  ')
    fireEvent.click(screen.getByRole('button', { name: 'Submit response' }))
    await screen.findByText('I will explain each step.', { selector: 'p' })
    expect(screen.getByText('Needs improvement')).toBeInTheDocument()
    const post = vi.mocked(fetch).mock.calls.find(([, options]) => options?.method === 'POST')!
    expect(JSON.parse(String(post[1]?.body))).toEqual({ response: 'I will explain each step.' })
    expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url).includes('caregiverId') || String(url).includes('elderId'))).toBe(false)
  })
  it('edits or cancels the current response without changing the conclusion', async () => {
    records[0].caregiverResponse = 'Original response'
    mount(); await screen.findByText('Original response')
    fireEvent.click(screen.getByRole('button', { name: 'Edit response' })); input('Draft response')
    fireEvent.click(screen.getByRole('button', { name: 'Cancel editing' }))
    expect(screen.getByText('Original response')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Edit response' }))
    expect(screen.getByLabelText('Your response')).toHaveValue('Original response')
    input('Updated response'); fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    await screen.findByText('Updated response', { selector: 'p' })
    expect(screen.getByText('Finding: Explain the routine clearly')).toBeInTheDocument()
  })
  it('preserves the draft through manual and return refresh', async () => {
    mount(); await screen.findByText('Demo Elder'); input('Unsent draft')
    records[0].notes = 'Updated finding'
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByText('Finding: Updated finding')
    expect(screen.getByLabelText('Your response')).toHaveValue('Unsent draft')
    await act(async () => { await new Promise(r => setTimeout(r, 510)); fireEvent.focus(window) })
    await waitFor(() => expect(screen.getByLabelText('Your response')).toHaveValue('Unsent draft'))
  })
  it('refuses blank and overlong replies and accepts 500 characters', async () => {
    mount(); await screen.findByText('Demo Elder'); input('   ')
    expect(screen.getByRole('button', { name: 'Submit response' })).toBeDisabled()
    fireEvent.submit(screen.getByRole('form', { name: 'Respond to spot check 8' }))
    expect(screen.getByRole('alert')).toHaveTextContent('1 to 500')
    input('a'.repeat(501)); fireEvent.submit(screen.getByRole('form', { name: 'Respond to spot check 8' }))
    expect(vi.mocked(fetch).mock.calls.some(([, options]) => options?.method === 'POST')).toBe(false)
    input('a'.repeat(500)); fireEvent.click(screen.getByRole('button', { name: 'Submit response' }))
    await screen.findByText('a'.repeat(500))
  })
  it('locates a notification in the authorized list and lets the user change filters', async () => {
    records.push({ ...initial, id: 9, elderName: 'Other own elder', caregiverResponse: 'Already answered' })
    mount('/caregiver/spot-checks?spotCheckId=9')
    const target = await screen.findByRole('article', { name: 'Spot check 9' })
    await waitFor(() => expect(target).toHaveFocus())
    expect(screen.getByLabelText('Response status')).toHaveValue('ALL')
    expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url).includes('/spot-checks/9'))).toBe(false)
    fireEvent.change(screen.getByLabelText('Response status'), { target: { value: 'UNANSWERED' } })
    expect(screen.queryByText('Other own elder')).not.toBeInTheDocument()
  })
  it.each(['999', 'bad', '-1', '9007199254740993'])('handles an unavailable target %s without a detail request', async target => {
    mount('/caregiver/spot-checks?spotCheckId=' + target)
    await screen.findByText(/This conclusion is currently unavailable/)
    expect(screen.getByText('Demo Elder')).toBeInTheDocument()
    expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url).includes('/spot-checks/'))).toBe(false)
  })
  it('shows empty results and orders checked dates before null dates', async () => {
    records = []
    mount(); await screen.findByText('No completed spot checks yet.')
    records = [{ ...initial, id: 20, checkedAt: null }, { ...initial, id: 21, checkedAt: '2026-10-09T09:00:00' }]
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByRole('article', { name: 'Spot check 21' })
    expect(screen.getAllByRole('article')[0]).toHaveAccessibleName('Spot check 21')
  })
  it.each([401, 403])('clears data and drafts after access loss %s', async status => {
    mount(); await screen.findByText('Demo Elder'); input('Private draft')
    read = async () => reply({}, status); fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByRole('heading', { name: status === 401 ? 'Sign in' : 'Access not permitted' })
    expect(screen.queryByText('Demo Elder')).not.toBeInTheDocument()
    if (status === 403) {
      read = async () => reply(records); fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
      await screen.findByText('Demo Elder'); expect(screen.getByLabelText('Your response')).toHaveValue('')
    }
  })
  it('keeps a conflict draft and clears the page when a write is forbidden', async () => {
    write = async () => reply({ code: 'SPOT_CHECK_NOT_CONCLUDED' }, 409)
    mount(); await screen.findByText('Demo Elder'); input()
    fireEvent.click(screen.getByRole('button', { name: 'Submit response' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('cannot currently be answered')
    expect(screen.getByLabelText('Your response')).toHaveValue('I will explain each step.')
    write = async () => reply({}, 403); fireEvent.click(screen.getByRole('button', { name: 'Submit response' }))
    await screen.findByText('Access not permitted'); expect(screen.queryByText('Demo Elder')).not.toBeInTheDocument()
  })
  it('does not retry an uncertain write or falsely report success', async () => {
    write = async () => { throw new TypeError('Network down') }
    mount(); await screen.findByText('Demo Elder'); input()
    fireEvent.click(screen.getByRole('button', { name: 'Submit response' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Refresh the list to check')
    expect(screen.queryByText(/Response saved/)).not.toBeInTheDocument()
    expect(vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1)
  })
  it('distinguishes a saved response from a failed follow-up read', async () => {
    write = async options => { read = async () => reply({}, 503); return reply({ ...initial, caregiverResponse: JSON.parse(String(options?.body)).response }) }
    mount(); await screen.findByText('Demo Elder'); input()
    fireEvent.click(screen.getByRole('button', { name: 'Submit response' }))
    await screen.findByText(/Response saved.*list could not be refreshed/)
    expect(screen.queryByText('Demo Elder')).not.toBeInTheDocument()
  })
})
