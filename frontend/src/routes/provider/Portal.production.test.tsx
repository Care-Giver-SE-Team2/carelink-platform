import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeEach, expect, it, vi } from 'vitest'
import { LIVE, submitApplication } from '../../features/provider-platform/api'
import { WorkbenchProvider } from './WorkbenchProvider'
import { useWorkbench } from './workbenchContext'
import Credentials from './pages/Credentials'
import Status from './pages/Status'
import Webhooks from './pages/Webhooks'

vi.hoisted(() => {
  vi.stubEnv('PROD', true)
  vi.stubEnv('MODE', 'production')
  vi.stubEnv('VITE_OPEN_PLATFORM_MOCK', '')
})

const details = {
  organisationName: 'Contract fixture', contactName: 'Test operator', contactEmail: 'operator@example.invalid',
  contactPhone: '+6591234567', services: ['Companionship'], webhookUrl: 'https://hooks.example.invalid/carelink',
}
const credentials = { clientId: 'contract-client', clientSecret: 'contract-client-secret', webhookSecret: 'contract-webhook-secret' }
const at = '2026-10-18T00:00:00Z'
const fetchMock = vi.fn<typeof fetch>()
let webhookUrl: string
let sentDelivery: object | null

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  fetchMock.mockReset()
  webhookUrl = details.webhookUrl
  sentDelivery = null
  vi.stubGlobal('fetch', fetchMock)
  // These are local HTTP substitutes; no request is sent to staging, a partner or AWS.
  fetchMock.mockImplementation(async (input, init) => {
    const path = String(input)
    let body: unknown
    switch (path) {
      case '/api/open/v1/applications': body = { applicationId: 'APP-CONTRACT' }; break
      case '/api/open/v1/portal/sessions': body = { accessToken: 'portal-contract-token', expiresIn: 3600 }; break
      case '/api/open/v1/portal/application': body = { ...details, id: 'APP-CONTRACT', status: 'APPROVED', submittedAt: at, reviewedAt: at, reason: null, suspended: false }; break
      case '/api/open/v1/portal/health': body = { delivered: 1, retrying: 0, failed: 0, lastDeliveredAt: at, suspended: false }; break
      case '/api/open/v1/portal/credentials/collect': body = credentials; break
      case '/api/open/v1/portal/credentials': body = { clientId: credentials.clientId, secretHint: 'cret', secretIssuedAt: at, scopes: ['open-platform/jobs.read'], tokenUrl: '/api/open/v1/oauth2/token' }; break
      case '/api/open/v1/portal/credentials/rotate': body = { clientId: credentials.clientId, clientSecret: 'contract-rotated-secret' }; break
      case '/api/open/v1/portal/webhook':
        if (init?.method === 'PUT') webhookUrl = JSON.parse(String(init.body)).url
        body = { url: webhookUrl, events: ['job.dispatched'], secretHint: 'cret', secretIssuedAt: at }; break
      case '/api/open/v1/portal/webhook/deliveries?size=100': body = { items: sentDelivery ? [sentDelivery] : [] }; break
      case '/api/open/v1/portal/webhook/test':
        sentDelivery = { id: 'dlv-contract', event: 'ping', jobId: null, createdAt: at, status: 'RETRYING', attempts: [], nextAttemptAt: at, request: { url: webhookUrl, headers: {}, body: '{}' } }
        body = sentDelivery; break
      default: throw new Error(`Unexpected HTTP request: ${path}`)
    }
    return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
  })
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})
afterAll(() => vi.unstubAllEnvs())

function PrefillProbe() {
  return <output aria-label="Sandbox prefill">{useWorkbench().connectPrefill === null ? 'empty' : 'present'}</output>
}

function portal(path = '/provider/status') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <WorkbenchProvider>
          <Routes>
            <Route path="/provider/status" element={<Status />} />
            <Route path="/provider/credentials" element={<Credentials />} />
            <Route path="/provider/webhooks" element={<Webhooks />} />
          </Routes>
          <PrefillProbe />
        </WorkbenchProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

