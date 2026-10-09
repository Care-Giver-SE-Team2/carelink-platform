import type { TagTone } from '../../../shared/components/ui'
import type { ManagedValueAddedServiceRequest } from '../../../features/value-added-services/types'

export type ExtraServiceFilter = 'needs' | 'awaiting' | 'scheduled' | 'closed' | 'all'

export const EXTRA_SERVICE_FILTERS: { value: ExtraServiceFilter; label: string }[] = [
  { value: 'needs', label: 'NEEDS CAREGIVER' },
  { value: 'awaiting', label: 'AWAITING FAMILY' },
  { value: 'scheduled', label: 'SCHEDULED' },
  { value: 'closed', label: 'CLOSED' },
  { value: 'all', label: 'ALL' },
]

export const EXTRA_SERVICE_PAGE_SIZE = 12

const CLOSED: ManagedValueAddedServiceRequest['status'][] = ['COMPLETED', 'REJECTED', 'CANCELLED']

export function matchesFilter(row: ManagedValueAddedServiceRequest, filter: ExtraServiceFilter): boolean {
  switch (filter) {
    case 'needs':
      return row.needsCaregiver
    case 'awaiting':
      return row.status === 'PENDING_APPROVAL'
    case 'scheduled':
      return row.status === 'DISPATCHED' && !row.needsCaregiver
    case 'closed':
      return CLOSED.includes(row.status)
    case 'all':
      return true
  }
}

export function isExtraServiceFilter(value: string | null): value is ExtraServiceFilter {
  return EXTRA_SERVICE_FILTERS.some((filter) => filter.value === value)
}

/** Where a request stands, as one tag: the visit's state once there is one, else the request's. */
export function stateTag(row: ManagedValueAddedServiceRequest): { label: string; tone: TagTone } {
  if (row.needsCaregiver) return { label: 'NEEDS CAREGIVER', tone: 'danger' }
  switch (row.status) {
    case 'PENDING_APPROVAL':
      return { label: 'AWAITING FAMILY', tone: 'muted' }
    case 'REJECTED':
      return { label: 'DECLINED', tone: 'muted' }
    case 'CANCELLED':
      return { label: 'CANCELLED', tone: 'muted' }
    case 'COMPLETED':
      return { label: 'COMPLETED', tone: 'muted' }
    default:
      break
  }
  switch (row.visitStatus) {
    case 'EXCEPTION':
      return { label: 'EXCEPTION', tone: 'danger' }
    case 'ARRIVED':
    case 'IN_PROGRESS':
      return { label: 'IN VISIT', tone: 'accent' }
    case 'COMPLETED':
    case 'VERIFIED':
    case 'AUTO_CLOSED':
      return { label: 'COMPLETED', tone: 'muted' }
    case 'CANCELLED':
      return { label: 'CANCELLED', tone: 'muted' }
    default:
      return { label: 'SCHEDULED', tone: 'ink' }
  }
}

/**
 * Whether the manager can still call the request off: while the family has not answered, or
 * while its visit has not started. Mirrors the server, which refuses otherwise.
 */
export function canCancel(row: ManagedValueAddedServiceRequest): boolean {
  if (row.status === 'PENDING_APPROVAL') return true
  return row.status === 'DISPATCHED' && (row.visitStatus === null || row.visitStatus === 'SCHEDULED' || row.visitStatus === 'CANCELLED')
}

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/**
 * "Sat 10 Oct, 10:00" from a server wall-clock time in Singapore ("2026-10-10T10:00:00"). Read
 * from the text, not through the browser's time zone, so a manager abroad sees the elder's time.
 */
export function formatWhen(local: string | null): string {
  const match = local && /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(local)
  if (!match) return '—'
  const [, year, month, day, hour, minute] = match
  const weekday = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day))).getUTCDay()
  return `${WEEKDAYS[weekday]} ${Number(day)} ${MONTHS[Number(month) - 1]}, ${hour}:${minute}`
}

/** The row to open: the one named in the URL by request or visit id, else the first shown. */
export function selectedRow(
  rows: ManagedValueAddedServiceRequest[],
  shown: ManagedValueAddedServiceRequest[],
  id: number | null,
  visitId: number | null,
): ManagedValueAddedServiceRequest | null {
  return (
    (id !== null ? rows.find((row) => row.id === id) : undefined) ??
    (visitId !== null ? rows.find((row) => row.visitId === visitId) : undefined) ??
    shown[0] ??
    null
  )
}

/** What the table says when a filter has nothing in it. */
export const EXTRA_SERVICE_EMPTY_TEXT: Record<ExtraServiceFilter, string> = {
  needs: 'Every dispatched visit has a caregiver.',
  awaiting: 'No requests are waiting for a family’s answer.',
  scheduled: 'No extra-service visits are coming up.',
  closed: 'Nothing completed, declined or cancelled yet.',
  all: 'No elder has asked for an extra service yet. Requests from the elder app appear here.',
}
