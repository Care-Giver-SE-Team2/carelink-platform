import { useQuery } from '@tanstack/react-query'
import { getServiceApplication, listServiceApplications } from './api'

export const serviceApplicationKey = ['family-service-applications'] as const
export function useServiceApplications(page: number) {
  return useQuery({ queryKey: [...serviceApplicationKey, 'list', page],
    queryFn: ({ signal }) => listServiceApplications(page, signal), retry: false })
}
export function useServiceApplication(id: string) {
  return useQuery({ queryKey: [...serviceApplicationKey, 'detail', id],
    queryFn: ({ signal }) => getServiceApplication(id, signal), retry: false,
    enabled: /^[1-9]\d*$/.test(id) })
}