it('keeps production onboarding, login, one-time credentials and webhook requests on the real adapter', async () => {
  expect(LIVE).toBe(true)
  expect(await submitApplication(details, 'contract-password')).toEqual({ applicationId: 'APP-CONTRACT' })
  expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toMatchObject({ ...details, password: 'contract-password', agreedToTerms: true })
  portal()
  await userEvent.type(screen.getByLabelText('Email'), details.contactEmail)
  await userEvent.type(screen.getByLabelText('Password'), 'contract-password')
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
  await userEvent.click(await screen.findByRole('button', { name: 'View credentials →' }))
  expect(await screen.findByRole('heading', { name: 'Your credentials' })).toBeInTheDocument()
  expect(screen.getByRole('status', { name: 'Client secret' })).toHaveTextContent(credentials.clientSecret)
  await userEvent.click(screen.getByRole('checkbox', { name: "I've stored all three values somewhere safe." }))
  await userEvent.click(screen.getByRole('button', { name: 'Continue' }))
  await screen.findByRole('heading', { name: 'API credentials' })
  await screen.findByText('cs_live_••••••••••••cret')
  expect(screen.queryByText(credentials.clientSecret)).not.toBeInTheDocument()
  expect(screen.getByLabelText('Sandbox prefill')).toHaveTextContent('empty')
  expect(screen.getByText(/The job API is not connected in this deployment\./)).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Rotate secret' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Rotate secret' }))
  const rotated = await screen.findByRole('region', { name: 'New client secret' })
  expect(within(rotated).getByRole('status', { name: 'Client secret' })).toHaveTextContent('contract-rotated-secret')
  await userEvent.click(within(rotated).getByRole('checkbox'))
  await userEvent.click(within(rotated).getByRole('button', { name: 'Done' }))
  await userEvent.click(within(screen.getByRole('navigation', { name: 'Partner Portal' })).getByRole('link', { name: 'Webhooks' }))
  const url = await screen.findByLabelText('Endpoint URL')
  await userEvent.clear(url)
  await userEvent.type(url, 'https://hooks.example.invalid/updated')
  await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
  await screen.findByText('Saved')
  await userEvent.click(screen.getByRole('button', { name: 'Send test event' }))
  await screen.findByRole('region', { name: 'Delivery dlv-contract' })
  expect(screen.getByText('Awaiting first attempt')).toBeInTheDocument()
  const saved = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/webhook') && init?.method === 'PUT')
  expect(JSON.parse(String(saved?.[1]?.body))).toMatchObject({ url: 'https://hooks.example.invalid/updated' })
  const authenticated = fetchMock.mock.calls.filter(([url]) => String(url).includes('/portal/') && !String(url).endsWith('/sessions'))
  expect(authenticated.length).toBeGreaterThan(5)
  for (const [, init] of authenticated) expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer portal-contract-token')
  expect(localStorage.length).toBe(0)
  expect(JSON.parse(sessionStorage.getItem('carelink.workbench.session')!).token).toBe('portal-contract-token')
})

it('restores a real portal session on reload and signs it out without reading old mock state', async () => {
  localStorage.setItem('carelink.mock.providerPlatform.v1', 'invalid legacy mock state')
  sessionStorage.setItem('carelink.workbench.session', JSON.stringify({ token: 'portal-contract-token', expiresAt: Date.now() + 3600_000 }))
  portal('/provider/credentials')
  await screen.findByText('cs_live_••••••••••••cret')
  await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
  await screen.findByRole('button', { name: 'Sign in' })
  expect(sessionStorage.getItem('carelink.workbench.session')).toBeNull()
  expect(localStorage.getItem('carelink.mock.providerPlatform.v1')).toBe('invalid legacy mock state')
})
