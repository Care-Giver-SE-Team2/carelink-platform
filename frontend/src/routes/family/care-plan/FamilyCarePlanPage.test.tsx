import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import FamilyHome from '../index'
import type { FamilyElderProfile } from '../../../features/family-elders/api'
import type { FamilyCarePlan } from '../../../features/careplan/familyCarePlan'

const tan: FamilyElderProfile = {
  id: 1, fullName: 'Tan Mei', gender: 'FEMALE', dateOfBirth: '1948-02-03', phone: null,
  address: '12 Example Road', postalCode: '012345', preferredDialects: null, livesAlone: true,
  mobilityLevel: 'INDEPENDENT', accessScope: 'READ_ONLY',
}
const lim: FamilyElderProfile = { ...tan, id: 2, fullName: 'Lim Wei', accessScope: 'FULL' }
const catalog = [
  { code: 'BATHING', label: 'Bathing assistance', category: 'Personal care' },
  { code: 'VITALS', label: 'Vital-sign check', category: 'Health monitoring' },
]
const plans: Record<number, FamilyCarePlan> = {
  1: {
    current: {
      version: 2, effectiveFrom: '2026-10-01', effectiveUntil: '2026-11-03', tasks: [
        { groupName: 'Personal care', activityCode: 'BATHING', name: 'Shower help', slots: [
          { day: 'MONDAY', startTime: '08:00:00', minutes: 30 }, { day: 'FRIDAY', startTime: '08:00:00', minutes: 30 }] },
        { groupName: 'Health monitoring', activityCode: 'VITALS', name: 'Vital-sign check', slots: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
          .map((day) => ({ day, startTime: '19:00:00', minutes: 15 })) },
      ],
    },
    upcoming: {
      version: 3, effectiveFrom: '2026-11-03', effectiveUntil: null, tasks: [
        { groupName: 'Personal care', activityCode: 'BATHING', name: 'Bathing assistance', slots: [{ day: 'WEDNESDAY', startTime: '16:30:00', minutes: 45 }] },
      ],
    },
  },
  2: { current: null, upcoming: null },
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
function install(override?: (path: string) => Response | undefined) {
  const fetch = vi.fn(async (path: string) => {
    const result = override?.(path)
    if (result !== undefined) return result
    if (path === '/api/auth/me') return json({ id: 7, username: 'family', roles: ['FAMILY'] })
    if (path === '/api/elders' || path === '/api/family/elders') return json([tan, lim])
    if (path === '/api/care-activities') return json(catalog)
    const match = path.match(/^\/api\/family\/elders\/(\d+)\/care-plan$/)
    if (match) return json(plans[Number(match[1])])
    throw new Error(`Unexpected request: ${path}`)
  })
  vi.stubGlobal('fetch', fetch)
  return fetch
}
function open() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter initialEntries={['/family/care-plan']}>
    <Routes><Route path="/family/*" element={<FamilyHome />} /></Routes>
  </MemoryRouter></QueryClientProvider>)
}
beforeEach(() => { vi.stubGlobal('scrollTo', vi.fn()) })
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks() })

describe('Family care plan', () => {
  it('shows the plan in place and the one coming up, task by task, with read-only access', async () => {
    install(); open()
    const now = await screen.findByRole('region', { name: 'In place now: version 2' })
    expect(within(now).getByText(/Version 2, 1 Oct 2026 to 3 Nov 2026 · 2 h 45 min a week/)).toBeInTheDocument()
    expect(await within(now).findByText('Bathing assistance')).toBeInTheDocument()
    expect(within(now).getByText('Shower help')).toBeInTheDocument()
    expect(within(now).getByText('Mon, Fri · 8:00 AM · 30 min')).toBeInTheDocument()
    expect(within(now).getByText('Every day · 7:00 PM · 15 min')).toBeInTheDocument()
    const next = screen.getByRole('region', { name: 'Coming up: version 3' })
    expect(within(next).getByText(/from 3 Nov 2026/)).toBeInTheDocument()
    expect(within(next).getByText('Wed · 4:30 PM · 45 min')).toBeInTheDocument()
  })

  it('says when an elder has no care plan yet', async () => {
    install(); open()
    await screen.findByRole('region', { name: 'In place now: version 2' })
    await userEvent.selectOptions(screen.getByLabelText('Elder'), '2')
    expect(await screen.findByRole('heading', { name: 'No care plan yet' })).toBeInTheDocument()
  })

  it('does not show a plan it is no longer allowed to read', async () => {
    install((path) => path.endsWith('/care-plan') ? json({}, 403) : undefined); open()
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to show this care plan')
  })
})
