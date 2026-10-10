import type { NotificationItem, Portal } from './types'

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** Opened already; SENT means in the inbox and not yet opened. */
export function isRead(item: Pick<NotificationItem, 'status'>): boolean {
  return item.status === 'READ'
}

/** The client a path belongs to; the manager console when nothing else matches. */
export function portalOf(pathname: string): Portal {
  if (pathname.startsWith('/family')) return 'family'
  if (pathname.startsWith('/caregiver')) return 'caregiver'
  if (pathname.startsWith('/elder')) return 'elder'
  return 'manager'
}

/** Where a message leads in this client, or null when there is no screen for it there. */
export function linkFor(item: Pick<NotificationItem, 'resourceType' | 'resourceId'>, portal: Portal): string | null {
  if (portal === 'family' && item.resourceType === 'INCIDENT') {
    return validId(item.resourceId) ? `/family/incidents/${item.resourceId}` : null
  }
  const routes: Record<string, Partial<Record<Portal, string>>> = {
    INCIDENT: { manager: item.resourceId == null ? '/manager/exceptions' : `/manager/exceptions/${item.resourceId}`, caregiver: item.resourceId == null ? '/caregiver/incidents' : `/caregiver/incidents/${item.resourceId}` },
    ABSENCE: { manager: item.resourceId == null ? '/manager/absences' : `/manager/absences/${item.resourceId}` },
    ROSTER_CHANGE: { manager: '/manager/absences', family: '/family/changes', caregiver: '/caregiver' },
    SPOT_CHECK: { manager: '/manager/quality', family: '/family/spot-checks', caregiver: item.resourceId == null ? '/caregiver/spot-checks' : `/caregiver/spot-checks?spotCheckId=${item.resourceId}` },
    CREDENTIAL: { manager: '/manager/certifications', caregiver: '/caregiver' },
    // An extra service's visit: staffed or reviewed on the manager's Extra services screen; for its
    // caregiver, the visit itself.
    VISIT: {
      manager: item.resourceId == null ? '/manager/extra-services' : `/manager/extra-services?visit=${item.resourceId}`,
      caregiver: item.resourceId == null ? '/caregiver' : `/caregiver/visits/${item.resourceId}`,
    },
    // An extra-service request, answered or followed on each side's Extra services screen.
    VALUE_ADDED_REQUEST: { family: '/family/extra-services', manager: '/manager/extra-services' },
    // A newly published care plan version, read on the family's Care plan page.
    CARE_PLAN: { family: '/family/care-plan' },
  }
  return (item.resourceType && routes[item.resourceType]?.[portal]) || null
}

/** A bell navigation context is not an authorization credential. Ignore malformed or stale pairs. */
export function familyIncidentNotificationId(state: unknown, incidentId: string): number | null {
  if (!state || typeof state !== 'object' || !('familyIncidentNotification' in state)) return null
  const context = state.familyIncidentNotification
  if (!context || typeof context !== 'object' || !('id' in context) || !('incidentId' in context)) return null
  return validId(context.id) && validId(context.incidentId) && String(context.incidentId) === incidentId ? context.id : null
}

export function validId(id: unknown): id is number {
  return typeof id === 'number' && Number.isSafeInteger(id) && id > 0
}

/**
 * "Wed 7 Oct, 09:00" from the server's Singapore time (its offset is ignored). Read from the
 * text rather than through Date, so the browser's own time zone cannot move it.
 */
export function when(createdAt: string): string {
  const [date, time = ''] = createdAt.split('T')
  const [year, month, day] = date.split('-').map(Number)
  if (!year || !month || !day) return createdAt
  const weekday = WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()]
  return `${weekday} ${day} ${MONTHS[month - 1]}, ${time.slice(0, 5)}`
}
