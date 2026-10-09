/**
 * Care plan tree types for the editor, plus the activity catalog the "Add sub-plan" picker offers.
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

/**
 * Grouped catalog for the "Add sub-plan" step 1 picker: selecting an activity
 * both names the sub-plan and determines the single task it starts with — see
 * the handoff ("no separate preset-label step"). This is product configuration,
 * not mock data: the backend stores a task's name and group as free text and
 * keeps no catalog of its own.
 */
export const ACTIVITY_CATALOG: { category: string; activities: string[] }[] = [
  { category: 'Personal care', activities: ['Bathing assistance', 'Grooming', 'Meal support'] },
  { category: 'Health monitoring', activities: ['Vital-sign check'] },
  { category: 'Medication support', activities: ['Morning reminder', 'Evening reminder'] },
  {
    category: 'Social and mobility',
    activities: ['Companionship walk', 'Light exercise', 'Errand accompaniment'],
  },
]

/** The catalog category an activity belongs to, which is the sub-plan it's filed under. */
export function activityCategory(activity: string): string | undefined {
  return ACTIVITY_CATALOG.find((group) => group.activities.includes(activity))?.category
}
