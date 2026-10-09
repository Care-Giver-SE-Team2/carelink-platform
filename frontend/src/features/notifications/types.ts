/**
 * One in-app message, as GET /api/notifications/me returns it (the FamilyNotification shape of the
 * contract, for every role).
 */
export type NotificationItem = {
  id: number
  /** The elder the message is about; null for a message about the account itself. */
  elderId: number | null
  eventType: string
  channel: 'IN_APP'
  title: string
  body: string | null
  /** What it is about: INCIDENT, ROSTER_CHANGE, SPOT_CHECK, CREDENTIAL or ABSENCE. */
  resourceType: string | null
  resourceId: number | null
  /** SENT: in the inbox, not yet opened. READ: opened. */
  status: 'SENT' | 'READ'
  /** Singapore time with its offset, e.g. 2026-10-07T09:00:05+08:00. */
  createdAt: string
  sentAt: string | null
  readAt: string | null
}

/** One page of the signed-in person's messages, newest first. */
export type Inbox = {
  items: NotificationItem[]
  page: number
  size: number
  totalElements: number
}

/** Which client the person is in, which decides where a message leads. */
export type Portal = 'manager' | 'family' | 'caregiver' | 'elder'
