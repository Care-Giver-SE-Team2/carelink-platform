import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import CaregiverLayout from './CaregiverLayout'
import { signOut } from '../../features/auth/api'

vi.mock('../../features/auth/api', () => ({ signOut: vi.fn() }))
vi.mock('../../shared/components/notifications/NotificationBell', () => ({ NotificationBell: () => null }))
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); vi.clearAllMocks() })
function mount(workAllowed: boolean, desktop = false) {
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: desktop, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  render(<MemoryRouter initialEntries={['/caregiver']}><Routes>
    <Route path="/caregiver" element={<CaregiverLayout name="Fictional Caregiver" workAllowed={workAllowed}><h1>Workspace</h1></CaregiverLayout>} />
    <Route path="/" element={<h1>Signed out</h1>} />
  </Routes></MemoryRouter>)
}
it('makes all mobile sections reachable, traps keyboard focus and restores the opener', () => {
  mount(true)
  const menu = screen.getByRole('button', { name: 'Menu' })
  menu.focus(); fireEvent.click(menu)
  const dialog = screen.getByRole('dialog', { name: 'Menu' })
  const history = within(dialog).getByRole('link', { name: 'My history' })
  expect(history).toBeInTheDocument()
  expect(within(dialog).getByRole('link', { name: 'Work preferences' })).toBeInTheDocument()
  const last = within(dialog).getByRole('button', { name: 'Sign out' })
  last.focus(); fireEvent.keyDown(dialog, { key: 'Tab' })
  expect(within(dialog).getByRole('button', { name: 'Close menu' })).toHaveFocus()
  fireEvent.keyDown(dialog, { key: 'Escape' })
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(menu).toHaveFocus()
})
it('shows only limited account/settings sections to a pending applicant', () => {
  mount(false)
  fireEvent.click(screen.getByRole('button', { name: 'Menu' }))
  const dialog = screen.getByRole('dialog')
  expect(within(dialog).getByRole('link', { name: 'My application' })).toBeInTheDocument()
  expect(within(dialog).getByRole('link', { name: 'My credentials' })).toBeInTheDocument()
  expect(within(dialog).queryByRole('link', { name: /My schedule|My leave|My history/ })).not.toBeInTheDocument()
})
it('provides desktop navigation and signs out to the landing page', async () => {
  vi.mocked(signOut).mockResolvedValue(undefined)
  mount(true, true)
  expect(screen.queryByRole('button', { name: 'Menu' })).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'My schedule' })).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))
  expect(await screen.findByRole('heading', { name: 'Signed out' })).toBeInTheDocument()
})
