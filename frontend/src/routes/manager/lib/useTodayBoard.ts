import { useQuery, useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { fetchCaregivers } from '../../../shared/api/profile'
import { fetchDayRoster } from '../../../shared/api/visit'
import { fetchElders } from '../data/elders'
import { toKpis, toRoster } from '../data/today'
import type { Kpis } from '../data/today'
import { useOpenExceptionCount } from './useOpenExceptionCount'

// Lookups shared with the rest of the manager pages: same keys and fetchers as
// useElders / useCaregivers, so they are read once, not per refetch.
// 'static' returns what's cached and fetches only when nothing is.
const elders = (client: QueryClient) => client.query({ queryKey: ['elders'], queryFn: fetchElders, staleTime: 'static' })
const caregivers = (client: QueryClient) =>
  client.query({ queryKey: ['caregivers'], queryFn: fetchCaregivers, staleTime: 'static' })

/** Re-polled, like the exception count, so check-ins and overdue visits show without a reload. */
const REFRESH_MS = 30_000

/** Today's roster, fetched apart from how it's shown so a timeline view can reuse it. */
export function useTodayRoster() {
  const client = useQueryClient()
  return useQuery({
    queryKey: ['roster', 'today'],
    queryFn: async ({ signal }) => {
      const [visits, elderRows, caregiverRows] = await Promise.all([
        fetchDayRoster(undefined, signal),
        elders(client),
        caregivers(client),
      ])
      return toRoster(visits, elderRows, caregiverRows)
    },
    refetchInterval: REFRESH_MS,
  })
}

/**
 * Headline figures once everything behind them has loaded: visit counts from the roster,
 * exception counts from the same queries the nav reads, so the two never disagree.
 */
export function useTodayKpis(): { data: Kpis | undefined } {
  const roster = useTodayRoster()
  const open = useOpenExceptionCount()
  return {
    data: roster.data && open.data !== undefined ? toKpis(roster.data, open.data) : undefined,
  }
}
