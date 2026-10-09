import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import FamilyHome from './index'

const family = { id: 11, username: 'wei_ling', displayName: 'Lim Wei Ling', roles: ['FAMILY'] }
const elders = [
  { id: 21, fullName: 'Chan Bee Choo', dateOfBirth: '1948-03-02', address: null, sector: 'AMK', planStatus: 'published', planVersion: 1, nextVisitDate: null },
  { id: 22, fullName: 'Lim Ah Kow', dateOfBirth: null, address: null, sector: null, planStatus: 'none', planVersion: null, nextVisitDate: null },
]

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
function installApi(override?: (url: URL, init?: RequestInit) => Response | undefined) {
  const fetchMock = vi.fn((path: string, init?: RequestInit) => {
    const url = new URL(path, 'http://localhost')
    const response = override?.(url, init)
    if (response !== undefined) return Promise.resolve(response)
    if (url.pathname === '/api/auth/me') return Promise.resolve(json(family))
    if (url.pathname === '/api/elders') return Promise.resolve(json(elders))
    if (url.pathname === '/api/auth/csrf') {
      document.cookie = 'XSRF-TOKEN=token; path=/'
      return Promise.resolve(new Response(null, { status: 200 }))
    }
    if (url.pathname === '/api/auth/logout') return Promise.resolve(new Response(null, { status: 204 }))
    throw new Error(`Unexpected API request: ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
function openAccount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/family/account']}>
        <Routes>
          <Route path="/" element={<h1>Landing</h1>} />
          <Route path="/family/*" element={<FamilyHome />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-09-28T02:30:00Z'))
  vi.stubGlobal('scrollTo', vi.fn())
})
afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('Family account', () => {
  it('shows the family member, who they follow, notifications and help under the Account tab', async () => {
    installApi()
    openAccount()

    expect(await screen.findByRole('heading', { level: 1, name: 'Lim Wei Ling' })).toBeInTheDocument()
    expect(screen.getByText('LW')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Account' })).toHaveAttribute('aria-current', 'page')
    expect(document.title).toBe('Account · CareLink')

    const following = screen.getByRole('region', { name: 'Following' })
    expect(await within(following).findByText('Chan Bee Choo')).toBeInTheDocument()
    expect(within(following).getByText('78 · AMK')).toBeInTheDocument()
    expect(within(following).getByText('Lim Ah Kow')).toBeInTheDocument()
    expect(within(following).getByText('Linked elder')).toBeInTheDocument()
    expect(within(following).getAllByText('LINKED')).toHaveLength(2)
    expect(within(following).getByRole('link', { name: /My elders/ })).toHaveAttribute('href', '/family/elders')
    expect(within(following).getByRole('link', { name: /Review binding requests/ })).toHaveAttribute('href', '/family/family-bindings')
    expect(within(following).getByRole('link', { name: 'Chan Bee Choo' })).toHaveAttribute('href', '/family/elders/21')

    const notifications = screen.getByRole('region', { name: 'Notifications' })
    expect(within(notifications).getByText('Urgent alerts').nextSibling).toHaveTextContent('App and email')
    expect(within(notifications).getByText('Weekly summary').nextSibling).toHaveTextContent('Mondays, 09:00')
    expect(within(screen.getByRole('region', { name: 'Help' })).getByText('Call the care manager')).toBeInTheDocument()
    expect(screen.getByText(/This phone stops receiving alerts/)).toBeInTheDocument()
  })

  it('signs out and returns to the landing page', async () => {
    const fetchMock = installApi()
    const user = userEvent.setup()
    openAccount()

    await user.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([path, init]) => path === '/api/auth/logout' && init?.method === 'POST')).toBe(true)
  })

  it('treats an expired session as already signed out', async () => {
    const user = userEvent.setup()
    installApi((url) => url.pathname === '/api/auth/logout' ? new Response(null, { status: 401 }) : undefined)
    openAccount()

    await user.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
  })

  it('stays on Account and explains when sign-out fails', async () => {
    const user = userEvent.setup()
    installApi((url) => url.pathname === '/api/auth/logout' ? new Response(null, { status: 500 }) : undefined)
    openAccount()

    await user.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to sign out. Please try again.')
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeEnabled()
  })

  it('says so when no elder is linked yet', async () => {
    installApi((url) => url.pathname === '/api/elders' ? json([]) : undefined)
    openAccount()
    expect(await screen.findByText('No linked elders yet.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /My elders/ })).toBeInTheDocument()
  })

  it('returns to the landing page when the session has expired', async () => {
    installApi((url) => url.pathname === '/api/auth/me' ? new Response(null, { status: 401 }) : undefined)
    openAccount()
    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()
  })
})
