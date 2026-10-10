import { useQuery } from '@tanstack/react-query'
import { api } from '../../shared/api/client'

/** One recurring day: Monday at "08:00:00" for 30 minutes (java.time names and LocalTime strings). */
export type FamilyPlanSlot = { day: string; startTime: string; minutes: number }
export type FamilyPlanTask = { groupName: string | null; activityCode: string | null; name: string; slots: FamilyPlanSlot[] }
/** effectiveUntil is exclusive and null while the version is open-ended. */
export type FamilyPlanVersion = { version: number; effectiveFrom: string; effectiveUntil: string | null; tasks: FamilyPlanTask[] }
/** The version in force today and one published to start later; either may be null. */
export type FamilyCarePlan = { current: FamilyPlanVersion | null; upcoming: FamilyPlanVersion | null }

/** GET /api/family/elders/{id}/care-plan — access-checked and audited on the server. */
export function getFamilyCarePlan(elderId: number, signal?: AbortSignal) {
  return api<FamilyCarePlan>(`/family/elders/${elderId}/care-plan`, { signal })
}

export function useFamilyCarePlan(elderId: number | undefined) {
  return useQuery({
    queryKey: ['familyCarePlan', elderId],
    queryFn: ({ signal }) => getFamilyCarePlan(elderId!, signal),
    enabled: elderId !== undefined,
    retry: false,
  })
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** A plan date ("2026-11-03", a calendar day with no time zone) as "3 Nov 2026". */
export function planDate(iso: string): string {
  const [year, month, day] = iso.split('-').map(Number)
  return `${day} ${MONTHS[month - 1]} ${year}`
}

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
const SHORT = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

/** "08:00:00" -> "8:00 AM". */
function clock(time: string): string {
  const [h, m] = time.split(':').map(Number)
  return `${h % 12 === 0 ? 12 : h % 12}:${String(m).padStart(2, '0')} ${h < 12 ? 'AM' : 'PM'}`
}

/**
 * A task's week in plain words, one line per distinct time: days sharing a start time and length
 * are listed together ("Mon, Wed, Fri · 8:00 AM · 30 min"), and all seven days read "Every day".
 */
export function slotLines(slots: FamilyPlanSlot[]): string[] {
  const groups = new Map<string, number[]>()
  for (const slot of [...slots].sort((a, b) => DAYS.indexOf(a.day) - DAYS.indexOf(b.day))) {
    const key = `${clock(slot.startTime)} · ${slot.minutes} min`
    groups.set(key, [...(groups.get(key) ?? []), DAYS.indexOf(slot.day)])
  }
  return [...groups].map(([when, days]) => `${days.length === 7 ? 'Every day' : days.map((d) => SHORT[d]).join(', ')} · ${when}`)
}

/** Total weekly time across a version's tasks, e.g. "3 h 15 min a week". */
export function weeklyTime(version: FamilyPlanVersion): string {
  const minutes = version.tasks.flatMap((task) => task.slots).reduce((sum, slot) => sum + slot.minutes, 0)
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return `${[h > 0 && `${h} h`, (m > 0 || h === 0) && `${m} min`].filter(Boolean).join(' ')} a week`
}

/** Tasks grouped under their sub-plan, in plan order; tasks without one come under "Other care". */
export function groupedTasks(version: FamilyPlanVersion): { group: string; tasks: FamilyPlanTask[] }[] {
  const groups: { group: string; tasks: FamilyPlanTask[] }[] = []
  for (const task of version.tasks) {
    const name = task.groupName ?? 'Other care'
    const group = groups.find((g) => g.group === name)
    if (group) group.tasks.push(task)
    else groups.push({ group: name, tasks: [task] })
  }
  return groups
}
