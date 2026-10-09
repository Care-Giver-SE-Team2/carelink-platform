import { initialiseCsrf } from '../auth/api'
import { api } from '../../shared/api/client'
import type { Inbox, NotificationItem } from './types'

/** Refresh the bell after an independent detail page receives a successful read receipt. */
export const NOTIFICATIONS_CHANGED_EVENT = 'carelink:notifications-changed'

/**
 * One function per endpoint of the in-app inbox: the two the contract drafted for UC-FM05
 * (/notifications/me, /notifications/{id}/read) and the two the bell adds. The server answers for
 * whoever is signed in, so nothing here names a person. Every write initialises CSRF first.
 *
 * @author Wang Ziyu
 */

/** One page of my messages, newest first. */
export function fetchInbox(page = 0, size = 20, signal?: AbortSignal): Promise<Inbox> {
  return api<Inbox>(`/notifications/me?page=${page}&size=${size}`, { signal })
}

/** The number on the bell. */
export function fetchUnreadCount(signal?: AbortSignal): Promise<number> {
  return api<{ unread: number }>('/notifications/me/unread-count', { signal }).then((body) => body.unread)
}

/** I opened this message. */
export async function markNotificationRead(id: number, signal?: AbortSignal): Promise<NotificationItem> {
  await initialiseCsrf(signal)
  return api<NotificationItem>(`/notifications/${id}/read`, { method: 'POST', signal })
}

/** Everything I have is read; resolves to how many were not. */
export async function markAllNotificationsRead(signal?: AbortSignal): Promise<number> {
  await initialiseCsrf(signal)
  return api<{ updated: number }>('/notifications/me/read-all', { method: 'POST', signal }).then(
    (body) => body.updated,
  )
}
