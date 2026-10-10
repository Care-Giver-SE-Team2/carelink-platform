import { useCallback, useEffect, useRef, useState } from 'react'
import {
  createCarePlanDraft,
  discardCarePlanDraft,
  fetchLatestCarePlan,
  saveCarePlanDraft,
} from '../../../shared/api/careplan'
import type { PlanNodePayload } from '../../../shared/api/careplan'

export type DraftSaveState = 'idle' | 'pending' | 'saving' | 'saved' | 'failed'

type DraftContent = { startDate: string; nodes: PlanNodePayload[] }

/** How long after the last edit the draft is saved. */
const SAVE_DELAY_MS = 800

/**
 * Keeps the manager's in-progress care plan draft on the backend, so leaving the editor doesn't
 * lose it. `schedule` is called after each edit with the whole draft; it is saved once edits pause,
 * or straight away when the editor unmounts. The first save opens the draft on the backend (or
 * reuses one already open); later saves overwrite it. Saves run one after another, so a slow save
 * can't land after a newer one. A failed save keeps the content and is retried by `retry` or by
 * the next edit, which sends the whole draft again anyway.
 *
 * `knownDraftId` is the draft the page loaded with, if the elder's latest plan is one.
 */
export function useDraftAutosave(elderId: string | number | undefined, knownDraftId: number | null) {
  const [state, setState] = useState<DraftSaveState>('idle')
  const elderIdRef = useRef(elderId)
  const draftId = useRef<number | null>(knownDraftId)
  const pending = useRef<DraftContent | null>(null)
  const queue = useRef<Promise<void>>(Promise.resolve())
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  useEffect(() => {
    elderIdRef.current = elderId
  }, [elderId])

  useEffect(() => {
    if (knownDraftId !== null) draftId.current = knownDraftId
  }, [knownDraftId])

  const openDraft = useCallback(async (): Promise<number> => {
    if (draftId.current !== null) return draftId.current
    const elder = elderIdRef.current!
    const latest = await fetchLatestCarePlan(elder)
    const id = latest?.status === 'DRAFT' ? latest.id : (await createCarePlanDraft(elder)).id
    draftId.current = id
    return id
  }, [])

  const flush = useCallback((): Promise<void> => {
    clearTimeout(timer.current)
    const content = pending.current
    pending.current = null
    if (!content) return queue.current
    setState('saving')
    queue.current = queue.current.then(async () => {
      try {
        const id = await openDraft()
        await saveCarePlanDraft(id, content.startDate || null, content.nodes)
        if (!pending.current) setState('saved')
      } catch {
        // Keep it for a retry unless a newer edit has already replaced it.
        pending.current ??= content
        setState('failed')
      }
    })
    return queue.current
  }, [openDraft])

  // Leaving the editor saves whatever is still waiting for the pause.
  useEffect(() => () => void flush(), [flush])

  const schedule = useCallback(
    (content: DraftContent) => {
      pending.current = content
      setState('pending')
      clearTimeout(timer.current)
      timer.current = setTimeout(() => void flush(), SAVE_DELAY_MS)
    },
    [flush],
  )

  /** Waits for every save, including one still waiting for the pause — before publishing. `openDraft` then
   * gives the draft to publish, opening one if nothing was saved yet. */
  const settle = useCallback(() => flush(), [flush])

  /** Drops unsaved edits and deletes the draft on the backend, if one was opened. */
  const discard = useCallback(async () => {
    clearTimeout(timer.current)
    pending.current = null
    await queue.current
    const id = draftId.current
    if (id !== null) await discardCarePlanDraft(id)
    draftId.current = null
    setState('idle')
  }, [])

  /** The draft became a published version; the next edit opens a new draft. */
  const published = useCallback(() => {
    draftId.current = null
    setState('idle')
  }, [])

  return { state, schedule, settle, openDraft, discard, published, retry: flush }
}
