import { afterEach, describe, expect, it, vi } from 'vitest'

import { fetchInbox, fetchUnreadCount, markAllNotificationsRead, markNotificationRead } from './api'

/** The request each inbox function makes: path, method, and CSRF before every write. @author Wang Ziyu */

function stubFetch(body: unknown) {
  const fetchMock = vi.fn().mockImplementation((url: string) => {
    if (url.endsWith('/csrf')) {
      document.cookie = 'XSRF-TOKEN=bell-token; path=/'
      return Promise.resolve(new Response(null))
    }
    return Promise.resolve(new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } }))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function lastRequest(fetchMock: ReturnType<typeof vi.fn>) {
  const calls = fetchMock.mock.calls.filter(([url]) => !String(url).endsWith('/csrf'))
  const [url, init] = calls[calls.length - 1]
  return { url: String(url), init: init as RequestInit }
}

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('inbox reads', () => {
  it('asks for a page of my messages and for the count alone', async () => {
    const fetchMock = stubFetch({ items: [], page: 1, size: 5, totalElements: 3 })

    const inbox = await fetchInbox(1, 5)
    expect(lastRequest(fetchMock).url).toBe('/api/notifications/me?page=1&size=5')
    expect(inbox.totalElements).toBe(3)

    const countMock = stubFetch({ unread: 4 })
    await expect(fetchUnreadCount()).resolves.toBe(4)
    expect(lastRequest(countMock).url).toBe('/api/notifications/me/unread-count')
  })
})

describe('inbox writes', () => {
  it('initialises CSRF and posts to mark one or all read', async () => {
    const fetchMock = stubFetch({ updated: 2 })

    await markNotificationRead(9)
    expect(lastRequest(fetchMock).url).toBe('/api/notifications/9/read')
    expect(lastRequest(fetchMock).init.method).toBe('POST')
    expect(new Headers(lastRequest(fetchMock).init.headers).get('X-XSRF-TOKEN')).toBe('bell-token')

    await expect(markAllNotificationsRead()).resolves.toBe(2)
    expect(lastRequest(fetchMock).url).toBe('/api/notifications/me/read-all')
  })
})
