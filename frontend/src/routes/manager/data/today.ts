import type { VisitState } from '../../../shared/components/ui'
import type { VisitResponse } from '../../../shared/api/visit'
import type { CaregiverOption } from '../../../shared/api/profile'
import type { ElderRow } from './elders'

/**
 * Today board data — the board's own shapes, mapped from the real endpoints:
 *
 * - roster ← GET /api/visits/roster (today's visits), named via GET /api/elders and GET /api/caregivers.
 * - KPIs are counted from the roster, plus the open exception count from
 *   GET /api/incidents (see useOpenExceptionCount).
 */

export type Visit = {
  id: string
  /** "HH:MM", Singapore time. */
  time: string
  elder: { name: string }
  /** Absent while the visit is unassigned. */
  caregiver?: { name: string }
  service: string
  state: VisitState
  /** Absent until the caregiver checks in. Times are "HH:MM", Singapore time. */
  attendance?: Attendance
}

export type Attendance = {
  checkedIn: string
  /** Absent until the caregiver checks out. */
  checkedOut?: string
  /** Minutes after the scheduled start the caregiver checked in; 0 when on time or early. */
  lateMinutes: number
}

export type TodayRoster = {
  visits: Visit[]
}

export type Kpis = {
  scheduled: number
  completed: number
  unassigned: number
  openExceptions: number
}

const VISIT_STATES: Record<VisitResponse['status'], VisitState> = {
  SCHEDULED: 'scheduled',
  ARRIVED: 'in_visit',
  IN_PROGRESS: 'in_visit',
  COMPLETED: 'closed',
  VERIFIED: 'verified',
  AUTO_CLOSED: 'closed',
  EXCEPTION: 'exception',
  CANCELLED: 'closed',
}

/** The backend's visit times are Singapore wall-clock `LocalDateTime`s with no offset; Singapore has no DST. */
function singaporeInstant(localDateTime: string): number {
  return new Date(`${localDateTime}+08:00`).getTime()
}

function attendanceOf(visit: VisitResponse): Attendance | undefined {
  if (visit.checkedInAt == null) return undefined
  const late = (singaporeInstant(visit.checkedInAt) - singaporeInstant(visit.scheduledStart)) / 60_000
  return {
    checkedIn: visit.checkedInAt.slice(11, 16),
    checkedOut: visit.checkedOutAt?.slice(11, 16),
    lateMinutes: Math.max(0, Math.floor(late)),
  }
}

/**
 * An assigned visit whose start has passed with nobody checked in. Flagged from the start
 * time, not the backend's lateness threshold — the missed check-in incident covers that.
 */
function overdue(visit: VisitResponse, now: Date): boolean {
  return (
    visit.caregiverId != null &&
    visit.status === 'SCHEDULED' &&
    visit.checkedInAt == null &&
    singaporeInstant(visit.scheduledStart) < now.getTime()
  )
}

export function toRoster(
  visits: VisitResponse[],
  elders: ElderRow[],
  caregivers: CaregiverOption[],
  now: Date = new Date(),
): TodayRoster {
  const elderById = new Map(elders.map((elder) => [elder.id, elder]))
  const caregiverById = new Map(caregivers.map((caregiver) => [caregiver.id, caregiver.fullName]))

  const rows = visits.map((visit): Visit => {
    const id = String(visit.id)
    const elder = elderById.get(String(visit.elderId))
    const caregiver = visit.caregiverId == null ? undefined : caregiverById.get(visit.caregiverId)
    return {
      id,
      time: visit.scheduledStart.slice(11, 16),
      elder: { name: elder?.name ?? `Elder #${visit.elderId}` },
      caregiver: caregiver ? { name: caregiver } : undefined,
      service: visit.serviceType ?? '—',
      // An exception outranks the gap: an uncovered visit past its start is an exception.
      state:
        visit.caregiverId == null && visit.status !== 'EXCEPTION'
          ? 'needs_cover'
          : overdue(visit, now)
            ? 'not_checked_in'
            : VISIT_STATES[visit.status],
      attendance: attendanceOf(visit),
    }
  })
  return { visits: rows }
}

export function toKpis(roster: TodayRoster, openExceptions: number): Kpis {
  return {
    scheduled: roster.visits.length,
    completed: roster.visits.filter((visit) => visit.state === 'closed' || visit.state === 'verified').length,
    unassigned: roster.visits.filter((visit) => !visit.caregiver).length,
    openExceptions,
  }
}
