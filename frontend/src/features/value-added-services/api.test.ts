import {
  afterEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

import {
  assignValueAddedServiceCaregiver,
  cancelValueAddedServiceRequest,
  createElderValueAddedServiceRequest,
  fetchCaregiverCoverOptions,
  fetchManagedValueAddedServiceRequests,
  decideValueAddedServiceRequest,
  fetchElderValueAddedServiceRequests,
  fetchFamilyValueAddedServiceRequests,
  fetchValueAddedServices,
} from './api'

afterEach(() => {
  vi.unstubAllGlobals()

  document.cookie =
    'XSRF-TOKEN=; Max-Age=0; path=/'
})

function json(
  body: unknown,
  status = 200,
): Response {
  return new Response(
    JSON.stringify(body),
    {
      status,
      headers: {
        'Content-Type':
          'application/json',
      },
    },
  )
}

describe(
  'value-added services API',
  () => {
    it('loads the Elder catalogue', async () => {
      const catalogue = [
        {
          id: 1,
          name: 'Hospital escort',
          description:
            'Escort to medical appointments',
          status:
            'AVAILABLE' as const,
        },
      ]

      const fetchMock =
        vi.fn()
          .mockResolvedValue(
            json(catalogue),
          )

      vi.stubGlobal(
        'fetch',
        fetchMock,
      )

      await expect(
        fetchValueAddedServices(),
      ).resolves.toEqual(
        catalogue,
      )

      expect(
        fetchMock.mock.calls[0][0],
      ).toBe(
        '/api/elders/me/value-added-services',
      )
    })

    it('loads current Elder requests', async () => {
      const fetchMock =
        vi.fn()
          .mockResolvedValue(
            json([]),
          )

      vi.stubGlobal(
        'fetch',
        fetchMock,
      )

      await expect(
        fetchElderValueAddedServiceRequests(),
      ).resolves.toEqual([])

      expect(
        fetchMock.mock.calls[0][0],
      ).toBe(
        '/api/elders/me/value-added-service-requests',
      )
    })

    it('creates an Elder request with CSRF', async () => {
      document.cookie =
        'XSRF-TOKEN=extra%2Bservice; path=/'

      const response = {
        id: 5,
        elderId: 1,
        valueAddedServiceId: 1,
        serviceName:
          'Hospital escort',
        requestedByFamilyMemberId:
          null,
        approvingFamilyMemberId:
          null,
        visitId:
          null,
        requestedSchedule:
          '2026-10-10T10:00:00',
        specialInstructions:
          'Need assistance',
        status:
          'PENDING_APPROVAL' as const,
        decidedAt:
          null,
        createdAt:
          '2026-10-07T17:00:00',
      }

      const fetchMock =
        vi.fn()
          .mockResolvedValue(
            json(response, 201),
          )

      vi.stubGlobal(
        'fetch',
        fetchMock,
      )

      await expect(
        createElderValueAddedServiceRequest({
          valueAddedServiceId: 1,
          requestedSchedule:
            '2026-10-10T10:00',
          specialInstructions:
            'Need assistance',
        }),
      ).resolves.toEqual(
        response,
      )

      expect(
        fetchMock.mock.calls[0][0],
      ).toBe(
        '/api/elders/me/value-added-service-requests',
      )

      const init =
        fetchMock.mock.calls[0][1]

      expect(init.method)
        .toBe('POST')

      expect(
        JSON.parse(
          init.body as string,
        ),
      ).toEqual({
        valueAddedServiceId: 1,
        requestedSchedule:
          '2026-10-10T10:00',
        specialInstructions:
          'Need assistance',
      })

      expect(
        init.headers.get(
          'X-XSRF-TOKEN',
        ),
      ).toBe(
        'extra+service',
      )
    })

    it('loads requests for the selected Family elder', async () => {
      const fetchMock =
        vi.fn()
          .mockResolvedValue(
            json([]),
          )

      vi.stubGlobal(
        'fetch',
        fetchMock,
      )

      await fetchFamilyValueAddedServiceRequests(
        10,
      )

      expect(
        fetchMock.mock.calls[0][0],
      ).toBe(
        '/api/family/value-added-service-requests?elderId=10',
      )
    })

    it('approves a request', async () => {
      document.cookie =
        'XSRF-TOKEN=family%2Btoken; path=/'

      const fetchMock =
        vi.fn()
          .mockResolvedValue(
            json({
              id: 5,
              status:
                'DISPATCHED',
            }),
          )

      vi.stubGlobal(
        'fetch',
        fetchMock,
      )

      await decideValueAddedServiceRequest(
        5,
        'APPROVED',
      )

      expect(
        fetchMock.mock.calls[0][0],
      ).toBe(
        '/api/family/value-added-service-requests/5/decision',
      )

      const init =
        fetchMock.mock.calls[0][1]

      expect(init.method)
        .toBe('POST')

      expect(
        JSON.parse(
          init.body as string,
        ),
      ).toEqual({
        decision:
          'APPROVED',
      })

      expect(
        init.headers.get(
          'X-XSRF-TOKEN',
        ),
      ).toBe(
        'family+token',
      )
    })

    it('rejects a request', async () => {
      const fetchMock =
        vi.fn()
          .mockResolvedValue(
            json({
              id: 5,
              status:
                'REJECTED',
            }),
          )

      vi.stubGlobal(
        'fetch',
        fetchMock,
      )

      await decideValueAddedServiceRequest(
        5,
        'REJECTED',
      )

      const init =
        fetchMock.mock.calls[0][1]

      expect(
        JSON.parse(
          init.body as string,
        ),
      ).toEqual({
        decision:
          'REJECTED',
      })
    })
  },
)
describe('value-added services API for the manager', () => {
  it('loads every request and a visit\'s caregiver options', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(json([])))
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchManagedValueAddedServiceRequests()).resolves.toEqual([])
    await expect(fetchCaregiverCoverOptions(4)).resolves.toEqual([])

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/value-added-service-requests',
      '/api/value-added-service-requests/4/caregiver-options',
    ])
  })

  it('assigns a caregiver and cancels with CSRF', async () => {
    document.cookie = 'XSRF-TOKEN=manager; path=/'
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(json({ id: 4 })))
    vi.stubGlobal('fetch', fetchMock)

    await assignValueAddedServiceCaregiver(4, 9)
    await cancelValueAddedServiceRequest(4)

    const [assignUrl, assign] = fetchMock.mock.calls[0]
    expect(assignUrl).toBe('/api/value-added-service-requests/4/caregiver')
    expect(assign.method).toBe('POST')
    expect(assign.body).toBe(JSON.stringify({ caregiverId: 9 }))
    expect(new Headers(assign.headers).get('X-XSRF-TOKEN')).toBe('manager')

    const [cancelUrl, cancel] = fetchMock.mock.calls[1]
    expect(cancelUrl).toBe('/api/value-added-service-requests/4/cancellation')
    expect(cancel.method).toBe('POST')
  })
})
