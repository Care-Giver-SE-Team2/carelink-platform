import { useQuery } from '@tanstack/react-query'
import { fetchCareActivities } from '../../shared/api/careplan'
import type { CareActivity } from '../../shared/api/careplan'

/**
 * The care activity catalog — one list for the family application forms and the manager's care
 * plan editor, so what a family asks for is always something the manager can plan. It only
 * changes with a deploy, so it is fetched once per session.
 */
export function useCareActivities() {
  return useQuery({
    queryKey: ['careActivities'],
    queryFn: ({ signal }) => fetchCareActivities(signal),
    staleTime: Infinity,
  })
}

export type CareActivityGroup = { category: string; activities: CareActivity[] }

/** The catalog grouped by category, keeping catalog order for both the groups and their activities. */
export function groupCareActivities(catalog: CareActivity[]): CareActivityGroup[] {
  const groups: CareActivityGroup[] = []
  for (const activity of catalog) {
    const group = groups.find((g) => g.category === activity.category)
    if (group) group.activities.push(activity)
    else groups.push({ category: activity.category, activities: [activity] })
  }
  return groups
}

/** A stored care need as people read it: the catalog label, or the family's own words on an older application. */
export function careNeedLabel(catalog: CareActivity[] | undefined, need: string): string {
  return catalog?.find((activity) => activity.code === need)?.label ?? need
}
