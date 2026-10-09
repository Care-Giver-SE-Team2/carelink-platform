export class ApiError extends Error {
  readonly status: number
  readonly body: unknown

  constructor(
    message: string,
    status: number,
    body?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

/** Dispatched on window whenever the server answers 401 outside of login. */
export const SESSION_EXPIRED_EVENT = 'carelink:session-expired'

/**
 * Shared HTTP client used by CareLink frontend features.
 *
 * Requests are sent to /api through the Vite development proxy.
 * Browser session cookies are included automatically.
 *
 * Authentication failures are returned to the calling feature as
 * ApiError instances. The client does not navigate itself: a 401 (other
 * than a failed login) also dispatches SESSION_EXPIRED_EVENT on window,
 * and RequireRole, which wraps every role client, returns the user to the
 * landing page.
 */
export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers)

  if (
    options.body !== undefined &&
    options.body !== null &&
    !headers.has('Content-Type')
  ) {
    headers.set('Content-Type', 'application/json')
  }

  /*
   * Spring Security exposes the CSRF token through the XSRF-TOKEN
   * cookie. State-changing requests send the same value back using
   * the X-XSRF-TOKEN header.
   */
  const method = (options.method ?? 'GET').toUpperCase()

  const stateChanging =
    method !== 'GET' &&
    method !== 'HEAD' &&
    method !== 'OPTIONS'

  if (
    stateChanging &&
    !headers.has('X-XSRF-TOKEN')
  ) {
    const csrfToken = readCookie('XSRF-TOKEN')

    if (csrfToken) {
      headers.set(
        'X-XSRF-TOKEN',
        decodeURIComponent(csrfToken),
      )
    }
  }

  const response = await fetch(`/api${path}`, {
    ...options,
    headers,
    credentials: 'include',
  })

  if (!response.ok) {
    const body = await readResponseBody(response)

    if (response.status === 401 && path !== '/auth/login') {
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    }

    throw new ApiError(
      errorMessage(body, response.status),
      response.status,
      body,
    )
  }

  /*
   * Successful endpoints such as CSRF bootstrap and logout may
   * intentionally return no response body.
   */
  if (response.status === 204) {
    return undefined as T
  }

  const contentType =
    response.headers.get('content-type') ?? ''

  if (contentType.includes('application/json')) {
    return (await response.json()) as T
  }

  const text = await response.text()

  if (!text) {
    return undefined as T
  }

  return text as T
}

function readCookie(name: string): string | null {
  const prefix = `${name}=`

  for (const part of document.cookie.split(';')) {
    const cookie = part.trim()

    if (cookie.startsWith(prefix)) {
      return cookie.substring(prefix.length)
    }
  }

  return null
}

/**
 * Read an error body while retaining the HTTP error if parsing fails.
 *
 * @param response Failed HTTP response
 * @return Parsed JSON, text, or null when the body cannot be read
 * @author Wang Zhili
 */
async function readResponseBody(
  response: Response,
): Promise<unknown> {
  const contentType = (response.headers.get('content-type') ?? '')
    .split(';')[0]
    .trim()
    .toLowerCase()

  try {
    if (
      contentType === 'application/json' ||
      contentType === 'application/problem+json'
    ) {
      return await response.json()
    }

    const text = await response.text()

    return text || null
  } catch {
    return null
  }
}

/**
 * Select a message from JSON or text errors, with a status fallback.
 *
 * @param body Parsed error body
 * @param status HTTP response status
 * @return Message, problem detail, problem title, or fallback text
 * @author Wang Zhili
 */
function errorMessage(
  body: unknown,
  status: number,
): string {
  if (
    body !== null &&
    typeof body === 'object'
  ) {
    for (const field of ['message', 'detail', 'title']) {
      const value = (body as Record<string, unknown>)[field]

      if (typeof value === 'string' && value.trim()) {
        return value
      }
    }
  }

  if (
    typeof body === 'string' &&
    body.trim()
  ) {
    return body
  }

  if (status === 401) {
    return 'Authentication is required.'
  }

  if (status === 403) {
    return 'You are not authorised to perform this action.'
  }

  return `Request failed with status ${status}.`
}
