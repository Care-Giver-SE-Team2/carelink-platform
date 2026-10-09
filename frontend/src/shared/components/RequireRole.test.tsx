import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, SESSION_EXPIRED_EVENT } from '../api/client'
import { RequireRole } from './RequireRole'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
function user(roles: string[]) {
  return { id: 1, username: 'someone', displayName: 'Someone', roles }
}
function stubMe(response: () => Response | Promise<Response>) {
  const fetchMock = vi.fn((path: string) => {
    if (path === '/api/auth/me') return Promise.resolve(response())
    if (path === '/api/protected') return Promise.resolve(new Response(null, { status: 401 }))
    if (path === '/api/auth/login') return Promise.resolve(new Response(null, { status: 401 }))
    throw new Error(`Unexpected API request: ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const secret = vi.fn(() => <h1>Family screen</h1>)

function open(path = '/family') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/" element={<h1>Landing</h1>} />
          <Route path="/family/*" element={<RequireRole role="FAMILY">{secret()}</RequireRole>} />
          <Route path="/manager/*" element={<h1>Manager console</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return client
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  secret.mockClear()
})

describe('RequireRole', () => {
  it('sends a visitor with no session to the landing page without showing the screen', async () => {
    stubMe(() => new Response(null, { status: 401 }))
    open('/family/schedule')
    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Family screen' })).not.toBeInTheDocument()
  })

  it('shows nothing of the screen while the session is still being checked', () => {
    stubMe(() => new Promise<Response>(() => {}))
    open()
    expect(screen.getByRole('status')).toHaveTextContent('Checking your sign-in')
    expect(screen.queryByRole('heading', { name: 'Family screen' })).not.toBeInTheDocument()
  })

  it.each([['FAMILY'], ['ROLE_FAMILY']])('renders the screen for a signed-in %s user', async (role) => {
    stubMe(() => json(user([role])))
    open()
    expect(await screen.findByRole('heading', { name: 'Family screen' })).toBeInTheDocument()
  })

  it("sends a user of another role to their own client", async () => {
    stubMe(() => json(user(['ROLE_MANAGER'])))
    open()
    expect(await screen.findByRole('heading', { name: 'Manager console' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Family screen' })).not.toBeInTheDocument()
  })

  it('sends an account with no portal role to the landing page', async () => {
    stubMe(() => json(user(['ADMIN'])))
    open()
    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
  })

  it('returns to the landing page when any later request finds the session expired', async () => {
    stubMe(() => json(user(['FAMILY'])))
    open()
    await screen.findByRole('heading', { name: 'Family screen' })
    await act(async () => { await api('/protected').catch(() => {}) })
    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
  })

  it('does not treat a failed login as an expired session', async () => {
    stubMe(() => json(user(['FAMILY'])))
    const listener = vi.fn()
    window.addEventListener(SESSION_EXPIRED_EVENT, listener)
    await api('/auth/login', { method: 'POST' }).catch(() => {})
    window.removeEventListener(SESSION_EXPIRED_EVENT, listener)
    expect(listener).not.toHaveBeenCalled()
  })

  it('offers a retry, not the screen, when the session check keeps failing for another reason', { timeout: 10000 }, async () => {
    let fail = true
    stubMe(() => fail ? new Response(null, { status: 503 }) : json(user(['FAMILY'])))
    open()
    // Two retries with backoff (about 3 seconds) come first.
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent('Unable to check your sign-in')
    expect(screen.queryByRole('heading', { name: 'Family screen' })).not.toBeInTheDocument()
    fail = false
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Family screen' })).toBeInTheDocument()
  })

  it("drops every cached query on leaving, so the next account never sees this one's data", async () => {
    stubMe(() => new Response(null, { status: 401 }))
    const client = open()
    client.setQueryData(['elders'], ['cached elder'])
    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
    expect(client.getQueryData(['elders'])).toBeUndefined()
  })
})
