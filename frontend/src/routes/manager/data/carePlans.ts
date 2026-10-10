/**
 * Care plan tree types for the editor. The activities the "Add sub-plan" picker offers come from
 * GET /api/care-activities (features/careplan/careActivities.ts) — the same catalog families apply from.
 *
 * A task's weekly effort is derived from its `visits` (one entry per scheduled day, each carrying
 * its own per-visit minutes — durations can vary by day) and rolled up through its sub-plan,
 * client-side so edits recompute without a round trip. See lib/planTree.ts. The plans themselves
 * come from GET /api/care-plans; nothing here stands in for an endpoint.
 */

export type EvidenceType = 'CHECKLIST' | 'READING' | 'PHOTO'

/** One scheduled day: its own start time (24-hour "HH:mm") and minutes. */
export type DayVisit = { day: string; startTime: string; minutes: number }

export type TaskNode = {
  id: string
  type: 'task'
  /** The catalog activity this task delivers, kept when the manager renames the task; null outside the catalog. */
  activityCode: string | null
  name: string
  visits: DayVisit[]
  evidence: EvidenceType
}

export type SubPlanNode = {
  id: string
  type: 'subplan'
  name: string
  /** Sub-plans are flat — no nesting beyond this single level (see the handoff). */
  children: TaskNode[]
}

export type PlanNode = TaskNode | SubPlanNode
