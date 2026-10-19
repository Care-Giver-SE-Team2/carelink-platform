import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import SystemAlertsPage, { SystemAlertDetailPage } from './SystemAlertsPage'
import { linkFor } from '../../features/notifications/presentation'
const detail = { incidentId: 9, visitId: 5, source: 'SYSTEM_MISSED_CHECKIN', scheduledStart: '2026-10-12T09:00:00', checkInDueAt: '2026-10-12T09:10:00', triggeredAt: '2026-10-12T09:10:01', timeZone: 'Asia/Singapore', workPackAvailable: true }
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
beforeEach(() => vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(reply(url.includes('?') ? { items: [detail], page: 0, size: 20, total: 1 } : detail)))))
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })
function mount(path = '/caregiver/system-alerts') { render(<MemoryRouter initialEntries={[path]}><Routes><Route path="/caregiver/system-alerts" element={<SystemAlertsPage />} /><Route path="/caregiver/system-alerts/:incidentId" element={<SystemAlertDetailPage />} /></Routes></MemoryRouter>) }
it('separates system facts from self reports and links the notification to its safe detail', async () => {
  expect(linkFor({ resourceType: 'CAREGIVER_SYSTEM_ALERT', resourceId: 9 }, 'caregiver')).toBe('/caregiver/system-alerts/9')
  expect(linkFor({ resourceType: 'CAREGIVER_SYSTEM_ALERT', resourceId: 9 }, 'family')).toBeNull()
  mount(); fireEvent.click(await screen.findByRole('link', { name: 'Open alert 9' }))
  expect(await screen.findByText(/not a report submitted by you/)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Open current work pack' })).toHaveAttribute('href', '/caregiver/visits/5')
})
it('keeps a reassigned alert minimal and never offers a new work pack', async () => {
  vi.mocked(fetch).mockResolvedValue(reply({ ...detail, workPackAvailable: false })); mount('/caregiver/system-alerts/9')
  expect(await screen.findByText(/Only your original alert summary/)).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Open current work pack' })).not.toBeInTheDocument()
})
it('clears previously loaded alert on refresh authorization failure', async () => {
  mount('/caregiver/system-alerts/9'); await screen.findByText(/Visit 5 · Alert 9/)
  vi.mocked(fetch).mockResolvedValue(reply({}, 404)); fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
  await screen.findByRole('alert'); expect(screen.queryByText(/Visit 5 · Alert 9/)).not.toBeInTheDocument()
})
