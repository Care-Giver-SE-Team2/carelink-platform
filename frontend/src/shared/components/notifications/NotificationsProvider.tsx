import type { ReactNode } from 'react'

import { NotificationsContext, serverNotifications } from './notificationsSource'
import type { NotificationsSource } from './notificationsSource'

/** Turns the bell on for everything inside it; main.tsx wraps the whole app in one. */
export function NotificationsProvider({
  source = serverNotifications,
  children,
}: {
  source?: NotificationsSource
  children: ReactNode
}) {
  return <NotificationsContext.Provider value={source}>{children}</NotificationsContext.Provider>
}
