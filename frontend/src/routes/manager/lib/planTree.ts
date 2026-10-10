import type { DayVisit, EvidenceType, PlanNode, SubPlanNode, TaskNode } from '../data/carePlans'
import type { CarePlanNodeResponse } from '../../../shared/api/careplan'
import type { PlanTreeItem, PlanTreeTask } from '../../../shared/components/ui'
import { WEEKDAYS, emptyDaySchedule } from '../components/weekdays'
import type { DayScheduleValue } from '../components/weekdays'

/** Bottom-up weekly effort in hours: a sub-plan is the sum of its tasks. */
export function weeklyHours(node: PlanNode): number {
  if (node.type === 'task') {
    return node.visits.reduce((sum, v) => sum + v.minutes, 0) / 60
  }
  return node.children.reduce((sum, child) => sum + weeklyHours(child), 0)
}

export function weeklyHoursOfTree(tree: PlanNode[]): number {
  return tree.reduce((sum, node) => sum + weeklyHours(node), 0)
}

export function countTree(tree: PlanNode[]): { subPlans: number; tasks: number } {
  let subPlans = 0
  let tasks = 0
  for (const node of tree) {
    if (node.type === 'subplan') {
      subPlans += 1
      tasks += node.children.length
    } else {
      tasks += 1
    }
  }
  return { subPlans, tasks }
}

/** "08:00" (or the backend's "08:00:00") -> minutes after midnight. */
function minutesOfDay(startTime: string): number {
  const [hours, minutes] = startTime.split(':').map(Number)
  return hours * 60 + minutes
}

/** Minutes after midnight -> "8:00" plus its meridiem, wrapping past midnight. */
function clock12(minutesOfDay: number): { time: string; meridiem: 'AM' | 'PM' } {
  const wrapped = ((minutesOfDay % 1440) + 1440) % 1440
  const hours = Math.floor(wrapped / 60)
  const minutes = wrapped % 60
  return {
    time: `${((hours + 11) % 12) + 1}:${String(minutes).padStart(2, '0')}`,
    meridiem: hours < 12 ? 'AM' : 'PM',
  }
}

/** "8:00–8:30 AM", or "11:45 AM–12:15 PM" when the visit crosses noon. The end is start + minutes. */
export function timeRange(startTime: string, minutes: number): string {
  const start = minutesOfDay(startTime)
  const from = clock12(start)
  const to = clock12(start + minutes)
  return from.meridiem === to.meridiem
    ? `${from.time}–${to.time} ${to.meridiem}`
    : `${from.time} ${from.meridiem}–${to.time} ${to.meridiem}`
}

/**
 * A task's schedule as the tags shown under its name: one per day ("Mon 8:00–8:30 AM · 30 m"),
 * collapsed to a single "Daily …" tag when all seven days share the same start and minutes.
 */
export function scheduleTags(visits: DayVisit[]): string[] {
  const [first] = visits
  const sameEveryDay =
    visits.length === 7 &&
    visits.every((v) => minutesOfDay(v.startTime) === minutesOfDay(first.startTime) && v.minutes === first.minutes)
  if (sameEveryDay) return [`Daily ${timeRange(first.startTime, first.minutes)} · ${first.minutes} m`]
  return visits.map((v) => `${v.day} ${timeRange(v.startTime, v.minutes)} · ${v.minutes} m`)
}

/** "HH:mm" -> the editor's "8:00 AM". */
export function toEditorTime(startTime: string): string {
  const { time, meridiem } = clock12(minutesOfDay(startTime))
  return `${time} ${meridiem}`
}

/**
 * What a manager typed in a start-time field -> 24-hour "HH:mm", or null if it isn't a time.
 * Accepts "8:00 AM", "8:00am", "8 PM" and 24-hour "16:30".
 */
