import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import FamilyHome from '../index'
import type { FamilyElderProfile } from '../../../features/family-elders/api'

const elder: FamilyElderProfile = {
  id: 1, fullName: 'Tan Mei', gender: 'FEMALE', dateOfBirth: '1948-02-03', phone: '81234567',
  address: '12 Example Road', postalCode: '123456', preferredDialects: 'Hokkien', livesAlone: true,
  mobilityLevel: 'INDEPENDENT', accessScope: 'FULL',
}
const readOnly: FamilyElderProfile = {
  ...elder, id: 2, fullName: 'Lim Ai Hua', accessScope: 'READ_ONLY', dateOfBirth: null, address: null,
}
let profiles: FamilyElderProfile[]
function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
function installApi(override?: (path: string, init?: RequestInit) => Response | Promise<Response> | undefined) {
  const request = vi.fn(async (path: string, init?: RequestInit) => {
    const response = override?.(path, init)
    if (response !== undefined) return response
    if (path === '/api/auth/me') return json({ id: 7, username: 'family', displayName: 'Family', roles: ['FAMILY'] })
    if (path === '/api/elders') return json(profiles.map((item) => ({ ...item, sector: 'AMK', planStatus: 'none' })))
    if (path === '/api/family/elders') return json(profiles)
    const id = Number(path.split('/').at(-1))
    const selected = profiles.find((item) => item.id === id)
    if (path.startsWith('/api/family/elders/') && selected) {
      if (init?.method === 'PUT') {
        profiles = profiles.map((item) => item.id === id ? { ...item, ...JSON.parse(init.body as string) } : item)
      }
      return json(profiles.find((item) => item.id === id))
    }
    throw new Error(`Unexpected API request: ${path}`)
  })
  vi.stubGlobal('fetch', request)
  return request
}
function open(path = '/family/elders', client = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  render(<QueryClientProvider client={client}>
    <MemoryRouter initialEntries={[path]}>
      <Link to="/family/elders/2">Switch to Lim Ai Hua</Link>
      <Routes><Route path="/family/*" element={<FamilyHome />} /></Routes>
    </MemoryRouter>
  </QueryClientProvider>)
  return client
}
beforeEach(() => {
  profiles = [elder, readOnly]
  vi.stubGlobal('scrollTo', vi.fn())
  document.cookie = 'XSRF-TOKEN=test-token; path=/'
})
afterEach(() => {
  cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('My elders and basic details', () => {
  it('lists bound elders with scopes and links to their individual profiles', async () => {
    installApi(); open()
    expect(await screen.findByRole('heading', { name: 'Tan Mei' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Lim Ai Hua' })).toBeInTheDocument()
    expect(screen.getByText('Full access')).toBeInTheDocument()
    expect(screen.getByText('Read-only')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View details for Tan Mei' })).toHaveAttribute('href', '/family/elders/1')
    expect(screen.getByText('Date of birth not recorded')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Review binding requests/ })).toHaveAttribute('href', '/family/family-bindings')
  })

  it('guides an unbound family to binding requests', async () => {
    profiles = []; installApi(); open()
    expect(await screen.findByRole('heading', { name: 'No linked elders yet' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Go to Family bindings/ })).toHaveAttribute('href', '/family/family-bindings')
  })

  it('shows an error and retries the elder list', async () => {
    let unavailable = true
    installApi((path) => path === '/api/family/elders' && unavailable ? json({}, 403) : undefined)
    open()
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load your elders')
    unavailable = false
    await userEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByRole('heading', { name: 'Tan Mei' })).toBeInTheDocument()
  })

  it('shows read-only details without an editor', async () => {
    installApi(); open('/family/elders/2')
    expect(await screen.findByRole('heading', { name: 'Lim Ai Hua' })).toBeInTheDocument()
    expect(screen.getByText(/Read-only · You can view/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit basic details' })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.getAllByText('Not recorded')).toHaveLength(2)
  })

  it('saves only allowed details and refreshes the shared elder selector cache', async () => {
    const request = installApi()
    const user = userEvent.setup()
    const client = open('/family/elders/1')
    client.setQueryData(['elders', 'family'], [{ id: 1, fullName: 'Old name' }])
    await user.click(await screen.findByRole('button', { name: 'Edit basic details' }))
    await user.clear(screen.getByLabelText('Full name *'))
    await user.type(screen.getByLabelText('Full name *'), 'Tan Mei Updated')
    await user.clear(screen.getByLabelText('Home address'))
    await user.type(screen.getByLabelText('Home address'), '21 New Example Road')
    await user.clear(screen.getByLabelText('Phone'))
    await user.selectOptions(screen.getByLabelText('Mobility'), 'ASSISTIVE_CANE')
    await user.click(screen.getByRole('button', { name: 'Save details' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Basic details saved.')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Tan Mei Updated')
    const [, init] = request.mock.calls.find(([, init]) => init?.method === 'PUT')!
    const submitted = JSON.parse(init!.body as string)
    expect(submitted).toEqual({
      fullName: 'Tan Mei Updated', gender: 'FEMALE', dateOfBirth: '1948-02-03', phone: null,
      address: '21 New Example Road', postalCode: '123456', preferredDialects: 'Hokkien',
      livesAlone: true, mobilityLevel: 'ASSISTIVE_CANE',
    })
    expect(new Headers(init!.headers).get('X-XSRF-TOKEN')).toBe('test-token')
    expect(client.getQueryState(['elders', 'family'])?.isInvalidated).toBe(true)
    expect(client.getQueryData(['family-elder-profiles', 1])).toMatchObject({ fullName: 'Tan Mei Updated' })
  })

  it('keeps the draft and reports a failed save without a success notice', async () => {
    installApi((path, init) => path === '/api/family/elders/1' && init?.method === 'PUT' ? json({}, 503) : undefined)
    const user = userEvent.setup(); open('/family/elders/1')
    await user.click(await screen.findByRole('button', { name: 'Edit basic details' }))
    await user.clear(screen.getByLabelText('Full name *')); await user.type(screen.getByLabelText('Full name *'), 'Unsaved name')
    await user.click(screen.getByRole('button', { name: 'Save details' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Your changes have not been saved')
    expect(screen.getByLabelText('Full name *')).toHaveValue('Unsaved name')
    expect(screen.queryByText('Basic details saved.')).not.toBeInTheDocument()
    expect(profiles[0].fullName).toBe('Tan Mei')
  })

  it('hides cached protected details when a binding is revoked while editing', async () => {
    let revoked = false
    installApi((path, init) => {
      if (path !== '/api/family/elders/1') return
      if (init?.method === 'PUT') revoked = true
      if (revoked) return json({}, 403)
    })
    const user = userEvent.setup(); open('/family/elders/1')
    await user.click(await screen.findByRole('button', { name: 'Edit basic details' }))
    await user.click(screen.getByRole('button', { name: 'Save details' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Check your current binding')
    expect(screen.queryByText('12 Example Road')).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByText('Basic details saved.')).not.toBeInTheDocument()
  })

  it('discards unsaved details when switching to a different elder', async () => {
    installApi(); const user = userEvent.setup(); open('/family/elders/1')
    await user.click(await screen.findByRole('button', { name: 'Edit basic details' }))
    await user.clear(screen.getByLabelText('Full name *')); await user.type(screen.getByLabelText('Full name *'), 'Unsaved name')
    await user.click(screen.getByRole('link', { name: 'Switch to Lim Ai Hua' }))
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Lim Ai Hua')
    expect(screen.queryByDisplayValue('Unsaved name')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save details' })).not.toBeInTheDocument()
  })

  it('cancels an edit and restores saved values when reopened', async () => {
    installApi(); const user = userEvent.setup(); open('/family/elders/1')
    await user.click(await screen.findByRole('button', { name: 'Edit basic details' }))
    await user.clear(screen.getByLabelText('Full name *')); await user.type(screen.getByLabelText('Full name *'), 'Unsaved name')
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    await user.click(screen.getByRole('button', { name: 'Edit basic details' }))
    expect(screen.getByLabelText('Full name *')).toHaveValue('Tan Mei')
  })

  it('rejects invalid postal codes before a request is sent', async () => {
    const request = installApi(); const user = userEvent.setup(); open('/family/elders/1')
    await user.click(await screen.findByRole('button', { name: 'Edit basic details' }))
    await user.clear(screen.getByLabelText('Postal code')); await user.type(screen.getByLabelText('Postal code'), 'ABCDEF')
    await user.click(screen.getByRole('button', { name: 'Save details' }))
    expect(screen.getByLabelText('Postal code')).toBeInvalid()
    expect(request.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false)
  })

  it('disables the editor during save to prevent duplicate submissions', async () => {
    let finish!: (response: Response) => void
    const request = installApi((path, init) => path === '/api/family/elders/1' && init?.method === 'PUT'
      ? new Promise<Response>((resolve) => { finish = resolve }) : undefined)
    const user = userEvent.setup(); open('/family/elders/1')
    await user.click(await screen.findByRole('button', { name: 'Edit basic details' }))
    await user.click(screen.getByRole('button', { name: 'Save details' }))
    expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled()
    expect(screen.getByLabelText('Full name *')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
    finish(json(elder))
    await screen.findByText('Basic details saved.')
    expect(request.mock.calls.filter(([, init]) => init?.method === 'PUT')).toHaveLength(1)
  })

  it('can update or clear optional gender and living arrangements', async () => {
    const request = installApi(); const user = userEvent.setup(); open('/family/elders/1')
    await user.click(await screen.findByRole('button', { name: 'Edit basic details' }))
    await user.selectOptions(screen.getByLabelText('Gender'), 'OTHER')
    await user.selectOptions(screen.getByLabelText('Lives alone'), 'false')
    await user.click(screen.getByRole('button', { name: 'Save details' }))
    await screen.findByText('Basic details saved.')
    expect(profiles[0]).toMatchObject({ gender: 'OTHER', livesAlone: false })
    await user.click(screen.getByRole('button', { name: 'Edit basic details' }))
    await user.selectOptions(screen.getByLabelText('Gender'), '')
    await user.selectOptions(screen.getByLabelText('Lives alone'), '')
    await user.click(screen.getByRole('button', { name: 'Save details' }))
    await screen.findByText('Basic details saved.')
    const submissions = request.mock.calls.filter(([, init]) => init?.method === 'PUT')
    expect(JSON.parse(submissions[1][1]!.body as string)).toMatchObject({ gender: null, livesAlone: null })
  })

  it('allows retry when profile access becomes available again', async () => {
    let denied = true
    installApi((path) => path === '/api/family/elders/1' && denied ? json({}, 403) : undefined)
    open('/family/elders/1')
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to view this profile')
    denied = false
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Tan Mei')
    expect(screen.getByRole('button', { name: 'Edit basic details' })).toBeEnabled()
  })

  it('does not request malformed elder IDs', async () => {
    const request = installApi(); open('/family/elders/not-an-id')
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid elder profile')
    expect(request).not.toHaveBeenCalled()
  })

  it('confirms a binding and refreshes both cached elder lists without signing in again', async () => {
    let active = false
    profiles = []
    installApi((path, init) => {
      if (path === '/api/family/family-bindings') return json([{ id: 42, elderId: 1, elderName: 'Tan Mei',
        relationship: 'DAUGHTER', accessScope: 'FULL', status: active ? 'ACTIVE' : 'PENDING_CONFIRMATION' }])
      if (path === '/api/family/family-bindings/42/decision' && init?.method === 'POST') {
        active = true; profiles = [elder]
        return json({ id: 42, elderId: 1, status: 'ACTIVE' })
      }
    })
    const user = userEvent.setup()
    const client = new QueryClient()
    client.setQueryData(['elders', 'family'], [])
    client.setQueryData(['family-elder-profiles'], [])
    open('/family/family-bindings', client)
    await user.click(await screen.findByRole('button', { name: 'Confirm binding' }))
    expect(await screen.findByText('Binding confirmed.')).toBeInTheDocument()
    expect(client.getQueryState(['elders', 'family'])?.isInvalidated).toBe(true)
    expect(client.getQueryState(['family-elder-profiles'])?.isInvalidated).toBe(true)
    // The active page uses the freshly fetched server list, rather than the formerly empty cache.
    await user.click(screen.getByRole('link', { name: 'Account' }))
    const following = await screen.findByRole('region', { name: 'Following' })
    await user.click(await within(following).findByRole('link', { name: /My elders/ }))
    expect(await screen.findByRole('heading', { name: 'Tan Mei' })).toBeInTheDocument()
    await waitFor(() => expect(client.getQueryData(['family-elder-profiles'])).toEqual([elder]))
  })
})
