import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { FamilyLayout } from './FamilyLayout'

const elders = [{ id: 21, fullName: 'Tan Mei', dateOfBirth: null, address: null, sector: null, planStatus: 'none', planVersion: null, nextVisitDate: null }]
const change = {
  id: 1, elderId: 21, elderName: 'Tan Mei', visitStart: '2026-10-13T10:00', visitEnd: null, usualCaregiverName: 'Siti Rahmah',
  status: 'AWAITING_FAMILY', respondBy: '2026-10-12T18:00', suggestedCaregiverId: null, options: [], outcome: null,
  decidedBy: null, decidedAt: null, assignedCaregiver: null, rescheduledStart: null, note: null,
}
const spotCheck = {
  id: 2, elderId: 21, elderName: 'Tan Mei', caregiverId: 31, caregiverName: 'Siti Rahmah', visitId: 9, visitTime: '2026-10-14T09:00',
  purpose: 'Quality check', stage: 'AWAITING_FAMILY', decidedAt: null, result: null, notes: null, checkedAt: null,
  closingReason: null, incidentId: null, caregiverResponse: null,
}

function json(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

/** Answers the navigation's own reads: the linked elders and what waits on the family. */
function installApi({ changes = [] as unknown[], spotChecks = [] as unknown[] } = {}) {
  vi.stubGlobal('fetch', vi.fn((path: string) => {
    const url = new URL(path, 'http://localhost')
    const responses: Record<string, unknown> = {
      '/api/elders': elders,
      '/api/roster-changes': changes,
      '/api/spot-checks': spotChecks,
      '/api/family/value-added-service-requests': [],
    }
    if (!(url.pathname in responses)) throw new Error(`Unexpected API request: ${path}`)
    return Promise.resolve(json(responses[url.pathname]))
  }))
}

function renderAt(path: string, title?: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<FamilyLayout title={title} />}>
            <Route path="/family/home" element={<h1>Home page</h1>} />
            <Route path="/family/schedule" element={<h1>Your weekly schedule</h1>} />
            <Route path="/family/visits/:id" element={<h1>Visit progress</h1>} />
            <Route path="/family/intake/new" element={<h1>New application</h1>} />
            <Route path="/family/changes" element={<h1>Visit changes</h1>} />
            <Route path="/family/spot-checks" element={<h1>Spot checks</h1>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.stubGlobal('scrollTo', vi.fn())
  installApi()
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('Family workspace', () => {
  it('navigates between tabs and identifies the current one', async () => {
    const user = userEvent.setup()
    renderAt('/family/intake/new')

    const nav = screen.getByRole('navigation', { name: 'Family pages' })
    expect(within(nav).getAllByRole('link').map((link) => link.textContent))
      .toEqual(['Home', 'Schedule', 'Reports'])
    // Applications are only in the Menu, so the Menu shows as the current tab.
    expect(within(nav).getByRole('button', { name: 'Menu' })).toHaveAttribute('data-current')
    await user.click(screen.getByRole('link', { name: 'Schedule' }))
    expect(screen.getByRole('heading', { name: 'Your weekly schedule' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Schedule' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getByRole('button', { name: 'Menu' })).not.toHaveAttribute('data-current')
    expect(screen.getByRole('link', { name: 'Home' })).toHaveAttribute('href', '/family/home')
    expect(screen.getByRole('link', { name: 'Reports' })).toHaveAttribute('href', '/family/reports/weekly')
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#family-content')
  })

  it('keeps the Schedule tab active on a visit reached from the schedule', () => {
    renderAt('/family/visits/7')
    expect(screen.getByRole('link', { name: 'Schedule' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current')
  })

  it('uses the page title and restores the previous one on leaving', () => {
    const previousTitle = document.title
    renderAt('/family/schedule', 'Weekly schedule')
    expect(document.title).toBe('Weekly schedule · CareLink')
    cleanup()
    expect(document.title).toBe(previousTitle)
  })

  it('has no Sign out of its own; it lives only on Account', async () => {
    const user = userEvent.setup()
    renderAt('/family/schedule')
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Menu' }))
    expect(screen.getByRole('link', { name: 'Account' })).toHaveAttribute('href', '/family/account')
  })

  it('lists every section in the Menu, grouped, and closes on choosing one or on Esc', async () => {
    const user = userEvent.setup()
    renderAt('/family/home')
    const menuButton = screen.getByRole('button', { name: 'Menu' })
    expect(menuButton).toHaveAttribute('aria-expanded', 'false')

    await user.click(menuButton)
    const sheet = screen.getByRole('dialog', { name: 'Menu' })
    expect(within(sheet).getAllByRole('heading').map((heading) => heading.textContent))
      .toEqual(['Care', 'Needs your answer', 'Your service'])
    expect(within(sheet).getAllByRole('link').map((link) => link.textContent)).toEqual([
      'Home', 'Schedule', 'Care plan', 'Reports', 'Visit changes', 'Spot checks', 'Extra services',
      'Care applications', 'Caregiver review', 'Account',
    ])
    expect(within(sheet).getByRole('link', { name: 'Home' })).toHaveFocus()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(menuButton).toHaveFocus()

    await user.click(menuButton)
    await user.click(within(screen.getByRole('dialog')).getByRole('link', { name: 'Spot checks' }))
    expect(screen.getByRole('heading', { name: 'Spot checks' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    // Spot checks is only in the Menu, so the Menu shows as the current tab.
    expect(screen.getByRole('button', { name: 'Menu' })).toHaveAttribute('data-current')
  })

  it('counts what waits on the family, on Menu and beside each section in it', async () => {
    installApi({ changes: [change, { ...change, id: 3, status: 'SETTLED' }], spotChecks: [spotCheck] })
    const user = userEvent.setup()
    renderAt('/family/home')

    await user.click(await screen.findByRole('button', { name: 'Menu, 2 waiting' }))
    const sheet = screen.getByRole('dialog', { name: 'Menu' })
    expect(within(sheet).getByRole('link', { name: 'Visit changes, 1 waiting' })).toHaveAttribute('href', '/family/changes')
    expect(within(sheet).getByRole('link', { name: 'Spot checks, 1 waiting' })).toBeInTheDocument()
    expect(within(sheet).getByRole('link', { name: 'Extra services' })).toBeInTheDocument()
  })
})
