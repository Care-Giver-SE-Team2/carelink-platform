import {
  cleanup,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  MemoryRouter,
} from 'react-router-dom'
import {
  afterEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

import {
  createFamilyBinding,
  getFamilyBindings,
  revokeFamilyBinding,
} from '../../../features/family-binding/api'
import { ApiError } from '../../../shared/api/client'
import FamilyBindings from './FamilyBindings'

vi.mock(
  '../../../features/family-binding/api',
  () => ({
    createFamilyBinding:
      vi.fn(),
    getFamilyBindings:
      vi.fn(),
    revokeFamilyBinding:
      vi.fn(),
  }),
)

/*
 * ElderShell (session handling) is not under test here.
 * These tests focus on EL04 Elder-side behaviour.
 */
vi.mock(
  '../components/ElderShell',
  () => ({
    ElderShell: ({
      children,
    }: {
      children:
        React.ReactNode
    }) => (
      <div>{children}</div>
    ),
  }),
)

const mockedGet =
  vi.mocked(
    getFamilyBindings,
  )

const mockedCreate =
  vi.mocked(
    createFamilyBinding,
  )

const mockedRevoke =
  vi.mocked(
    revokeFamilyBinding,
  )

const pendingBinding = {
  id: 10,
  familyMemberId: 3,
  familyMemberName:
    'Family Test',
  relationship: 'SON' as const,
  primaryContact: true,
  accessScope: 'FULL' as const,
  status:
    'PENDING_CONFIRMATION' as const,
  confirmedAt: null,
  createdAt:
    '2026-09-21T10:00:00',
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function renderPage() {
  return render(
    <MemoryRouter>
      <FamilyBindings />
    </MemoryRouter>,
  )
}

describe(
  'Elder family bindings',
  () => {
    it('shows existing family bindings', async () => {
      mockedGet.mockResolvedValue(
        [pendingBinding],
      )

      renderPage()

      expect(
        await screen.findByRole(
          'heading',
          {
            name:
              'Family Test',
          },
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'Son · Primary contact',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          /Access: Full/,
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          /Status: Pending confirmation/,
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByRole(
          'button',
          {
            name:
              'Remove family member',
          },
        ),
      ).toBeInTheDocument()
    })

    it('shows an empty state when no family member is linked', async () => {
      mockedGet.mockResolvedValue(
        [],
      )

      renderPage()

      expect(
        await screen.findByText(
          'You do not have any family members linked yet.',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByRole(
          'heading',
          {
            name:
              'Bind another family member',
          },
        ),
      ).toBeInTheDocument()
    })

    it('creates a family binding request', async () => {
      mockedGet.mockResolvedValue(
        [],
      )

      mockedCreate.mockResolvedValue(
        pendingBinding,
      )

      renderPage()

      const user =
        userEvent.setup()

      const username =
        await screen.findByLabelText(
          'Family username',
        )

      await user.type(
        username,
        '  family_test  ',
      )

      await user.selectOptions(
        screen.getByLabelText(
          'Relationship',
        ),
        'SON',
      )

      await user.selectOptions(
        screen.getByLabelText(
          'Access',
        ),
        'FULL',
      )

      await user.click(
        screen.getByLabelText(
          'Primary contact',
        ),
      )

      await user.click(
        screen.getByRole(
          'button',
          {
            name:
              'Send binding request',
          },
        ),
      )

      await waitFor(() => {
        expect(
          mockedCreate,
        ).toHaveBeenCalledWith({
          familyUsername:
            'family_test',
          relationship: 'SON',
          primaryContact: true,
          accessScope: 'FULL',
        })
      })

      expect(
        await screen.findByText(
          'Family binding request created.',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByRole(
          'heading',
          {
            name:
              'Family Test',
          },
        ),
      ).toBeInTheDocument()

      expect(username)
        .toHaveValue('')
    })

    it('shows 404 message when family username does not exist', async () => {
      mockedGet.mockResolvedValue(
        [],
      )

      mockedCreate.mockRejectedValue(
        new ApiError(
          'Not found',
          404,
        ),
      )

      renderPage()

      const user =
        userEvent.setup()

      await user.type(
        await screen.findByLabelText(
          'Family username',
        ),
        'missing',
      )

      await user.click(
        screen.getByRole(
          'button',
          {
            name:
              'Send binding request',
          },
        ),
      )

      expect(
        await screen.findByText(
          'No family member account was found with that username.',
        ),
      ).toBeInTheDocument()
      expect(
        screen.getByLabelText(
          'Family username',
        ),
      ).toHaveAttribute('aria-invalid', 'true')
    })

    it('checks the username format before looking it up', async () => {
      mockedGet.mockResolvedValue([])
      renderPage()

      const user =
        userEvent.setup()

      await user.type(
        await screen.findByLabelText(
          'Family username',
        ),
        'lim wei ling',
      )
      await user.click(
        screen.getByRole(
          'button',
          {
            name:
              'Send binding request',
          },
        ),
      )

      expect(
        screen.getByText(
          /A username has 3 to 64 letters/,
        ),
      ).toBeInTheDocument()
      expect(mockedCreate).not.toHaveBeenCalled()
    })

    it('shows 409 message for an existing binding', async () => {
      mockedGet.mockResolvedValue(
        [],
      )

      mockedCreate.mockRejectedValue(
        new ApiError(
          'Conflict',
          409,
        ),
      )

      renderPage()

      const user =
        userEvent.setup()

      await user.type(
        await screen.findByLabelText(
          'Family username',
        ),
        'family_test',
      )

      await user.click(
        screen.getByRole(
          'button',
          {
            name:
              'Send binding request',
          },
        ),
      )

      expect(
        await screen.findByRole(
          'alert',
        ),
      ).toHaveTextContent(
        'This family member is already linked or awaiting confirmation.',
      )
    })

    it('shows backend message for another API creation failure', async () => {
      mockedGet.mockResolvedValue(
        [],
      )

      mockedCreate.mockRejectedValue(
        new ApiError(
          'Request rejected',
          400,
        ),
      )

      renderPage()

      const user =
        userEvent.setup()

      await user.type(
        await screen.findByLabelText(
          'Family username',
        ),
        'family_test',
      )

      await user.click(
        screen.getByRole(
          'button',
          {
            name:
              'Send binding request',
          },
        ),
      )

      expect(
        await screen.findByRole(
          'alert',
        ),
      ).toHaveTextContent(
        'Request rejected',
      )
    })

    it('shows generic message for a non-API creation failure', async () => {
      mockedGet.mockResolvedValue(
        [],
      )

      mockedCreate.mockRejectedValue(
        new TypeError(
          'Network failure',
        ),
      )

      renderPage()

      const user =
        userEvent.setup()

      await user.type(
        await screen.findByLabelText(
          'Family username',
        ),
        'family_test',
      )

      await user.click(
        screen.getByRole(
          'button',
          {
            name:
              'Send binding request',
          },
        ),
      )

      expect(
        await screen.findByRole(
          'alert',
        ),
      ).toHaveTextContent(
        'Unable to create the family binding.',
      )
    })

    it('revokes an existing family binding', async () => {
      mockedGet.mockResolvedValue(
        [pendingBinding],
      )

      mockedRevoke.mockResolvedValue({
        ...pendingBinding,
        status:
          'REVOKED',
      })

      renderPage()

      const user =
        userEvent.setup()

      await user.click(
        await screen.findByRole(
          'button',
          {
            name:
              'Remove family member',
          },
        ),
      )

      await waitFor(() => {
        expect(
          mockedRevoke,
        ).toHaveBeenCalledWith(
          10,
        )
      })

      expect(
        await screen.findByText(
          'Family binding removed.',
        ),
      ).toBeInTheDocument()

      /*
       * REVOKED bindings are deliberately hidden from the Elder page.
       */
      expect(
        screen.queryByRole(
          'heading',
          {
            name:
              'Family Test',
          },
        ),
      ).not.toBeInTheDocument()
    })

    it('shows backend message when revoke fails with an API error', async () => {
      mockedGet.mockResolvedValue(
        [pendingBinding],
      )

      mockedRevoke.mockRejectedValue(
        new ApiError(
          'Binding cannot be removed',
          409,
        ),
      )

      renderPage()

      const user =
        userEvent.setup()

      await user.click(
        await screen.findByRole(
          'button',
          {
            name:
              'Remove family member',
          },
        ),
      )

      expect(
        await screen.findByRole(
          'alert',
        ),
      ).toHaveTextContent(
        'Binding cannot be removed',
      )

      expect(
        screen.getByRole(
          'heading',
          {
            name:
              'Family Test',
          },
        ),
      ).toBeInTheDocument()
    })

    it('shows generic message when revoke fails outside the API client', async () => {
      mockedGet.mockResolvedValue(
        [pendingBinding],
      )

      mockedRevoke.mockRejectedValue(
        new TypeError(
          'Network failure',
        ),
      )

      renderPage()

      const user =
        userEvent.setup()

      await user.click(
        await screen.findByRole(
          'button',
          {
            name:
              'Remove family member',
          },
        ),
      )

      expect(
        await screen.findByRole(
          'alert',
        ),
      ).toHaveTextContent(
        'Unable to remove the family binding.',
      )
    })

    it('shows load failure without crashing the binding form', async () => {
      mockedGet.mockRejectedValue(
        new Error(
          'Network failure',
        ),
      )

      renderPage()

      expect(
        await screen.findByRole(
          'alert',
        ),
      ).toHaveTextContent(
        'Unable to load family members.',
      )

      expect(
        screen.getByRole(
          'heading',
          {
            name:
              'Bind another family member',
          },
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByLabelText(
          'Family username',
        ),
      ).toBeInTheDocument()
    })

    it('renders active and read-only bindings correctly', async () => {
      mockedGet.mockResolvedValue([
        {
          ...pendingBinding,
          relationship:
            'DAUGHTER',
          primaryContact:
            false,
          accessScope:
            'READ_ONLY',
          status:
            'ACTIVE',
        },
      ])

      renderPage()

      expect(
        await screen.findByText(
          'Daughter',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          /Access: Read only/,
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          /Status: Active/,
        ),
      ).toBeInTheDocument()
    })
  },
)