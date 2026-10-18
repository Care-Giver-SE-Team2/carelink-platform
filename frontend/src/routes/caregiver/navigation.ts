import { isCalendarDate } from './calendarDate'
import { todayInSingapore } from './format'

/** Only carry known, valid filters between caregiver pages, never arbitrary query data. */
export function scheduleSearch(source: URLSearchParams) {
  const dates = new URLSearchParams()
  for (const name of ['dateFrom', 'dateTo']) {
    const value = source.get(name)
    if (value && isCalendarDate(value)) dates.set(name, value)
  }
  if (dates.has('dateFrom') && dates.has('dateTo') && dates.get('dateFrom')! > dates.get('dateTo')!) return ''
  return dates.size ? '?' + dates.toString() : ''
}

const statuses = ['SCHEDULED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'VERIFIED', 'AUTO_CLOSED', 'EXCEPTION', 'CANCELLED'] as const
export const historyStatuses = statuses
export function historyFilters(source: URLSearchParams) {
  const rawMonth = source.get('month') ?? ''
  const month = /^(20\d{2}|2100)-(0[1-9]|1[0-2])$/.test(rawMonth) ? rawMonth : todayInSingapore().slice(0, 7)
  const rawStatus = source.get('status') ?? ''
  const status = statuses.some(value => value === rawStatus) ? rawStatus : ''
  const rawPage = source.get('page') ?? ''
  const page = /^\d{1,8}$/.test(rawPage) && Number(rawPage) <= 1000000 ? Number(rawPage) : 0
  return { month, status, page }
}
export function historySearch(filters: ReturnType<typeof historyFilters>) {
  const query = new URLSearchParams({ month: filters.month, page: String(filters.page) })
  if (filters.status) query.set('status', filters.status)
  return '?' + query.toString()
}
