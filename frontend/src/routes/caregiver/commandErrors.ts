import { ApiError } from '../../shared/api/client'

export function unknownResult(error: unknown) { return !(error instanceof ApiError) || error.status >= 500 }
export function commandError(error: unknown) {
  if (unknownResult(error)) return 'Result unknown. Check saved results before retrying the same request. Do not create another request for the same action.'
  if (!(error instanceof ApiError)) return ''
  if (error.status === 400) return 'Check the required entries and their length.'
  if (error.status === 404) return 'This record is unavailable.'
  return 'The visit or task has changed, or this action is not allowed. Refresh to check before editing and submitting again.'
}
