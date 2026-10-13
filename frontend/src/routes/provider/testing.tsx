import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { setApiLatency } from '../../features/provider-platform/api'
import { requestToken, resetMockPlatform, signIn } from '../../features/provider-platform/mockPlatform'
import ProviderWorkbench from '.'
import { LocationProbe } from './LocationProbe'

/** The seeded provider with history; see README.md. */
export const SEEDED = {
  applicationId: 'APP-2026-00409',
  email: 'partners@brighthome.sg',
  password: 'brighthome-demo',
  clientId: 'cl_prov_BrightHome9',
  clientSecret: 'cs_demo_brighthome',
}

/** A fresh mock platform, no latency, nothing remembered. */
export function resetWorkbench() {
  localStorage.clear()
  sessionStorage.clear()
  resetMockPlatform()
  setApiLatency(0)
}

/** Starts the Partner Portal already signed in to the portal as `email`, the seeded provider by default. */
export function signInAs(email = SEEDED.email, password = SEEDED.password) {
  const { session, expiresAt } = signIn(email, password)
  sessionStorage.setItem('carelink.workbench.session', JSON.stringify({ token: session, expiresAt }))
}

/** Starts the Partner Portal already holding a token for the seeded provider. */
export function connectSeeded() {
  const token = { ...requestToken(SEEDED.clientId, SEEDED.clientSecret), clientId: SEEDED.clientId }
  sessionStorage.setItem('carelink.workbench.token', JSON.stringify(token))
  return token
}

/** Renders the Partner Portal at `entry`, with the current location in a test id. */
export function renderWorkbench(entry: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/provider/*" element={<ProviderWorkbench />} />
        </Routes>
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}
