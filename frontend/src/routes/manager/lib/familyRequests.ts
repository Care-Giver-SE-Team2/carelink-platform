import { useQuery } from '@tanstack/react-query'
import { fetchElderCareRequests } from '../../../shared/api/profile'
import type { ElderCareRequest } from '../../../shared/api/profile'
import type { CareActivity } from '../../../shared/api/careplan'
import type { PlanNode, SubPlanNode, TaskNode } from '../data/carePlans'

/** What the family has applied for on the elder's behalf — the starting point of the care plan. */
export function useElderCareRequests(id: string | undefined) {
  return useQuery({
    queryKey: ['elderCareRequests', id],
    queryFn: () => fetchElderCareRequests(id!),
    enabled: id !== undefined,
  })
}

/** Every care need across the elder's applications, once each, in the order the family first asked for them. */
export function requestedNeeds(requests: ElderCareRequest[]): string[] {
  const oldestFirst = [...requests].reverse()
  return [...new Set(oldestFirst.flatMap((request) => request.careNeeds))]
}

/**
 * The first draft of a plan for an elder who has none: one task per catalog activity the family
 * asked for, filed under its catalog category like "Add sub-plan" would, in catalog order. The
 * tasks start with no visits — the family chose what care, not when — so the manager sets days
 * and times before publishing. Needs outside the catalog (older free-text applications) are left
 * for the manager to read in the rail.
 */
export function seedPlanFromRequests(needs: string[], catalog: CareActivity[]): PlanNode[] {
  const tree: SubPlanNode[] = []
  for (const activity of catalog) {
    if (!needs.includes(activity.code)) continue
    const task: TaskNode = {
      id: `task-requested-${activity.code}`,
      type: 'task',
      activityCode: activity.code,
      name: activity.label,
      visits: [],
      evidence: 'CHECKLIST',
    }
    const group = tree.find((node) => node.name === activity.category)
    if (group) group.children.push(task)
    else tree.push({ id: `subplan-requested-${activity.code}`, type: 'subplan', name: activity.category, children: [task] })
  }
  return tree
}

/** Tasks with no visits yet — the backend refuses to publish a task with nothing scheduled. */
export function unscheduledTasks(tree: PlanNode[]): TaskNode[] {
  return tree
    .flatMap((node) => (node.type === 'task' ? [node] : node.children))
    .filter((task) => task.visits.length === 0)
}

export type RequestedNeedStatus = 'planned' | 'unscheduled' | 'missing' | 'uncatalogued'

/** How the plan answers one requested need: a scheduled task, a task still without visits, no task, or not a
 * catalog activity. Matched on the task's activity code, so renaming a task doesn't unlink it. */
export function requestedNeedStatus(need: string, catalog: CareActivity[], tree: PlanNode[]): RequestedNeedStatus {
  if (!catalog.some((a) => a.code === need)) return 'uncatalogued'
  const tasks = tree
    .flatMap((node) => (node.type === 'task' ? [node] : node.children))
    .filter((task) => task.activityCode === need)
  if (tasks.length === 0) return 'missing'
  return tasks.some((task) => task.visits.length > 0) ? 'planned' : 'unscheduled'
}
