/**
 * An error response from the provider API: its HTTP status, the machine-readable `code` from
 * the body, and the request id CareLink support can trace. `status` 0 means the request never
 * got an answer (offline, or the network dropped it).
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly requestId: string

  constructor(status: number, code: string, message: string, requestId: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.requestId = requestId
  }
}

/** True when the request never reached CareLink or CareLink failed it (network, 5xx). */
export function isTransient(error: unknown): boolean {
  return error instanceof ApiError ? error.status === 0 || error.status >= 500 : true
}
