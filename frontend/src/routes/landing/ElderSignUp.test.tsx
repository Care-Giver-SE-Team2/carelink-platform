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

async function fill(username = 'elder.new', password = 'password123', confirm = password) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Username'), username)
  await user.type(screen.getByLabelText('Password', { exact: true }), password)
  await user.type(screen.getByLabelText('Confirm password'), confirm)
  return user
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('Elder self-registration', () => {
  it('creates the account, logs in and opens Elder workspace', async () => {
    const fetchMock = server()
    open()
    const user = await fill()
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByRole('heading', { name: 'Elder workspace' })).toBeTruthy()
    const registration = fetchMock.mock.calls.find(([url]) => url === '/api/elder-registrations')!
    expect(JSON.parse(registration[1].body)).toEqual({ username: 'elder.new', password: 'password123' })
    expect(new Headers(registration[1].headers).get('X-XSRF-TOKEN')).toBe('elder-token')
    const login = fetchMock.mock.calls.find(([url]) => url === '/api/auth/login')!
    expect(JSON.parse(login[1].body)).toEqual({ username: 'elder.new', password: 'password123' })
  })

  it('blocks mismatched passwords before making requests', async () => {
    const fetchMock = server()
    open()
    const user = await fill('elder.new', 'password123', 'different123')
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(screen.getByRole('alert').textContent).toContain('Passwords do not match')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('blocks invalid usernames and short passwords', async () => {
    const fetchMock = server()
    open()
    const user = await fill('BadName', 'short')
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(screen.getByRole('alert').textContent).toContain('Username must be')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows duplicate username and does not log in', async () => {
    const fetchMock = server(409)
    open()
    const user = await fill()
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'Username is already taken.')
    expect(fetchMock.mock.calls.some(([url]) => url === '/api/auth/login')).toBe(false)
  })

  it('explains when registration succeeds but login fails', async () => {
    server(201, 401)
    open()
    const user = await fill()
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('Account created'))
  })
})
