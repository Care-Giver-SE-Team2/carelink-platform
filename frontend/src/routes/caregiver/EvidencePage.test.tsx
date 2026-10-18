import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import EvidencePage from './EvidencePage'
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
let state: string
let write: () => Promise<Response>
beforeEach(() => {
  state = 'IN_PROGRESS'; write = async () => reply({ visitVersion: 2 })
  vi.stubGlobal('fetch', vi.fn((url: string, options?: RequestInit) => {
    if (options?.method === 'POST') return write()
    return Promise.resolve(reply(url.endsWith('work-pack') ? { visit: { id: 1, version: 1, status: state }, tasks: [{ id: 3, name: 'Care task' }] } : { items: [], page: 0, size: 20, total: 0, maxFiles: 20 }))
  }))
  vi.spyOn(window, 'confirm').mockReturnValue(true)
})
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })
const mount = () => render(<MemoryRouter initialEntries={['/caregiver/visits/1/evidence']}><Routes><Route path="/caregiver/visits/:visitId/evidence" element={<EvidencePage />} /></Routes></MemoryRouter>)
async function select() { fireEvent.click(await screen.findByRole('button', { name: 'Add evidence image' })); fireEvent.change(screen.getByLabelText('Evidence image'), { target: { files: [new File(['fake image for UI test'], 'demo.png', { type: 'image/png' })] } }) }
it('uploads multipart with the locked version and never claims verification or check-out gating', async () => {
  mount(); await select(); fireEvent.change(screen.getByLabelText('Evidence kind'), { target: { value: 'SIGNATURE' } }); fireEvent.click(screen.getByRole('button', { name: 'Upload image' }))
  await screen.findByText(/Evidence saved — Unverified/)
  const [, options] = vi.mocked(fetch).mock.calls.find(([, init]) => init?.method === 'POST')!
  const body = options?.body as FormData
  expect(body.get('kind')).toBe('SIGNATURE'); expect(body.get('expectedVersion')).toBe('1'); expect(body.get('clientRequestId')).toBeTruthy()
  expect(screen.getByText(/Evidence is not required to check out/)).toBeInTheDocument()
})
it('preserves identical image and command after unknown response', async () => {
  write = async () => { throw new TypeError('offline') }
  mount(); await select(); fireEvent.click(screen.getByRole('button', { name: 'Upload image' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Retry unchanged upload' }))
  await waitFor(() => expect(vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(2))
  const forms = vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'POST').map(([, options]) => options?.body as FormData)
  expect(forms[0].get('clientRequestId')).toBe(forms[1].get('clientRequestId')); expect(forms[0].get('file')).toBe(forms[1].get('file'))
  expect(screen.getByLabelText('Evidence image')).toBeDisabled()
})
it('completed work is read-only and invalid uploads are stopped locally', async () => {
  state = 'COMPLETED'; mount(); await screen.findByText(/Existing evidence is read-only/)
  expect(screen.queryByRole('button', { name: 'Add evidence image' })).not.toBeInTheDocument()
})
it('does not offer upload when the work pack is forbidden', async () => {
  vi.mocked(fetch).mockResolvedValue(reply({}, 403)); mount(); await screen.findByRole('alert')
  expect(screen.queryByRole('button', { name: 'Add evidence image' })).not.toBeInTheDocument()
})
it('returns to the work pack with the schedule dates but no unrelated query data', async () => {
  render(<MemoryRouter initialEntries={['/caregiver/visits/1/evidence?dateFrom=2026-10-09&dateTo=2026-10-15&note=secret']}><Routes><Route path="/caregiver/visits/:visitId/evidence" element={<EvidencePage />} /></Routes></MemoryRouter>)
  expect(await screen.findByRole('link', { name: '← Visit work pack' })).toHaveAttribute('href', '/caregiver/visits/1?dateFrom=2026-10-09&dateTo=2026-10-15')
})
