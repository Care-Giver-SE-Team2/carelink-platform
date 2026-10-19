import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import CaregiverSignUp from './CaregiverSignUp'
const reply = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
beforeEach(() => vi.stubGlobal('fetch', vi.fn(async () => reply({}))))
afterEach(() => vi.unstubAllGlobals())
function fill(password = 'DemoOnly123!') {
  render(<MemoryRouter><CaregiverSignUp /></MemoryRouter>)
  for (const [label, value] of [['Full name', 'Test Caregiver'], ['Mobile number', '91234567'], ['Choose a username', 'Demo-New'], ['Choose a password', password], ['Confirm password', password]]) fireEvent.change(screen.getByLabelText(label), { target: { value } })
}
it('registers without any certificate or privilege fields and does not auto-login', async () => {
  fill(); fireEvent.click(screen.getByRole('button', { name: 'Submit application' }))
  await screen.findByText('Application submitted')
  expect(vi.mocked(fetch).mock.calls.map(call => call[0])).toEqual(['/api/auth/csrf', '/api/caregiver-registrations'])
  const payload = JSON.parse(String(vi.mocked(fetch).mock.lastCall?.[1]?.body))
  expect(payload).toEqual({ fullName: 'Test Caregiver', phone: '+6591234567', username: 'demo-new', password: 'DemoOnly123!', declaredDialects: null, requestedSector: null })
  expect(screen.queryByLabelText('Choose a password')).not.toBeInTheDocument()
})
it('stops passwords exceeding the UTF-8 limit before any request', () => {
  fill('密'.repeat(25)); fireEvent.click(screen.getByRole('button', { name: 'Submit application' }))
  expect(screen.getByRole('alert')).toHaveTextContent('72 UTF-8 bytes'); expect(fetch).not.toHaveBeenCalled()
})
it('does not silently retry a registration with an unknown result', async () => {
  vi.mocked(fetch).mockImplementation(async url => { if (String(url).endsWith('csrf')) return reply({}); throw new TypeError('offline') })
  fill(); fireEvent.click(screen.getByRole('button', { name: 'Submit application' }))
  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Try signing in'))
  expect(fetch).toHaveBeenCalledTimes(2)
})
