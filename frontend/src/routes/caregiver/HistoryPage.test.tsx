import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import HistoryPage, { HistoryDetailPage } from './HistoryPage'

const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
afterEach(() => vi.unstubAllGlobals())
const mount = (detail = false) => render(<MemoryRouter initialEntries={[detail ? '/caregiver/history/5' : '/caregiver/history']}><Routes><Route path="/caregiver/history" element={<HistoryPage />} /><Route path="/caregiver/history/:visitId" element={<HistoryDetailPage />} /></Routes></MemoryRouter>)
it('filters by action month, keeps absent provenance explicit, and paginates', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => reply({ items: [{ visitId: 5, currentVisitStatus: 'COMPLETED', ownCheckInAt: null, ownCheckOutAt: '2026-10-12T10:00:00', lastOwnActionAt: '2026-10-12T10:00:00', commandCount: 2 }], total: 21, coverageNote: 'Only attributable actions.' })))
  mount(); await screen.findByText('Visit 5 · COMPLETED')
  expect(screen.getByText('My check-in: No attributable record')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'View my facts for Visit 5' }).getAttribute('href')).toMatch(/^\/caregiver\/history\/5\?month=\d{4}-\d{2}&page=0$/)
  fireEvent.change(screen.getByLabelText('Month (YYYY-MM)'), { target: { value: '2026-09' } })
  fireEvent.click(screen.getByRole('button', { name: 'Apply month' }))
  await waitFor(() => expect(vi.mocked(fetch).mock.lastCall?.[0]).toContain('month=2026-09'))
  await screen.findByText('Visit 5 · COMPLETED'); fireEvent.click(screen.getByRole('button', { name: 'Next' }))
  await waitFor(() => expect(vi.mocked(fetch).mock.lastCall?.[0]).toContain('page=1'))
})
it('restores validated list filters after detail pagination and navigation back', async () => {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => reply(url.includes('/history/5?')
    ? { visitId: 5, total: 21, facts: [], spotChecks: [] }
    : { items: [{ visitId: 5, currentVisitStatus: 'VERIFIED', ownCheckInAt: null, ownCheckOutAt: null, lastOwnActionAt: '2026-09-12T10:00:00', commandCount: 2 }], total: 60, coverageNote: 'Own facts.' })))
  render(<MemoryRouter initialEntries={['/caregiver/history?month=2026-09&status=VERIFIED&page=1']}><Routes><Route path="/caregiver/history" element={<HistoryPage />} /><Route path="/caregiver/history/:visitId" element={<HistoryDetailPage />} /></Routes></MemoryRouter>)
  fireEvent.click(await screen.findByRole('link', { name: 'View my facts for Visit 5' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Next' }))
  await waitFor(() => expect(vi.mocked(fetch).mock.lastCall?.[0]).toContain('/history/5?page=1'))
  fireEvent.click(screen.getByRole('link', { name: '← My execution history' }))
  await screen.findByText('Visit 5 · VERIFIED')
  expect(screen.getByLabelText('Month (YYYY-MM)')).toHaveValue('2026-09')
  expect(screen.getByLabelText('Current Visit status')).toHaveValue('VERIFIED')
  expect(vi.mocked(fetch).mock.lastCall?.[0]).toContain('month=2026-09&page=1&size=20&status=VERIFIED')
})
it('rejects invalid months without sending a query', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => reply({ items: [], total: 0, coverageNote: 'No legacy attribution.' })))
  mount(); await screen.findByText('No legacy attribution.')
  const count = vi.mocked(fetch).mock.calls.length
  fireEvent.change(screen.getByLabelText('Month (YYYY-MM)'), { target: { value: '2026-13' } }); fireEvent.click(screen.getByRole('button', { name: 'Apply month' }))
  expect(screen.getByRole('alert')).toHaveTextContent('YYYY-MM'); expect(fetch).toHaveBeenCalledTimes(count)
})
it('renders only supplied personal facts and uses the historical download route', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => reply({ visitId: 5, total: 1, spotChecks: [], facts: [{ action: 'EVIDENCE_UPLOAD', sourceId: 8, visitVersion: 3, recordedAt: '2026-10-12T09:00:00', evidence: { evidenceId: 8, kind: 'PHOTO', verificationStatus: 'UNVERIFIED', fileName: 'care.png', downloadPath: '/api/caregivers/me/history/5/evidence/8/content' }, health: { healthFlag: 'ATTENTION', healthNote: 'Own observation', readings: [{ metric: 'PULSE', value: 72, unit: 'bpm' }] } }] })))
  mount(true); await screen.findByText('EVIDENCE UPLOAD')
  expect(screen.getByText('PULSE: 72 bpm')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Download my evidence 8' })).toHaveAttribute('href', '/api/caregivers/me/history/5/evidence/8/content')
  expect(screen.queryByRole('link', { name: /work pack/i })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /upload|save|check out/i })).not.toBeInTheDocument()
})
it('does not show another caregiver’s history when the server denies access', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => reply({}, 404)))
  mount(true); await screen.findByRole('alert')
  expect(screen.queryByRole('link', { name: /Download/ })).not.toBeInTheDocument()
})
