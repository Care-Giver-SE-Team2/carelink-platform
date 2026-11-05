import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import ApplicationPage from './ApplicationPage'
import type { Admission } from '../../features/admission/api'

const application = { id: 2, caregiverId: 1, status: 'PENDING' as const, roundNumber: 1, details: { fullName: 'Demo Caregiver', phone: '+6591234567', declaredDialects: null, requestedSector: null }, submittedAt: '2026-10-12T09:00:00', reviewedByUserId: null, reviewedAt: null, reviewReason: null }
let admission: Admission
const profile = { id: 1, userId: 10, fullName: 'Demo Caregiver', phone: '+6591234567', sector: null, dialects: null, status: 'ONBOARDING' }
const refreshProfile = vi.fn()
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
beforeEach(() => {
  admission = { caregiverId: 1, userId: 10, username: 'demo', workStatus: 'ONBOARDING', phone: profile.phone, version: 0, application, history: [], canWork: false, canEdit: true, canResubmit: false, compatibility: null }
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(reply(admission))))
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  refreshProfile.mockClear()
})
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })
const mount = () => render(<MemoryRouter><ApplicationPage profile={profile} refreshProfile={refreshProfile} /></MemoryRouter>)
it('shows real pending status and optional certificate wording without a self-approval control', async () => {
  mount(); expect(await screen.findByText(/Application 2 · PENDING/)).toBeInTheDocument()
  expect(screen.getByText(/No certificate is required/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Approve/ })).not.toBeInTheDocument()
})
it('preserves application drafts on focus and sends the original version', async () => {
  mount(); fireEvent.click(await screen.findByRole('button', { name: 'Edit application' }))
  fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'Corrected Name' } })
  act(() => window.dispatchEvent(new Event('focus')))
  expect(screen.getByLabelText('Full name')).toHaveValue('Corrected Name')
  fireEvent.click(screen.getByRole('button', { name: 'Save application' }))
  await screen.findByText('Application details saved.')
  const call = vi.mocked(fetch).mock.calls.find(([, options]) => options?.method === 'PUT')!
  expect(JSON.parse(String(call[1]?.body))).toMatchObject({ fullName: 'Corrected Name', expectedVersion: 0 })
  expect(refreshProfile).toHaveBeenCalled()
})
it('retries the unchanged request after an unknown save response', async () => {
  vi.mocked(fetch).mockImplementation((_url, options) => options?.method === 'PUT' ? Promise.reject(new TypeError('offline')) : Promise.resolve(reply(admission)))
  mount(); fireEvent.click(await screen.findByRole('button', { name: 'Edit application' }))
  fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'Corrected Name' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save application' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Retry unchanged request' }))
  await waitFor(() => expect(vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'PUT')).toHaveLength(2))
  const calls = vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'PUT')
  expect(calls[0][1]?.body).toEqual(calls[1][1]?.body)
})
it('shows a rejection reason and sends a deliberate resubmission', async () => {
  admission = { ...admission, canResubmit: true, version: 1, application: { ...application, status: 'REJECTED', reviewReason: 'Correct mobile number' } }
  mount(); fireEvent.click(await screen.findByRole('button', { name: 'Resubmit application' }))
  await screen.findByText(/Application resubmitted/)
  const call = vi.mocked(fetch).mock.calls.find(([, options]) => options?.method === 'POST')!
  expect(call[0]).toBe('/api/caregivers/me/application/resubmissions')
  expect(JSON.parse(String(call[1]?.body)).expectedVersion).toBe(1)
})
