import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { FamilyLayout } from './FamilyLayout'

beforeEach(() => {
  vi.stubGlobal('scrollTo', vi.fn())
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function renderSchedule() {
  render(
    <MemoryRouter initialEntries={['/family/schedule']}>
      <Routes>
        <Route element={<FamilyLayout />}>
          <Route path="/family/schedule" element={<h1>Your weekly schedule</h1>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('Family workspace', () => {
  it('navigates between tabs and identifies the current one', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/family/intake/new']}>
        <Routes>
          <Route element={<FamilyLayout />}>
            <Route path="/family/intake/new" element={<h1>New application</h1>} />
            <Route path="/family/schedule" element={<h1>Your weekly schedule</h1>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByRole('navigation', { name: 'Family pages' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Services' })).toHaveAttribute('aria-current', 'page')
    await user.click(screen.getByRole('link', { name: 'Schedule' }))
    expect(screen.getByRole('heading', { name: 'Your weekly schedule' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Schedule' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Services' })).not.toHaveAttribute('aria-current')
    expect(screen.getByRole('link', { name: 'Home' })).toHaveAttribute('href', '/family/home')
    expect(screen.getByRole('link', { name: 'Reports' })).toHaveAttribute('href', '/family/reports/weekly')
    expect(within(screen.getByRole('navigation', { name: 'Family pages' })).getAllByRole('link').map((link) => link.textContent))
      .toEqual(['Home', 'Schedule', 'Reports', 'Services', 'Account'])
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#family-content')
  })

  it('keeps the Schedule tab active on a visit reached from the schedule', () => {
    render(
      <MemoryRouter initialEntries={['/family/visits/7']}>
        <Routes>
          <Route element={<FamilyLayout />}>
            <Route path="/family/visits/:id" element={<h1>Visit progress</h1>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Schedule' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current')
  })

  it('uses the page title and restores the previous one on leaving', () => {
    const previousTitle = document.title
    const { unmount } = render(
      <MemoryRouter initialEntries={['/family/schedule']}>
        <Routes>
          <Route element={<FamilyLayout title="Weekly schedule" />}>
            <Route
              path="/family/schedule"
              element={<h1>Your weekly schedule</h1>}
            />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    expect(document.title).toBe('Weekly schedule · CareLink')
    unmount()
    expect(document.title).toBe(previousTitle)
  })

  it('has no Sign out of its own; it lives only on Account', () => {
    renderSchedule()
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Account' })).toHaveAttribute('href', '/family/account')
  })
})
