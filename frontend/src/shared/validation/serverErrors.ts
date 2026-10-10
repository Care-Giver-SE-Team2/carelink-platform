import { ApiError } from '../api/client'

/**
 * The per-field messages a 400 carries (`{ fields: { phone: "..." } }`, written by the server's
 * GlobalExceptionHandler), so a form can show each one under its own input. Empty for any other
 * error. `rename` maps a server field onto the input that shows it, e.g. a cross-field check.
 */
export function serverFieldErrors(error: unknown, rename: Record<string, string> = {}): Record<string, string> {
  if (!(error instanceof ApiError) || error.status !== 400) return {}
  const fields = (error.body as { fields?: unknown } | null)?.fields
  if (!fields || typeof fields !== 'object') return {}
  const result: Record<string, string> = {}
  for (const [field, message] of Object.entries(fields as Record<string, unknown>)) {
    if (typeof message !== 'string') continue
    const text = /[.!?]$/.test(message) ? message : `${message}.`
    result[rename[field] ?? field] ??= text.charAt(0).toUpperCase() + text.slice(1)
  }
  return result
}

/** The `code` a 409 business-rule answer carries, e.g. "VALUE_ADDED_SERVICE_TOO_SOON". */
export function problemCode(error: unknown): string | undefined {
  if (!(error instanceof ApiError)) return undefined
  const code = (error.body as { code?: unknown } | null)?.code
  return typeof code === 'string' ? code : undefined
}