export function parseEditorTime(input: string): string | null {
  const match = /^\s*(\d{1,2})(?::(\d{2}))?\s*([ap])?\.?\s*m?\.?\s*$/i.exec(input)
  if (!match) return null
  let hours = Number(match[1])
  const minutes = Number(match[2] ?? 0)
  const meridiem = match[3]?.toLowerCase()
  if (minutes > 59) return null
  if (meridiem) {
    if (hours < 1 || hours > 12) return null
    hours = (hours % 12) + (meridiem === 'p' ? 12 : 0)
  } else if (hours > 23 || match[2] === undefined) {
    return null
  }
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`
}

/** Fixed two-decimal hours for table cells, e.g. "2.25 h". */
export function formatHoursFixed(hours: number): string {
  return `${hours.toFixed(2)} h`
}

/** Loose one-decimal hours for prose subtitles, e.g. "6.5 h". Trims a trailing .0. */
export function formatHoursLoose(hours: number): string {
  const rounded = Math.round(hours * 10) / 10
  return `${rounded % 1 === 0 ? rounded.toFixed(0) : rounded.toFixed(1)} h`
}

/** "6h 30m" style duration for the rail's summary card. */
export function formatHoursMinutes(hours: number): string {
  const totalMinutes = Math.round(hours * 60)
  const h = Math.floor(totalMinutes / 60)
  const m = totalMinutes % 60
  return m === 0 ? `${h}h` : `${h}h ${m}m`
}

/** Remove a top-level sub-plan, or a task nested one level under a sub-plan, by id. A sub-plan
 * left with no tasks by the removal is removed along with it. */
export function removeNode(tree: PlanNode[], id: string): PlanNode[] {
  return tree.flatMap((node) => {
    if (node.id === id) return []
    if (node.type !== 'subplan' || !node.children.some((task) => task.id === id)) return [node]
    const children = node.children.filter((task) => task.id !== id)
    return children.length === 0 ? [] : [{ ...node, children }]
  })
}

/** Replace a task nested one level under a sub-plan, or a top-level task, by id. */
export function updateTask(tree: PlanNode[], id: string, updated: TaskNode): PlanNode[] {
  return tree.map((node) => {
    if (node.id === id) return updated
    if (node.type === 'subplan') {
      return { ...node, children: node.children.map((task) => (task.id === id ? updated : task)) }
    }
    return node
  })
}

export function findSubPlan(tree: PlanNode[], id: string): SubPlanNode | undefined {
  return tree.find((node): node is SubPlanNode => node.type === 'subplan' && node.id === id)
}

export function taskCount(node: TaskNode | SubPlanNode): number {
  return node.type === 'task' ? 1 : node.children.length
}

/** Distinct days of the week a caregiver visits, across every task in the tree — a task scheduled
 * on the same day as another still counts as a single weekly visit. */
export function visitsPerWeekOfTree(tree: PlanNode[]): number {
  const days = new Set<string>()
  function collect(node: PlanNode) {
    if (node.type === 'task') node.visits.forEach((v) => days.add(v.day))
    else node.children.forEach(collect)
  }
  tree.forEach(collect)
  return days.size
}

function toTaskNode(node: CarePlanNodeResponse): TaskNode {
  return {
    id: `task-${node.id}`,
    type: 'task',
    activityCode: node.activityCode,
    name: node.name,
    // The backend sends LocalTime as "HH:mm:ss"; the tree keeps "HH:mm".
    visits: node.visits.map((v) => ({ ...v, startTime: v.startTime.slice(0, 5) })),
    evidence: (node.evidenceType === 'NONE' ? 'CHECKLIST' : node.evidenceType) as EvidenceType,
  }
}

/** GET /api/care-plans/{id}/nodes's wire shape -> the frontend tree. The backend list is flat;
 * tasks sharing the same groupName are regrouped here into a sub-plan for display, in the order
 * each group first appears. A task with no groupName renders standalone. */
export function fromCarePlanNodeResponses(nodes: CarePlanNodeResponse[]): PlanNode[] {
  const result: PlanNode[] = []
  const groups = new Map<string, SubPlanNode>()
  for (const node of nodes) {
    const task = toTaskNode(node)
    if (!node.groupName) {
      result.push(task)
      continue
    }
    let group = groups.get(node.groupName)
    if (!group) {
      group = { id: `subplan-${node.groupName}`, type: 'subplan', name: node.groupName, children: [] }
      groups.set(node.groupName, group)
      result.push(group)
    }
    group.children.push(task)
  }
  return result
}

function toPlanTreeTask(task: TaskNode): PlanTreeTask {
  return {
    kind: 'task',
    id: task.id,
    label: task.name,
    schedule: scheduleTags(task.visits),
    weekly: formatHoursFixed(weeklyHours(task)),
  }
}

/** The editable tree -> PlanTree's display rows, with every figure formatted. */
export function toPlanTreeItems(tree: PlanNode[]): PlanTreeItem[] {
  return tree.map((node) =>
    node.type === 'task'
      ? toPlanTreeTask(node)
      : {
          kind: 'subplan',
          id: node.id,
          name: node.name,
          weekly: formatHoursFixed(weeklyHours(node)),
          tasks: node.children.map(toPlanTreeTask),
        },
  )
}

/** A task's visits -> the DaySchedule editor's value, each day pre-filled with its own start and minutes. */
export function dayScheduleFromVisits(visits: DayVisit[]): DayScheduleValue {
  const schedule = emptyDaySchedule()
  for (const visit of visits) {
    const day = WEEKDAYS.find((d) => d.short === visit.day)
    if (day) {
      schedule[day.key] = { active: true, startTime: toEditorTime(visit.startTime), minutes: visit.minutes }
    }
  }
  return schedule
}

/** The DaySchedule editor's active days -> visits, in weekday order. Call once isScheduleComplete. */
export function visitsFromDaySchedule(schedule: DayScheduleValue): DayVisit[] {
  return WEEKDAYS.filter((d) => schedule[d.key].active).map((d) => ({
    day: d.short,
    startTime: parseEditorTime(schedule[d.key].startTime) ?? '08:00',
    minutes: schedule[d.key].minutes ?? 0,
  }))
}

/** A schedule can be saved once at least one day is on and every day that's on has a start time and a duration. */
export function isScheduleComplete(schedule: DayScheduleValue): boolean {
  const active = WEEKDAYS.filter((d) => schedule[d.key].active)
  return (
    active.length > 0 &&
    active.every((d) => (schedule[d.key].minutes ?? 0) > 0 && parseEditorTime(schedule[d.key].startTime) !== null)
  )
}
