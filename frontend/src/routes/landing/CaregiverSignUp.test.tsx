import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import CaregiverSignUp from './CaregiverSignUp'

const reply = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
beforeEach(() => vi.stubGlobal('fetch', vi.fn(async () => reply({}))))
afterEach(() => vi.unstubAllGlobals())

function fill(password = 'DemoOnly123!', confirm = password) {
  render(<MemoryRouter><CaregiverSignUp /></MemoryRouter>)
  for (const [label, value] of [
    ['Full name', 'Test Caregiver'],
    ['Mobile number', '91234567'],
    ['Choose a username', 'Demo-New'],
    ['Choose a password', password],
    ['Type the password again', confirm],
  ])
    fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

const submit = () => fireEvent.click(screen.getByRole('button', { name: 'Submit application' }))

it('registers without any certificate or privilege fields and does not auto-login', async () => {
  fill()
  submit()
  await screen.findByText('Application submitted')
  expect(vi.mocked(fetch).mock.calls.map((call) => call[0])).toEqual(['/api/auth/csrf', '/api/caregiver-registrations'])
  const payload = JSON.parse(String(vi.mocked(fetch).mock.lastCall?.[1]?.body))
  expect(payload).toEqual({ fullName: 'Test Caregiver', phone: '+6591234567', username: 'demo-new', password: 'DemoOnly123!', declaredDialects: null, requestedSector: null })
  expect(screen.queryByLabelText('Choose a password')).not.toBeInTheDocument()
  expect(screen.getByRole('status')).toHaveTextContent('Sign in as demo-new')
  expect(screen.getByRole('link', { name: 'Continue to sign in' })).toHaveAttribute('href', '/')
})

it('sends the chosen languages and preferred area', async () => {
  fill()
  fireEvent.click(screen.getByLabelText('Malay'))
  fireEvent.click(screen.getByLabelText('English'))
  fireEvent.change(screen.getByLabelText('Preferred area · optional'), { target: { value: '  Ang Mo Kio ' } })
  submit()
  await screen.findByText('Application submitted')
  const payload = JSON.parse(String(vi.mocked(fetch).mock.lastCall?.[1]?.body))
  expect(payload).toMatchObject({ declaredDialects: 'Malay,English', requestedSector: 'Ang Mo Kio' })
})

it('shows each problem under its own field before any request', () => {
  fill('密'.repeat(25))
  submit()
  expect(screen.getByLabelText('Choose a password')).toHaveAccessibleDescription('Use at most 72 UTF-8 bytes.')
  expect(fetch).not.toHaveBeenCalled()
})

it('stops mismatched passwords before any request', () => {
  fill('DemoOnly123!', 'DemoOnly124!')
  submit()
  expect(screen.getByLabelText('Type the password again')).toHaveAccessibleDescription('The passwords do not match.')
  expect(fetch).not.toHaveBeenCalled()
})

it('asks for another username when it is taken', async () => {
  vi.mocked(fetch).mockImplementation(async (url) =>
    String(url).endsWith('csrf') ? reply({}) : reply({ code: 'USERNAME_TAKEN', detail: 'That username is already taken' }, 409),
  )
  fill()
  submit()
  await waitFor(() =>
    expect(screen.getByLabelText('Choose a username')).toHaveAccessibleDescription('That username is taken. Choose another.'),
  )
})

it('does not silently retry a registration with an unknown result', async () => {
  vi.mocked(fetch).mockImplementation(async (url) => {
    if (String(url).endsWith('csrf')) return reply({})
    throw new TypeError('offline')
  })
  fill()
  submit()
  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Try signing in'))
  expect(fetch).toHaveBeenCalledTimes(2)
})
