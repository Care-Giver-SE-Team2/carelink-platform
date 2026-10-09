import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'

import type { Inbox, NotificationItem } from '../../../features/notifications/types'
import { NotificationBell } from './NotificationBell'
import { NotificationsProvider } from './NotificationsProvider'
import type { NotificationsSource } from './notificationsSource'

/**
 * The bell against a stand-in for the server: the count, the list, what opening a message
 * does in each client, and that nothing happens outside the app's provider.
 *
 * @author Wang Ziyu
 */

const change: NotificationItem = {
  id: 2,
  elderId: 7,
  eventType: 'ROSTER_CHANGE_OFFERED',
  channel: 'IN_APP',
  title: 'Your caregiver is away for the visit on Thu 9 Oct, 10:00',
  body: 'We suggest Tan Mei Ling.',
  resourceType: 'ROSTER_CHANGE',
  resourceId: 40,
  status: 'SENT',
  createdAt: '2026-10-07T09:00:05+08:00',
  sentAt: '2026-10-07T09:00:06+08:00',
  readAt: null,
}

const incident: NotificationItem = {
  id: 1,
  elderId: 7,
  eventType: 'INCIDENT_RAISED',
  channel: 'IN_APP',
  title: 'HIGH: FALL',
  body: null,
  resourceType: 'INCIDENT',
  resourceId: 7,
  status: 'READ',
  createdAt: '2026-10-06T18:30:00+08:00',
  sentAt: '2026-10-06T18:30:01+08:00',
  readAt: '2026-10-06T19:00:00+08:00',
}

function source(items: NotificationItem[], overrides: Partial<NotificationsSource> = {}): NotificationsSource {
  let current = [...items]
  const unread = () => current.filter((item) => item.status === 'SENT').length
  const inbox = (): Inbox => ({ items: current, page: 0, size: 20, totalElements: current.length })
  return {
    unreadCount: vi.fn().mockImplementation(async () => unread()),
    inbox: vi.fn().mockImplementation(async () => inbox()),
    markRead: vi.fn().mockImplementation(async (id: number) => {
      current = current.map((item) => item.id === id ? { ...item, status: 'READ' } : item)
      return current.find((item) => item.id === id)!
    }),
    markAllRead: vi.fn().mockImplementation(async () => {
      const count = unread(); current = current.map((item) => ({ ...item, status: 'READ' })); return count
    }),
    ...overrides,
  }
}

function renderAt(path: string, stub: NotificationsSource | null) {
  const routes = (
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/family/schedule" element={<NotificationBell />} />
        <Route path="/manager/home" element={<NotificationBell />} />
        <Route path="/caregiver" element={<NotificationBell />} />
        <Route path="/manager/exceptions/:id" element={<IncidentDestination />} />
        <Route path="/caregiver/incidents/:id" element={<IncidentDestination />} />
        <Route path="/family/changes" element={<p>Visit changes page</p>} />
        <Route path="/family/incidents/:id" element={<IncidentDestination />} />
      </Routes>
    </MemoryRouter>
  )
  return render(stub ? <NotificationsProvider source={stub}>{routes}</NotificationsProvider> : routes)
}

