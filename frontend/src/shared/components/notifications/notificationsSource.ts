import { createContext, useContext } from 'react'

import {
  fetchInbox,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '../../../features/notifications/api'
import type { Inbox, NotificationItem } from '../../../features/notifications/types'

/** Where the bell gets its messages: the server in the app, a stand-in in a test. */
export type NotificationsSource = {
  unreadCount: (signal?: AbortSignal) => Promise<number>
  inbox: (page: number, size: number, signal?: AbortSignal) => Promise<Inbox>
  markRead: (id: number, signal?: AbortSignal) => Promise<NotificationItem>
  markAllRead: (signal?: AbortSignal) => Promise<number>
}

export const serverNotifications: NotificationsSource = {
  unreadCount: fetchUnreadCount,
  inbox: fetchInbox,
  markRead: markNotificationRead,
  markAllRead: markAllNotificationsRead,
}

/**
 * Null outside NotificationsProvider. The app provides it once at the root (main.tsx), so a
 * page rendered on its own - as every page test renders one - shows no bell and makes no
 * requests it did not ask for.
 */
export const NotificationsContext = createContext<NotificationsSource | null>(null)

export function useNotificationsSource(): NotificationsSource | null {
  return useContext(NotificationsContext)
}
