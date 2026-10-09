import { useEffect, useRef, useState } from 'react'
import { initialiseCsrf } from '../auth/api'
import { submitIntakeApplication } from './api'
import type { IntakeApplicationCreateRequest } from './types'

type SubmissionState =
  | { status: 'idle' }
  | { status: 'submitting' }
  | { status: 'error'; error: unknown; sent: boolean }

/**
 * Submits an application once per user action and cancels work when the form closes.
 * @return Submission state, submit action and error dismissal
 * @author Wang Zhili
 */
export function useIntakeSubmission() {
  const [state, setState] = useState<SubmissionState>({ status: 'idle' })
  const request = useRef<AbortController | null>(null)
  useEffect(() => () => request.current?.abort(), [])

  async function submit(input: IntakeApplicationCreateRequest) {
    if (request.current) return
    const controller = new AbortController()
    request.current = controller
    setState({ status: 'submitting' })
    let sent = false
    try {
      await initialiseCsrf(controller.signal)
      if (controller.signal.aborted) return
      sent = true
      const application = await submitIntakeApplication(input, controller.signal)
      if (!controller.signal.aborted) {
        setState({ status: 'idle' })
        return application
      }
    } catch (error) {
      if (!controller.signal.aborted) setState({ status: 'error', error, sent })
    } finally {
      if (request.current === controller) request.current = null
    }
  }

  return { state, submit }
}