function IncidentDestination() {
  const location = useLocation()
  return <p>Incident destination {JSON.stringify(location.state)}</p>
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

it('shows nothing and asks nothing outside the app provider', () => {
  renderAt('/family/schedule', null)

  expect(screen.queryByRole('button', { name: /Notifications/ })).not.toBeInTheDocument()
})

it('shows the unread count and opens the list newest first', async () => {
  renderAt('/family/schedule', source([change, incident]))

  const bell = await screen.findByRole('button', { name: 'Notifications, 1 unread' })
  await userEvent.click(bell)

  const panel = await screen.findByRole('dialog', { name: 'Notifications' })
  const items = await within(panel).findAllByRole('listitem')
  expect(items).toHaveLength(2)
  expect(items[0]).toHaveTextContent('Unread: Your caregiver is away for the visit on Thu 9 Oct, 10:00')
  expect(items[0]).toHaveTextContent('Wed 7 Oct, 09:00')
  expect(items[1]).not.toHaveTextContent('Unread:')
  expect(bell).toHaveAttribute('aria-expanded', 'true')
})

it('opening an unread message marks it read and goes to its screen', async () => {
  const stub = source([change, incident])
  renderAt('/family/schedule', stub)

  await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
  await userEvent.click(await screen.findByRole('button', { name: /Your caregiver is away/ }))

  expect(stub.markRead).toHaveBeenCalledWith(2, expect.any(AbortSignal))
  expect(await screen.findByText('Visit changes page')).toBeInTheDocument()
})

it('a message with no screen in this client is only marked read', async () => {
  const unreadIncident: NotificationItem = { ...incident, resourceType: null, resourceId: null, status: 'SENT', readAt: null }
  const stub = source([unreadIncident])
  renderAt('/family/schedule', stub)

  await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
  await userEvent.click(await screen.findByRole('button', { name: /HIGH: FALL/ }))

  expect(stub.markRead).toHaveBeenCalledWith(1, expect.any(AbortSignal))
  expect(screen.getByRole('dialog', { name: 'Notifications' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Notifications' })).toBeInTheDocument()
})

it('marks everything read at once', async () => {
  const stub = source([change, { ...incident, id: 3, status: 'SENT', readAt: null }])
  renderAt('/family/schedule', stub)

  await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 2 unread' }))
  const panel = await screen.findByRole('dialog', { name: 'Notifications' })
  await within(panel).findAllByRole('listitem')
  await userEvent.click(within(panel).getByRole('button', { name: 'Mark all as read' }))

  expect(stub.markAllRead).toHaveBeenCalled()
  expect(await screen.findByRole('button', { name: 'Notifications' })).toBeInTheDocument()
  expect(within(panel).getByRole('button', { name: 'Mark all as read' })).toBeDisabled()
})

it('says when the list could not be loaded, and tries again', async () => {
  const stub = source([change])
  stub.inbox = vi
    .fn()
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValue({ items: [change], page: 0, size: 20, totalElements: 1 })
  renderAt('/family/schedule', stub)

  await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
  const alert = await screen.findByRole('alert')
  expect(alert).toHaveTextContent('Your notifications could not be loaded.')
  await userEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

  expect(await screen.findByRole('button', { name: /Your caregiver is away/ })).toBeInTheDocument()
})

it('an empty inbox says what will appear there, and Escape closes it', async () => {
  renderAt('/family/schedule', source([]))

  await userEvent.click(await screen.findByRole('button', { name: 'Notifications' }))
  expect(await screen.findByText(/Nothing yet/)).toBeInTheDocument()
  await userEvent.keyboard('{Escape}')

  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
})

it('floats in the corner for a client with no shared header', async () => {
  render(
    <NotificationsProvider source={source([])}>
      <MemoryRouter initialEntries={['/family/home']}>
        <NotificationBell floating />
      </MemoryRouter>
    </NotificationsProvider>,
  )

  const bell = await screen.findByRole('button', { name: 'Notifications' })
  expect(bell.parentElement?.className).toMatch(/floating/)
})

it('a click outside closes it', async () => {
  renderAt('/family/schedule', source([change]))

  await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
  await screen.findByRole('dialog', { name: 'Notifications' })
  await userEvent.click(document.body)

  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
})

it('family incident navigation carries context but does not mark the notification read', async () => {
  const stub = source([{ ...incident, status: 'SENT', readAt: null }])
  renderAt('/family/schedule', stub)
  await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
  await userEvent.click(await screen.findByRole('button', { name: /HIGH: FALL/ }))
  expect(await screen.findByText(/Incident destination/)).toHaveTextContent('"familyIncidentNotification":{"id":1,"incidentId":7}')
  expect(stub.markRead).not.toHaveBeenCalled()
})

it('failed single read keeps the item unread and count intact until an explicit retry succeeds', async () => {
  let fail = true
  const stub = source([{ ...incident, resourceType: null, resourceId: null, status: 'SENT', readAt: null }], {
    markRead: vi.fn().mockImplementation(async () => {
      if (fail) throw new Error('Private network failure')
      return { ...incident, status: 'READ' }
    }), unreadCount: vi.fn().mockImplementation(async () => fail ? 1 : 0),
  })
  renderAt('/family/schedule', stub)
  await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
  await userEvent.click(await screen.findByRole('button', { name: /HIGH: FALL/ }))
  const alert = await screen.findByRole('alert')
  expect(alert).toHaveTextContent('could not be marked as read')
  expect(screen.getByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /Unread:\s*HIGH/ })).toBeInTheDocument()
  expect(screen.queryByText('Private network failure')).not.toBeInTheDocument()
  fail = false
  await userEvent.click(within(alert).getByRole('button', { name: 'Try again' }))
  await screen.findByRole('button', { name: 'Notifications' })
  expect(screen.getByRole('button', { name: /HIGH: FALL/ })).not.toHaveTextContent('Unread:')
  expect(stub.markRead).toHaveBeenCalledTimes(2)
})

it('failed read-all keeps the visible messages and unread count', async () => {
  const stub = source([change], { markAllRead: vi.fn().mockRejectedValue(new Error('write failed')) })
  renderAt('/family/schedule', stub)
  await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Mark all as read' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('could not be marked as read')
  expect(screen.getByRole('button', { name: /Unread:\s*Your caregiver/ })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument()
})


it.each(['/manager/home', '/caregiver'])('staff incident messages at %s keep their destination and existing read policy', async (path) => {
  const stub = source([{ ...incident, status: 'SENT', readAt: null }])
  renderAt(path, stub)
  await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
  await userEvent.click(await screen.findByRole('button', { name: /HIGH: FALL/ }))
  expect(stub.markRead).toHaveBeenCalledWith(1, expect.any(AbortSignal))
  expect(await screen.findByText('Incident destination null')).toBeInTheDocument()
})

it.each([{ id: 0 }, { resourceId: null }, { resourceId: -1 }])('family invalid incident links %j neither navigate nor mark read', async (change) => {
  const stub = source([{ ...incident, status: 'SENT', readAt: null, ...change }])
  renderAt('/family/schedule', stub)
  await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
  await userEvent.click(await screen.findByRole('button', { name: /HIGH: FALL/ }))
  expect(await screen.findByRole('alert')).toHaveTextContent('This incident notification has an invalid link.')
  expect(stub.markRead).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument()
  expect(screen.queryByText(/Incident destination/)).not.toBeInTheDocument()
})

it('does not optimistically read pending writes, prevents duplicates and cancels on unmount', async () => {
  let complete!: (item: NotificationItem) => void
  const response = new Promise<NotificationItem>((resolve) => { complete = resolve })
  const stub = source([change], { markRead: vi.fn().mockReturnValue(response) })
  const app = renderAt('/family/schedule', stub)
  await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
  const item = await screen.findByRole('button', { name: /Your caregiver is away/ })
  await userEvent.click(item); await userEvent.click(item)
  expect(item).toBeDisabled(); expect(item).toHaveTextContent('Unread:')
  expect(screen.getByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument()
  expect(stub.markRead).toHaveBeenCalledTimes(1)
  const signal = vi.mocked(stub.markRead).mock.calls[0][1]!
  app.unmount(); expect(signal.aborted).toBe(true)
  await act(async () => complete({ ...change, status: 'READ' }))
  expect(screen.queryByText('Visit changes page')).not.toBeInTheDocument()
})
