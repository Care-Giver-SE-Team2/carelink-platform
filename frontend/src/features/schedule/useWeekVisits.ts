import { useQuery } from '@tanstack/react-query'
import { listFamilyVisits } from './api'
import { shiftDays } from './presentation'

/**
 * One elder's visits for a Monday-to-Sunday week (first 20), shown beside the weekly summary on desktop.
 * @param elderId Elder the summary is for
 * @param week Monday in YYYY-MM-DD format
 */
export function useWeekVisits(elderId: number, week: string) {
  return useQuery({
    queryKey: ['family-week-visits', elderId, week],
    queryFn: ({ signal }) => listFamilyVisits({ elderId, dateFrom: week, dateTo: shiftDays(week, 6), page: 0, size: 20 }, signal),
  })
}
