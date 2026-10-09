import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import {
  Link,
  useNavigate,
} from 'react-router-dom'

import {
  getCurrentUser,
  signOut,
} from '../../features/auth/api'
import type { CurrentUser } from '../../features/auth/types'
import { ApiError } from '../api/client'
import { NotificationBell } from './notifications/NotificationBell'

/**
 * The frame every role screen sits in.
 *
 * It provides:
 * - the shared CareLink header
 * - the current authenticated user
 * - sign-out behaviour
 * - session-expiry handling
 *
 * `theme` switches the CSS custom properties in
 * shared/theme/theme.css. Pass "elder" for the elder client;
 * everything else uses the standard theme.
 */
export function RoleShell({
  title,
  theme = 'standard',
  wide = false,
  children,
}: {
  title: string
  theme?: 'standard' | 'elder'
  wide?: boolean
  children: ReactNode
}) {
  const navigate = useNavigate()

  const [currentUser, setCurrentUser] =
    useState<CurrentUser | null>(null)

  const [signingOut, setSigningOut] =
    useState(false)

  const [signOutError, setSignOutError] =
    useState<string | null>(null)

  /*
   * Resolve the user from the server-side Spring Security session.
   *
   * A 401 here means this protected role page no longer has a valid
   * authenticated session, so return the user to the main sign-in
   * page.
   *
   * Other errors should not destroy the whole role page. In that
   * situation we simply omit the current-user indicator.
   */
  useEffect(() => {
    const controller = new AbortController()

    getCurrentUser(controller.signal)
      .then((user) => {
        setCurrentUser(user)
      })
      .catch((error: unknown) => {
        if (
          error instanceof DOMException &&
          error.name === 'AbortError'
        ) {
          return
        }

        if (
          error instanceof ApiError &&
          error.status === 401
        ) {
          navigate('/', {
            replace: true,
          })
          return
        }

        setCurrentUser(null)
      })

    return () => {
      controller.abort()
    }
  }, [navigate])

  async function handleSignOut() {
    if (signingOut) {
      return
    }

    setSigningOut(true)
    setSignOutError(null)

    try {
      await signOut()

      setCurrentUser(null)

      navigate('/', {
        replace: true,
      })
    } catch (error: unknown) {
      /*
       * If the session has already expired by the time the user
       * presses Sign out, they are effectively signed out already.
       * Returning to the sign-in page is therefore the correct result.
       */
      if (
        error instanceof ApiError &&
        error.status === 401
      ) {
        setCurrentUser(null)

        navigate('/', {
          replace: true,
        })

        return
      }

      setSignOutError(
        'Unable to sign out. Please try again.',
      )
    } finally {
      setSigningOut(false)
    }
  }

  const displayName =
    currentUser?.displayName ||
    currentUser?.username ||
    ''

  return (
    <div
      data-theme={
        theme === 'elder'
          ? 'elder'
          : undefined
      }
    >
      <header
        style={{
          borderBottom:
            '1px solid var(--border)',
          padding: 'var(--gap)',
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--gap)',
        }}
      >
        <Link
          to="/"
          style={{
            color: 'var(--text-muted)',
          }}
        >
          ← CareLink
        </Link>

        <strong>{title}</strong>

        {currentUser && (
          <div
            style={{
              marginLeft: 'auto',
              display: 'flex',
              alignItems: 'center',
              gap: 12,
            }}
          >
            <NotificationBell />

            <div
              aria-hidden="true"
              style={{
                width: 36,
                height: 36,
                flexShrink: 0,
                borderRadius: '50%',
                border:
                  '1px solid var(--border)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 700,
                fontSize: 14,
              }}
            >
              {displayName
                .charAt(0)
                .toUpperCase()}
            </div>

            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                lineHeight: 1.2,
              }}
            >
              <span
                style={{
                  fontSize: 14,
                  fontWeight: 600,
                }}
              >
                {displayName}
              </span>

              <span
                style={{
                  marginTop: 3,
                  color:
                    'var(--text-muted)',
                  fontSize: 12,
                }}
              >
                {currentUser.roles
                  .map((role) =>
                    role.replace(
                      /^ROLE_/,
                      '',
                    ),
                  )
                  .join(', ')}
              </span>
            </div>

            <button
              type="button"
              onClick={handleSignOut}
              disabled={signingOut}
              style={{
                marginLeft: 8,
                padding: '7px 12px',
                border:
                  '1px solid var(--border)',
                borderRadius: 6,
                background: 'transparent',
                color: 'var(--text)',
                cursor: signingOut
                  ? 'not-allowed'
                  : 'pointer',
                opacity: signingOut
                  ? 0.6
                  : 1,
                fontSize: 13,
              }}
            >
              {signingOut
                ? 'Signing out…'
                : 'Sign out'}
            </button>

            {signOutError && (
              <span
                role="alert"
                style={{
                  color: '#b91c1c',
                  fontSize: 12,
                }}
              >
                {signOutError}
              </span>
            )}
          </div>
        )}
      </header>

      <main
        style={{
          padding: 'var(--gap)',
          maxWidth: wide
            ? 'none'
            : 640,
          margin: '0 auto',
        }}
      >
        {children}
      </main>
    </div>
  )
}