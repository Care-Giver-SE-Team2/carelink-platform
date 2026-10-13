import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import { getMyApplication, requestToken, signIn as startSession, signOut as endSession } from '../../features/provider-platform/api'
import type { PortalSession } from '../../features/provider-platform/api'
import type { Application, IssuedCredentials } from '../../features/provider-platform/types'
import { WorkbenchContext } from './workbenchContext'
import type { Connection, SandboxToken, WorkbenchState } from './workbenchContext'

const TOKEN_KEY = 'carelink.workbench.token'
const SESSION_KEY = 'carelink.workbench.session'

function read<T>(key: string): T | null {
  try {
    const raw = sessionStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : null
  } catch {
    return null
  }
}

function write(key: string, value: unknown) {
  try {
    if (value === null) sessionStorage.removeItem(key)
    else sessionStorage.setItem(key, JSON.stringify(value))
  } catch {
    // Storage blocked: the value lives for this page only.
  }
}

/**
 * Holds the portal's state for every page under /provider. The portal session and the
 * Sandbox token are kept in sessionStorage, so a reload keeps the provider signed in and
 * connected, and closing the tab signs them out.
 */
export function WorkbenchProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<PortalSession | null>(() => read<PortalSession>(SESSION_KEY))
  const [application, setApplication] = useState<Application | null>(null)
  const [restoring, setRestoring] = useState(() => read(SESSION_KEY) !== null)
  const [credentials, setCredentials] = useState<IssuedCredentials | null>(null)
  const [connectPrefill, setConnectPrefill] = useState<{ clientId: string; clientSecret: string } | null>(null)
  const [token, setToken] = useState<SandboxToken | null>(() => read<SandboxToken>(TOKEN_KEY))
  const [failed, setFailed] = useState(false)
  const [revoked, setRevoked] = useState(false)
  const [now, setNow] = useState(() => Date.now())
  const [leaveGuard, setLeaveGuard] = useState<string | null>(null)
  const { pathname } = useLocation()

  const [lastPathname, setLastPathname] = useState(pathname)

  // Credentials are shown once: leaving the API credentials page drops them, and anything a
  // page was guarding. The Sandbox pre-fill stays in memory until the Sandbox uses it.
  if (pathname !== lastPathname) {
    setLastPathname(pathname)
    setLeaveGuard(null)
    if (pathname !== '/provider/credentials') setCredentials(null)
  }

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30_000)
    return () => clearInterval(timer)
  }, [])

  const dropSession = useCallback(() => {
    setSession(null)
    setApplication(null)
    write(SESSION_KEY, null)
  }, [])

  // Signed in before a reload: fetch the application again, or sign out if the session ended.
  useEffect(() => {
    const kept = read<PortalSession>(SESSION_KEY)
    if (!kept) return
    let live = true
    getMyApplication(kept)
      .then((found) => {
        if (live) setApplication(found)
      })
      .catch(() => {
        if (live) dropSession()
      })
      .finally(() => {
        if (live) setRestoring(false)
      })
    return () => {
      live = false
    }
  }, [dropSession])

  const signIn = useCallback(async (email: string, password: string) => {
    const started = await startSession(email, password)
    const found = await getMyApplication(started)
    setSession(started)
    setApplication(found)
    write(SESSION_KEY, started)
    return found
  }, [])

  const refreshApplication = useCallback(async () => {
    if (!session) return
    try {
      setApplication(await getMyApplication(session))
    } catch (err) {
      dropSession()
      throw err
    }
  }, [session, dropSession])

  const dropToken = useCallback(() => {
    setToken(null)
    write(TOKEN_KEY, null)
  }, [])

  const signOut = useCallback(() => {
    if (session) void endSession(session).catch(() => {})
    dropSession()
    dropToken()
    setCredentials(null)
    setConnectPrefill(null)
    setFailed(false)
    setRevoked(false)
  }, [session, dropSession, dropToken])

  const expireToken = useCallback(() => {
    dropToken()
    setRevoked(true)
  }, [dropToken])

  const connect = useCallback(
    async (clientId: string, clientSecret: string) => {
      try {
        const issued = { ...(await requestToken(clientId, clientSecret)), clientId: clientId.trim() }
        setToken(issued)
        setFailed(false)
        setRevoked(false)
        setNow(Date.now())
        write(TOKEN_KEY, issued)
      } catch (err) {
        dropToken()
        setFailed(true)
        throw err
      }
    },
    [dropToken],
  )

  const disconnect = useCallback(() => {
    dropToken()
    setFailed(false)
    setRevoked(false)
  }, [dropToken])

  const live = token !== null && token.expiresAt > now
  const connection: Connection = live ? 'connected' : failed ? 'error' : 'none'
  const signedIn = session !== null && session.expiresAt > now && application !== null

  // Leaving with the credentials unconfirmed: the browser asks too.
  useEffect(() => {
    if (!leaveGuard) return
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault()
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [leaveGuard])

  const value = useMemo<WorkbenchState>(
    () => ({
      session: signedIn ? session : null,
      application: signedIn ? application : null,
      signIn,
      signOut,
      refreshApplication,
      portal: signedIn && application?.status === 'approved' ? session : null,
      credentials,
      setCredentials,
      connectPrefill,
      setConnectPrefill,
      token: live ? token : null,
      lastClientId: token?.clientId ?? null,
      connection,
      connect,
      disconnect,
      expireToken,
      expired: revoked || (token !== null && !live),
      now,
      // A token, even one that has run out, proves the provider was approved.
      approved: (signedIn && application?.status === 'approved') || token !== null || revoked,
      restoring,
      leaveGuard,
      setLeaveGuard,
    }),
    [
      signedIn,
      session,
      application,
      signIn,
      signOut,
      refreshApplication,
      credentials,
      connectPrefill,
      token,
      live,
      connection,
      connect,
      disconnect,
      expireToken,
      revoked,
      now,
      restoring,
      leaveGuard,
    ],
  )

  return <WorkbenchContext.Provider value={value}>{children}</WorkbenchContext.Provider>
}
