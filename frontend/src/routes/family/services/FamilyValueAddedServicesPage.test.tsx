import {
  cleanup,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  afterEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

import {
  decideValueAddedServiceRequest,
  fetchFamilyValueAddedServiceRequests,
} from '../../../features/value-added-services/api'
import {
  useFamilyElders,
} from '../../../features/family-account/useFamilyAccount'
import {
  useSelectedElder,
} from '../components/selectedElder'
import {
  FamilyValueAddedServicesPage,
} from './FamilyValueAddedServicesPage'

vi.mock(
  '../../../features/value-added-services/api',
  () => ({
    decideValueAddedServiceRequest:
      vi.fn(),
    fetchFamilyValueAddedServiceRequests:
      vi.fn(),
  }),
)

/* Booking a service has its own tests (FamilyValueAddedRequestForm.test.tsx). */
vi.mock(
  './FamilyValueAddedRequestForm',
  () => ({
    FamilyValueAddedRequestForm:
      () => null,
  }),
)

vi.mock(
  '../../../features/family-account/useFamilyAccount',
  () => ({
    useFamilyElders:
      vi.fn(),
  }),
)

vi.mock(
  '../components/selectedElder',
  () => ({
    useSelectedElder:
      vi.fn(),
  }),
)

const mockedFetch =
  vi.mocked(
    fetchFamilyValueAddedServiceRequests,
  )

const mockedDecision =
  vi.mocked(
    decideValueAddedServiceRequest,
  )

const mockedElders =
  vi.mocked(
    useFamilyElders,
  )

const mockedSelected =
  vi.mocked(
    useSelectedElder,
  )

const pending = {
  id: 5,
  elderId: 10,
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

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function prepare() {
  mockedSelected
    .mockReturnValue({
      elderId: 10,
      setElderId:
        vi.fn(),
    })

  mockedElders
    .mockReturnValue({
      data: [
        {
          id: 10,
          fullName:
            'Test Elder',
        },
      ],
    } as ReturnType<
      typeof useFamilyElders
    >)
}

describe(
  'FM08 family value-added service approval',
  () => {
    it('loads pending requests for selected elder', async () => {
      prepare()

      mockedFetch
        .mockResolvedValue(
          [pending],
        )

      render(
        <FamilyValueAddedServicesPage />,
      )

      expect(
        await screen.findByText(
          'Hospital escort',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'Need assistance',
        ),
      ).toBeInTheDocument()

      expect(
        mockedFetch,
      ).toHaveBeenCalledWith(
        10,
      )

      expect(
        screen.getByRole(
          'button',
          {
            name:
              'Approve',
          },
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByRole(
          'button',
          {
            name:
              'Reject',
          },
        ),
      ).toBeInTheDocument()
    })

    it('approves request and moves it to decision history', async () => {
      prepare()

      mockedFetch
        .mockResolvedValue(
          [pending],
        )

      mockedDecision
        .mockResolvedValue({
          ...pending,
          approvingFamilyMemberId:
            20,
          visitId:
            77,
          status:
            'DISPATCHED',
          decidedAt:
            '2026-10-07T17:10:00',
        })

      render(
        <FamilyValueAddedServicesPage />,
      )

      const user =
        userEvent.setup()

      await user.click(
        await screen.findByRole(
          'button',
          {
            name:
              'Approve',
          },
        ),
      )

      await waitFor(() => {
        expect(
          mockedDecision,
        ).toHaveBeenCalledWith(
          5,
          'APPROVED',
        )
      })

      expect(
        await screen.findByText(
          /Status:/,
        ),
      ).toHaveTextContent(
        'DISPATCHED',
      )

      expect(
        screen.getByText(
          /Visit #77/,
        ),
      ).toBeInTheDocument()

      expect(
        screen.queryByRole(
          'button',
          {
            name:
              'Approve',
          },
        ),
      ).not.toBeInTheDocument()
    })

    it('rejects request and moves it to history without visit', async () => {
      prepare()

      mockedFetch
        .mockResolvedValue(
          [pending],
        )

      mockedDecision
        .mockResolvedValue({
          ...pending,
          approvingFamilyMemberId:
            20,
          status:
            'REJECTED',
          decidedAt:
            '2026-10-07T17:10:00',
        })

      render(
        <FamilyValueAddedServicesPage />,
      )

      await userEvent
        .setup()
        .click(
          await screen.findByRole(
            'button',
            {
              name:
                'Reject',
            },
          ),
        )

      expect(
        await screen.findByText(
          /Status:/,
        ),
      ).toHaveTextContent(
        'REJECTED',
      )

      expect(
        screen.queryByText(
          /Visit #/,
        ),
      ).not.toBeInTheDocument()
    })

    it('shows empty pending state', async () => {
      prepare()

      mockedFetch
        .mockResolvedValue([])

      render(
        <FamilyValueAddedServicesPage />,
      )

      expect(
        await screen.findByText(
          'No requests are waiting for approval.',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'No decisions yet.',
        ),
      ).toBeInTheDocument()
    })

    it('shows load failure', async () => {
      prepare()

      mockedFetch
        .mockRejectedValue(
          new Error('network'),
        )

      render(
        <FamilyValueAddedServicesPage />,
      )

      expect(
        await screen.findByRole(
          'alert',
        ),
      ).toHaveTextContent(
        'Unable to load service requests.',
      )
    })
  },
)