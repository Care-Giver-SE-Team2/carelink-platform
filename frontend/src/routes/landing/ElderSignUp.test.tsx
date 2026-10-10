import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ElderSignUp from './ElderSignUp'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function server(registrationStatus = 201, loginStatus = 200) {
  const fetchMock = vi.fn().mockImplementation((url: string, init: RequestInit = {}) => {
    if (url === '/api/auth/csrf') {
      document.cookie = 'XSRF-TOKEN=elder-token; path=/'
      return Promise.resolve(new Response(null))
    }
    if (url === '/api/elder-registrations' && init.method === 'POST') {
      return Promise.resolve(registrationStatus === 201
        ? json({ userId: 42, username: 'elder.new' }, 201)
        : json({ code: 'USERNAME_TAKEN' }, registrationStatus))
    }
    if (url === '/api/auth/login' && init.method === 'POST') {
      return Promise.resolve(loginStatus === 200
        ? json({ id: 42, username: 'elder.new', roles: ['ELDER'] })
        : json({ error: 'Login failed' }, loginStatus))
    }
    return Promise.resolve(json({}, 404))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function open() {
  render(<MemoryRouter initialEntries={['/elder/register']}>
    <Routes>
      <Route path="/elder/register" element={<ElderSignUp />} />
      <Route path="/elder" element={<h1>Elder workspace</h1>} />
    </Routes>
  </MemoryRouter>)
}

async function fill(username = 'elder.new', password = 'password123', confirm = password, fullName = '  Tan Ah Mah ') {
  const user = userEvent.setup()
  if (fullName) await user.type(screen.getByLabelText('Your full name'), fullName)
  await user.type(screen.getByLabelText('Choose a username'), username)
  await user.type(screen.getByLabelText('Choose a password'), password)
  await user.type(screen.getByLabelText('Type the password again'), confirm)
  return user
}

const submit = { name: 'Create account and continue' }

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('Elder self-registration', () => {
  it('creates the account under the elder\'s name, logs in and opens Elder workspace', async () => {
    const fetchMock = server()
    open()
    const user = await fill()
    await user.click(screen.getByRole('button', submit))
    expect(await screen.findByRole('heading', { name: 'Elder workspace' })).toBeTruthy()
    const registration = fetchMock.mock.calls.find(([url]) => url === '/api/elder-registrations')!
    expect(JSON.parse(registration[1].body)).toEqual({ fullName: 'Tan Ah Mah', username: 'elder.new', password: 'password123' })
    expect(new Headers(registration[1].headers).get('X-XSRF-TOKEN')).toBe('elder-token')
    const login = fetchMock.mock.calls.find(([url]) => url === '/api/auth/login')!
    expect(JSON.parse(login[1].body)).toEqual({ username: 'elder.new', password: 'password123' })
  })

  it('checks the details beside each field before sending anything', async () => {
    const fetchMock = server()
    open()
    const user = await fill('-bad', 'short', 'short', '   ')
    await user.click(screen.getByRole('button', submit))
    expect(screen.getByText('Enter your full name.')).toBeTruthy()
    expect(screen.getByText('Use at least 8 characters.')).toBeTruthy()
    expect(screen.getByLabelText('Choose a username').getAttribute('aria-invalid')).toBe('true')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('blocks mismatched passwords before making requests', async () => {
    const fetchMock = server()
    open()
    const user = await fill('elder.new', 'password123', 'different123')
    await user.click(screen.getByRole('button', submit))
    expect(screen.getByText('The passwords do not match.')).toBeTruthy()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('asks for another username when the chosen one is taken, and does not log in', async () => {
    const fetchMock = server(409)
    open()
    const user = await fill()
    await user.click(screen.getByRole('button', submit))
    expect(await screen.findByText('That username is taken. Choose another.')).toBeTruthy()
    expect(fetchMock.mock.calls.some(([url]) => url === '/api/auth/login')).toBe(false)
  })

  it('explains when registration succeeds but login fails', async () => {
    server(201, 401)
    open()
    const user = await fill()
    await user.click(screen.getByRole('button', submit))
    await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('Your account was created'))
  })
})
