import { createContext, useContext } from 'react'
import type { PortalSession } from '../../features/provider-platform/api'
import type { AccessToken, Application, IssuedCredentials } from '../../features/provider-platform/types'

export type Connection = 'connected' | 'none' | 'error'

/** A Sandbox token, with the client ID it was issued to. */
export type SandboxToken = AccessToken & { clientId: string }

/**
 * What the Partner Portal knows about the provider using it. The provider signs in with the email
 * and password it chose when applying; the session and the Sandbox token are kept in
 * sessionStorage. `credentials` exist only between collecting them and leaving the API
 * credentials page, and are never stored.
 */
export type WorkbenchState = {
  /** The portal session while signed in. */
  session: PortalSession | null
  /** The signed-in provider's application. */
  application: Application | null
  /** Signs in; rejects with the ApiError (401 for a wrong email or password). */
  signIn: (email: string, password: string) => Promise<Application>
  /** Signs out of the portal and drops the Sandbox token. */
  signOut: () => void
  /** Fetches the application again, e.g. to see whether it has been decided. */
  refreshApplication: () => Promise<void>
  /** The session, once the signed-in provider is approved: what portal pages act with. */
  portal: PortalSession | null
  credentials: IssuedCredentials | null
  setCredentials: (credentials: IssuedCredentials | null) => void
  /** Client id and secret carried from the first-time view into the Sandbox, in memory only. */
  connectPrefill: { clientId: string; clientSecret: string } | null
  setConnectPrefill: (prefill: { clientId: string; clientSecret: string } | null) => void
  token: SandboxToken | null
  /** The client ID of the last Sandbox token, live or not, to fill in the next request. */
  lastClientId: string | null
  connection: Connection
  /** Exchanges the client id and secret for a token; rejects with the ApiError on failure. */
  connect: (clientId: string, clientSecret: string) => Promise<void>
  disconnect: () => void
  /** A job call answered 401: drop the token so the header shows Not connected. */
  expireToken: () => void
  /** The token ran out or was refused since the provider last connected. */
  expired: boolean
  /** Ticks every 30 seconds, for countdowns. */
  now: number
  /** API credentials, Webhooks and Sandbox open once approved, or once the provider has held a token. */
  approved: boolean
  /** A session kept from before a reload is still being checked, so `approved` may yet turn true. */
  restoring: boolean
  /** Set while leaving the page would lose something; the nav asks first. */
  leaveGuard: string | null
  setLeaveGuard: (message: string | null) => void
}

export const WorkbenchContext = createContext<WorkbenchState | null>(null)

export function useWorkbench(): WorkbenchState {
  const state = useContext(WorkbenchContext)
  if (!state) throw new Error('useWorkbench must be used inside WorkbenchProvider')
  return state
}
