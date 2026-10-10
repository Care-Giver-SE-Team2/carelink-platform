import { api, ApiError } from './client'

/** Mirrors careplan.domain.model.CarePlan — GET/POST /api/care-plans return it as-is (no DTO yet). */
export type CarePlanResponse = {
  id: number
  elderId: number
  createdByUserId: number | null
  supersedesPlanId: number | null
  version: number
  status: 'DRAFT' | 'PUBLISHED' | 'SUPERSEDED' | 'STOPPED'
  totalHours: number | null
  publishedAt: string | null
  createdAt: string
  updatedAt: string
  startDate: string | null
  stopEffectiveDate: string | null
  stopReason: string | null
  stoppedByUserId: number | null
  stoppedAt: string | null
}

/**
 * One day of a task's schedule. startTime is 24-hour "HH:mm" when sent; the backend returns it as
 * java.time.LocalTime's "HH:mm:ss". The end time is start + minutes, worked out client-side.
 */
export type VisitPayload = { day: string; startTime: string; minutes: number }

/** Every node is a task; groupName is a display-only label, not a hierarchy. activityCode is the
 * catalog activity the task delivers (GET /api/care-activities), or null for a task outside it. */
export type PlanNodePayload = {
  groupName: string | null
  activityCode: string | null
  name: string
  visits: VisitPayload[]
  evidenceType: 'NONE' | 'CHECKLIST' | 'PHOTO' | 'READING'
}

export type CarePlanNodeResponse = {
  id: number
  groupName: string | null
  activityCode: string | null
  name: string
  visits: VisitPayload[]
  evidenceType: 'NONE' | 'CHECKLIST' | 'PHOTO' | 'READING'
  weeklyHours: number | null
}

/** The elder's highest-version plan (draft, published or superseded), or null if it has none yet. */
export async function fetchLatestCarePlan(elderId: string | number): Promise<CarePlanResponse | null> {
  try {
    return await api<CarePlanResponse>(`/care-plans/latest?elderId=${elderId}`)
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null
    throw err
  }
}

/** Every version of the elder's plan, drafts included, newest first; [] when the elder has none. */
export function fetchCarePlanVersions(elderId: string | number): Promise<CarePlanResponse[]> {
  return api<CarePlanResponse[]>(`/care-plans?elderId=${elderId}`)
}

export function fetchCarePlanNodes(carePlanId: number): Promise<CarePlanNodeResponse[]> {
  return api<CarePlanNodeResponse[]>(`/care-plans/${carePlanId}/nodes`)
}

export function createCarePlanDraft(elderId: string | number): Promise<CarePlanResponse> {
  return api<CarePlanResponse>('/care-plans', {
    method: 'POST',
    body: JSON.stringify({ elderId: Number(elderId) }),
  })
}

/** startDate as an ISO "yyyy-MM-dd" string, matching java.time.LocalDate's JSON form. */
export function publishCarePlan(
  carePlanId: number,
  startDate: string,
  nodes: PlanNodePayload[],
): Promise<CarePlanResponse> {
  return api<CarePlanResponse>(`/care-plans/${carePlanId}/publish`, {
    method: 'POST',
    body: JSON.stringify({ startDate, nodes }),
  })
}

/** Saves the editor's work in progress on a draft. Unlike publish, startDate may still be null and a task may
 * have no visits yet. Nothing is scheduled from a draft. */
export function saveCarePlanDraft(
  carePlanId: number,
  startDate: string | null,
  nodes: PlanNodePayload[],
): Promise<CarePlanResponse> {
  return api<CarePlanResponse>(`/care-plans/${carePlanId}/draft`, {
    method: 'PUT',
    body: JSON.stringify({ startDate, nodes }),
  })
}

/** Throws a draft away; the version it would have replaced stays in force. */
export function discardCarePlanDraft(carePlanId: number): Promise<void> {
  return api<void>(`/care-plans/${carePlanId}`, { method: 'DELETE' })
}

/** Effective date as an ISO "yyyy-MM-dd" string, matching java.time.LocalDate's JSON form. */
export function stopCarePlan(carePlanId: number, effectiveDate: string, reason: string): Promise<CarePlanResponse> {
  return api<CarePlanResponse>(`/care-plans/${carePlanId}/stop`, {
    method: 'POST',
    body: JSON.stringify({ effectiveDate, reason }),
  })
}

/** One entry of the care activity catalog — the same list the family applies from and the manager plans from.
 * code is what an application's careNeeds stores; label is what both sides see and what a plan task is named;
 * category is the sub-plan the task is filed under. */
export type CareActivity = { code: string; label: string; category: string }

/** GET /api/care-activities, in catalog order. */
export function fetchCareActivities(signal?: AbortSignal): Promise<CareActivity[]> {
  return api<CareActivity[]>('/care-activities', { signal })
}
