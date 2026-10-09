import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate, useNavigate } from 'react-router-dom'
import { getCurrentUser } from '../../features/auth/api'
import { hasRole, homePathFor } from '../../features/auth/roles'
import type { CareLinkRole } from '../../features/auth/roles'
import { ApiError, SESSION_EXPIRED_EVENT } from '../api/client'

/**
 * Gate in front of a role's client. Nothing inside renders until the server session is confirmed:
 * - no session (401) → the landing page, which is the only sign-in screen;
 * - signed in with another role → that role's own client;
 * - any later 401 from any API call (the session expired mid-use) → the landing page.
 *
 * Leaving the client drops every cached query, so the next account signed in on this tab never
 * sees the previous one's data.
 */
export function RequireRole({ role, children }: { role: CareLinkRole; children: ReactNode }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const session = useQuery({
    queryKey: ['session', role],
    queryFn: ({ signal }) => getCurrentUser(signal),
    staleTime: 0,
    gcTime: 0,
    retry: (count, error) => !(error instanceof ApiError && error.status === 401) && count < 2,
  })

  useEffect(() => {
    const expired = () => navigate('/', { replace: true })
    window.addEventListener(SESSION_EXPIRED_EVENT, expired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, expired)
  }, [navigate])

  useEffect(() => () => queryClient.clear(), [queryClient])

  if (session.error instanceof ApiError && session.error.status === 401) return <Navigate to="/" replace />
  if (session.isError) {
    return <div role="alert" style={{ padding: 24, textAlign: 'center' }}>
      <p>Unable to check your sign-in. Check your connection and try again.</p>
      <button type="button" onClick={() => void session.refetch()}>Try again</button>
    </div>
  }
  if (!session.data) return <p role="status" style={{ padding: 24, textAlign: 'center' }}>Checking your sign-in…</p>
  if (!hasRole(session.data.roles, role)) return <Navigate to={homePathFor(session.data.roles) ?? '/'} replace />
  return children
}
