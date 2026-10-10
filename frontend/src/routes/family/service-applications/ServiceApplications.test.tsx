import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import FamilyHome from '../index'
import type { FamilyElderProfile } from '../../../features/family-elders/api'
import type { ServiceApplication } from '../../../features/service-applications/api'
import { serviceApplicationKey } from '../../../features/service-applications/queries'

// The navigation's waiting counts are tested with FamilyLayout and on Home; here they stay at zero so
// only this page's own requests are made.
vi.mock('../components/usePendingDecisions', () => ({
  usePendingDecisions: () => ({ changes: [], spotChecks: [], requests: [], total: 0 }),
}))

const elder: FamilyElderProfile = {
  id: 1, fullName: 'Tan Mei', gender: 'FEMALE', dateOfBirth: '1948-02-03', phone: '81234567',
  address: '12 Example Road', postalCode: '012345', preferredDialects: 'Hokkien', livesAlone: true,
  mobilityLevel: 'INDEPENDENT', accessScope: 'FULL',
}
const second = { ...elder, id: 2, fullName: 'Chen Li', address: '34 Second Road', phone: null }
const readonly = { ...elder, id: 3, fullName: 'Lim Ai Hua', accessScope: 'READ_ONLY' as const }
const saved: ServiceApplication = { id: 23, elderId: 1, elderSnapshot: elder, careNeeds: ['BATHING'], notes: 'Morning visits', status: 'SUBMITTED', createdAt: '2026-10-09T01:00:00Z',
  outcome: 'SUBMITTED', needs: [{ need: 'BATHING', plannedVersion: null, plannedFrom: null }], declineReason: null, declinedAt: null }
const catalog = [
  { code: 'BATHING', label: 'Bathing assistance', category: 'Personal care' },
  { code: 'MEAL_SUPPORT', label: 'Meal support', category: 'Personal care' },
  { code: 'VITALS', label: 'Vital-sign check', category: 'Health monitoring' },
]
let profiles: FamilyElderProfile[]
function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
function install(override?: (path: string, init: RequestInit) => Response | Promise<Response> | undefined) {
  const fetch = vi.fn(async (path: string, init: RequestInit) => {
    const result = override?.(path, init)
    if (result !== undefined) return result
    if (path === '/api/auth/csrf') { document.cookie = 'XSRF-TOKEN=service-token; path=/'; return new Response(null) }
    if (path === '/api/auth/me') return json({ id: 7, username: 'family', roles: ['FAMILY'] })
    if (path === '/api/elders') return json(profiles)
    if (path === '/api/care-activities') return json(catalog)
    if (path === '/api/family/elders') return json(profiles)
    if (path.startsWith('/api/family/elders/')) return json(profiles.find((e) => e.id === Number(path.split('/').at(-1))))
    if (path === '/api/family/service-applications' && init.method === 'POST') return json(saved, 201)
    if (path === '/api/family/service-applications/23') return json(saved)
    if (path.startsWith('/api/family/service-applications?')) return json({ items: [saved], page: 0, size: 20, totalElements: 1 })
    if (path.startsWith('/api/intake-applications?')) return json({ items: [], page: 0, size: 20, totalElements: 0 })
    throw new Error(`Unexpected request: ${path}`)
  })
  vi.stubGlobal('fetch', fetch)
  return fetch
}
function open(path = '/family/service-applications/new') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const view = render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[path]}>
    <Routes><Route path="/" element={<h1>Landing</h1>} /><Route path="/family/*" element={<FamilyHome />} /></Routes>
  </MemoryRouter></QueryClientProvider>)
  return { client, ...view }
}
const submit = () => screen.getByRole('button', { name: 'Submit service application' })
async function fill() {
  const user = userEvent.setup()
  await screen.findByLabelText('Elder')
  await user.click(screen.getByLabelText('Bathing assistance'))
  return user
}
beforeEach(() => { profiles = [elder, second, readonly]; vi.stubGlobal('scrollTo', vi.fn()) })
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/' })

