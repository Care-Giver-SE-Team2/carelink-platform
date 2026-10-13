/**
 * The webhook rules CareLink keeps, as the Docs page states them and the mock follows them.
 * Proposed here; the webhook spec (docs/api/webhooks.md, Phase 1) is where they are settled.
 */

/** Minutes between attempts: the first retry a minute after the first try, the last six hours later. */
export const RETRY_DELAYS_MINUTES = [1, 5, 30, 120, 360]

/** The first try and every retry. */
export const MAX_ATTEMPTS = RETRY_DELAYS_MINUTES.length + 1

/** A receiver should refuse a webhook whose X-CareLink-Timestamp is older than this. */
export const SIGNATURE_TOLERANCE_SECONDS = 300

/** How long an attempt may take before it counts as unanswered. */
export const ATTEMPT_TIMEOUT_SECONDS = 10

/** Minutes after the delivery was created that attempt `n` (0-based) is made. */
export function attemptOffsetMinutes(n: number): number {
  return RETRY_DELAYS_MINUTES.slice(0, n).reduce((a, b) => a + b, 0)
}

/** "1 min, 5 min, 30 min, 2 h, 6 h". */
export function retryScheduleLabel(): string {
  return RETRY_DELAYS_MINUTES.map((m) => (m >= 60 ? `${m / 60} h` : `${m} min`)).join(', ')
}