describe('Bound elder service applications', () => {
  it('redirects the old create URL and submits only existing elderId, catalog care needs and notes with CSRF', async () => {
    const fetch = install(); open('/family/intake/new')
    const user = await fill()
    expect(screen.getByLabelText('Elder')).toHaveValue('1')
    expect(screen.getByText('12 Example Road')).toBeInTheDocument()
    expect(screen.queryByLabelText('Elder full name')).not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /Lim Ai Hua/ })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Other care needs')).not.toBeInTheDocument()
    await user.click(screen.getByLabelText('Meal support'))
    await user.type(screen.getByLabelText('Notes for this application (optional)'), ' Morning visits ')
    await user.click(submit())
    expect(await screen.findByRole('heading', { name: 'Application submitted' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Elder details at submission' })).toBeInTheDocument()
    const posts = fetch.mock.calls.filter(([, init]) => init.method === 'POST')
    expect(posts).toHaveLength(1)
    expect(posts[0][0]).toBe('/api/family/service-applications')
    expect(JSON.parse(posts[0][1].body as string)).toEqual({ elderId: 1, careNeeds: ['BATHING', 'MEAL_SUPPORT'], notes: 'Morning visits' })
    expect(new Headers(posts[0][1].headers).get('X-XSRF-TOKEN')).toBe('service-token')
    expect(posts[0][1].credentials).toBe('include')
    expect(screen.getByText(/waiting for review/)).toBeInTheDocument()
  })

  it('switches profiles and clears the previous elder’s service draft', async () => {
    install(); open(); const user = await fill()
    await user.type(screen.getByLabelText('Notes for this application (optional)'), 'Tan only')
    await user.selectOptions(screen.getByLabelText('Elder'), '2')
    expect(screen.getByText('34 Second Road')).toBeInTheDocument()
    expect(screen.queryByText('12 Example Road')).not.toBeInTheDocument()
    expect(screen.queryByText('81234567')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Bathing assistance')).not.toBeChecked()
    expect(screen.getByLabelText('Notes for this application (optional)')).toHaveValue('')
    await user.click(screen.getByLabelText('Vital-sign check')); await user.click(submit())
  })

  it('preserves the elder chosen on My elders when entering the application', async () => {
    install(); open('/family/elders/2')
    await screen.findByRole('heading', { name: 'Chen Li' })
    await userEvent.click(screen.getByRole('button', { name: 'Menu' }))
    await userEvent.click(screen.getByRole('link', { name: 'Care applications' }))
    await userEvent.click(await screen.findByRole('link', { name: 'New service application' }))
    expect(await screen.findByLabelText('Elder')).toHaveValue('2')
  })

  it('does not silently choose another person when the shared elder has read-only access', async () => {
    install(); open('/family/elders/3'); await screen.findByRole('heading', { name: 'Lim Ai Hua' })
    await userEvent.click(screen.getByRole('button', { name: 'Menu' }))
    await userEvent.click(screen.getByRole('link', { name: 'Care applications' }))
    await userEvent.click(await screen.findByRole('link', { name: 'New service application' }))
    expect(await screen.findByLabelText('Elder')).toHaveValue('')
    expect(submit()).toBeDisabled()
  })

  it.each([{ data: [] }, { data: [readonly] }])('blocks submission without a FULL binding ($data)', async ({ data }) => {
    profiles = data; const fetch = install(); open()
    expect(await screen.findByRole('heading', { name: 'No elders available for a service application' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Review binding requests' })).toHaveAttribute('href', '/family/family-bindings')
    expect(screen.queryByRole('button', { name: 'Submit service application' })).not.toBeInTheDocument()
    expect(fetch.mock.calls.some(([, init]) => init.method === 'POST')).toBe(false)
  })

  it.each([{ address: null }, { postalCode: '123' }, { fullName: '  ' }])('requires saved mandatory fields %j', async (patch) => {
    profiles = [{ ...elder, ...patch }]; install(); open(); await screen.findByLabelText('Elder')
    expect(submit()).toBeDisabled()
    expect(screen.getByText(/Complete the elder's saved name/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /View or update basic details/ })).toHaveAttribute('href', '/family/elders/1')
  })

  it('validates services and notes before sending', async () => {
    const fetch = install(); open(); await screen.findByLabelText('Elder'); await userEvent.click(submit())
    expect(await screen.findByText('Choose at least one care service.')).toBeInTheDocument()
    await userEvent.click(screen.getByLabelText('Vital-sign check'))
    fireEvent.change(screen.getByLabelText('Notes for this application (optional)'), { target: { value: 'a'.repeat(2001) } }); await userEvent.click(submit())
    expect(screen.getByText('Notes must be 2000 characters or fewer.')).toBeInTheDocument()
    expect(screen.getByLabelText('Notes for this application (optional)')).toHaveAttribute('aria-invalid', 'true')
    expect(fetch.mock.calls.some(([, init]) => init.method === 'POST')).toBe(false)
  })

  it('offers the care activity catalog, grouped by category, as the only care services', async () => {
    install(); open(); await screen.findByLabelText('Elder')
    expect(await screen.findByRole('group', { name: 'Personal care' })).toHaveTextContent('Bathing assistanceMeal support')
    expect(screen.getByRole('group', { name: 'Health monitoring' })).toHaveTextContent('Vital-sign check')
  })

  it('does not offer care services it could not load', async () => {
    let failing = true
    install((path) => path === '/api/care-activities' && failing ? json({}, 500) : undefined); open()
    expect(await screen.findByText('Unable to load care services.')).toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    failing = false; await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByLabelText('Bathing assistance')).toBeInTheDocument()
  })

  it('disables repeat submission while pending and aborts on unmount', async () => {
    let signal: AbortSignal | null | undefined
    const fetch = install((_path, init) => init.method === 'POST' ? new Promise<Response>(() => { signal = init.signal }) : undefined)
    const view = open(); const user = await fill(); await user.dblClick(submit())
    expect(screen.getByRole('button', { name: 'Submitting…' })).toBeDisabled()
    expect(fetch.mock.calls.filter(([, init]) => init.method === 'POST')).toHaveLength(1)
    view.unmount(); expect(signal?.aborted).toBe(true)
  })

  it.each([403, 404, 409])('blocks and retains draft after permission/profile failure %s', async (status) => {
    const fetch = install((_path, init) => init.method === 'POST' ? json({}, status) : undefined)
    open(); const user = await fill(); await user.click(submit())
    expect(await screen.findByRole('alert')).toHaveTextContent(status === 409 ? 'Complete the saved name' : 'Submission not permitted')
    expect(screen.queryByText('12 Example Road')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Bathing assistance')).toBeChecked(); expect(submit()).toBeDisabled()
    profiles = [readonly]
    await user.click(screen.getByRole('button', { name: 'Refresh elder access' }))
    await screen.findByRole('heading', { name: 'No elders available for a service application' })
    expect(fetch.mock.calls.filter(([, init]) => init.method === 'POST')).toHaveLength(1)
  })

  it.each([500, 400])('does not report success after a %s response and keeps entries', async (status) => {
    const fetch = install((_path, init) => init.method === 'POST' ? json({}, status) : undefined)
    open(); const user = await fill(); await user.click(submit())
    expect(await screen.findByRole('alert')).toHaveTextContent(status === 500 ? 'Submission status unknown' : 'not accepted')
    expect(screen.getByLabelText('Bathing assistance')).toBeChecked()
    expect(screen.queryByRole('heading', { name: 'Application submitted' })).not.toBeInTheDocument()
    expect(fetch.mock.calls.filter(([, init]) => init.method === 'POST')).toHaveLength(1)
    if (status === 500) expect(screen.getByRole('link', { name: /Check my service applications/ })).toHaveAttribute('target', '_blank')
  })

  it('treats a malformed successful response as unconfirmed without retrying POST', async () => {
    const fetch = install((_path, init) => init.method === 'POST' ? json({}, 201) : undefined)
    open(); const user = await fill(); await user.click(submit())
    expect(await screen.findByRole('alert')).toHaveTextContent('Submission status unknown')
    expect(fetch.mock.calls.filter(([, init]) => init.method === 'POST')).toHaveLength(1)
  })

  it('does not POST when CSRF preparation fails', async () => {
    const fetch = install((path) => path.endsWith('/csrf') ? json({}, 500) : undefined)
    open(); const user = await fill(); await user.click(submit())
    expect(await screen.findByRole('alert')).toHaveTextContent('has not been sent')
    expect(fetch.mock.calls.some(([, init]) => init.method === 'POST')).toBe(false)
  })

  it('returns to sign-in after an expired submission session', async () => {
    install((_path, init) => init.method === 'POST' ? json({}, 401) : undefined)
    open(); const user = await fill(); await user.click(submit())
    expect(await screen.findByRole('heading', { name: 'Landing' })).toBeInTheDocument()
  })

  it('keeps the confirmed receipt even when the detail read fails', async () => {
    install((path) => path.endsWith('/service-applications/23') ? json({}, 403) : undefined)
    open(); const user = await fill(); await user.click(submit())
    expect(await screen.findByRole('heading', { name: 'Application submitted' })).toBeInTheDocument()
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to view')
  })

  it('shows the saved snapshot rather than the current profile, then hides it if refresh loses access', async () => {
    let denied = false
    profiles = [{ ...elder, address: 'Changed address' }]
    install((path) => path.endsWith('/service-applications/23') && denied ? json({}, 403) : undefined)
    open('/family/service-applications/23'); await screen.findByText('12 Example Road')
    expect(screen.queryByText('Changed address')).not.toBeInTheDocument()
    denied = true; await userEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByRole('alert'); expect(screen.queryByText('12 Example Road')).not.toBeInTheDocument()
  })

  it('invalidates cached lists after a successful submission', async () => {
    install(); const { client } = open(); client.setQueryData([...serviceApplicationKey, 'list', 0], { items: [], totalElements: 0 })
    const user = await fill(); await user.click(submit()); await screen.findByRole('heading', { name: 'Application submitted' })
    expect(client.getQueryState([...serviceApplicationKey, 'list', 0])?.isInvalidated).toBe(true)
  })

  it('loads paginated applications, preserves the return page, and offers legacy history', async () => {
    install((path) => path.includes('/service-applications?') ? json({ items: [saved], page: path.includes('page=1') ? 1 : 0, size: 20, totalElements: 21 }) : undefined)
    open('/family/service-applications'); await screen.findByText('21 service applications')
    const user = userEvent.setup(); await user.click(screen.getByRole('button', { name: 'Next' }))
    await screen.findByText('Page 2 of 2')
    await user.click(screen.getByRole('link', { name: /SERVICE APPLICATION #23/ }))
    expect(await screen.findByRole('link', { name: /Back to service applications/ })).toHaveAttribute('href', '/family/service-applications?page=1')
    await user.click(screen.getByRole('link', { name: /Back to service applications/ }))
    await user.click(await screen.findByRole('link', { name: 'Earlier registration applications' }))
    expect(await screen.findByText('EARLIER REGISTRATION APPLICATIONS')).toBeInTheDocument()
  })

  it('shows an empty out-of-range page with a first-page link', async () => {
    const fetch = install((path) => path.includes('/service-applications?') ? json({ items: [], page: 99, size: 20, totalElements: 0 }) : undefined)
    open('/family/service-applications?page=99'); await screen.findByRole('heading', { name: 'No applications on this page' })
    await userEvent.click(screen.getByRole('button', { name: 'Back to first page' }))
    await waitFor(() => expect(fetch.mock.calls.some(([path]) => path.endsWith('?page=0&size=20'))).toBe(true))
  })

  it('hides a cached list after a failed access recheck', async () => {
    let denied = false
    install((path) => path.includes('/service-applications?') && denied ? json({}, 403) : undefined)
    open('/family/service-applications'); await screen.findByRole('link', { name: /SERVICE APPLICATION #23/ })
    denied = true; await userEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByRole('alert'); expect(screen.queryByText('12 Example Road')).not.toBeInTheDocument()
  })

  it('retries loading profiles without sending an application', async () => {
    let denied = true
    install((path) => path === '/api/family/elders' && denied ? json({}, 403) : undefined)
    open(); await screen.findByRole('alert'); denied = false
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByLabelText('Elder')).toHaveValue('1')
  })

  it('does not retain a profile after a background binding refresh removes it', async () => {
    install(); const { client } = open(); const user = await fill()
    await user.selectOptions(screen.getByLabelText('Elder'), '2')
    profiles = [elder]
    await act(async () => { await client.invalidateQueries({ queryKey: ['family-elder-profiles'] }) })
    await waitFor(() => expect(screen.getByLabelText('Elder')).toHaveValue(''))
    expect(screen.queryByText('34 Second Road')).not.toBeInTheDocument()
    expect(submit()).toBeDisabled()
  })

  it('shows an application the care plan has answered, activity by activity, with a way to the plan', async () => {
    install((path) => path.endsWith('/service-applications/23') ? json({ ...saved, careNeeds: ['BATHING', 'VITALS'], outcome: 'PLANNED',
      needs: [{ need: 'BATHING', plannedVersion: 2, plannedFrom: '2026-10-20' }, { need: 'VITALS', plannedVersion: 2, plannedFrom: '2026-10-20' }] }) : undefined)
    open('/family/service-applications/23')
    expect(await screen.findByText('Planned')).toBeInTheDocument()
    expect(screen.getByText(/Everything you asked for is in your elder's care plan/)).toBeInTheDocument()
    expect(await screen.findByText(/Bathing assistance — In care plan v2 from 20 Oct 2026/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'See the care plan' })).toHaveAttribute('href', '/family/care-plan')
  })

  it('tells the family why the care team declined an application', async () => {
    install((path) => path.endsWith('/service-applications/23') ? json({ ...saved, status: 'DECLINED', outcome: 'DECLINED',
      declineReason: 'No caregiver free for morning visits yet', declinedAt: '2026-10-10T02:00:00Z' }) : undefined)
    open('/family/service-applications/23')
    expect(await screen.findByText('Declined')).toBeInTheDocument()
    expect(screen.getByText('Reason: No caregiver free for morning visits yet')).toBeInTheDocument()
    expect(screen.getByText(/Bathing assistance — Not in the care plan yet/)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'See the care plan' })).not.toBeInTheDocument()
  })

  it('badges each application in the list with its outcome', async () => {
    install((path) => path.includes('/service-applications?') ? json({ items: [{ ...saved, outcome: 'PLANNED' }], page: 0, size: 20, totalElements: 1 }) : undefined)
    open('/family/service-applications')
    expect(within(await screen.findByRole('link', { name: /SERVICE APPLICATION #23/ })).getByText('Planned')).toBeInTheDocument()
  })

  it('rejects invalid detail references without requesting a record', async () => {
    const fetch = install(); open('/family/service-applications/not-an-id')
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid application reference')
    expect(fetch.mock.calls.some(([path]) => path.includes('service-applications'))).toBe(false)
  })
})
