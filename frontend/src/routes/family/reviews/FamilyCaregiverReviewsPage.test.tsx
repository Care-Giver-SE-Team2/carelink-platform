import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createCaregiverReview,
  fetchCaregiverReviews,
  fetchReviewableCaregivers,
} from '../../../features/caregiver-reviews/api'
import { useFamilyElders } from '../../../features/family-account/useFamilyAccount'
import { useSelectedElder } from '../components/selectedElder'
import { FamilyCaregiverReviewsPage } from './FamilyCaregiverReviewsPage'

vi.mock('../../../features/caregiver-reviews/api', () => ({
  createCaregiverReview: vi.fn(),
  fetchCaregiverReviews: vi.fn(),
  fetchReviewableCaregivers: vi.fn(),
}))
vi.mock('../../../features/family-account/useFamilyAccount', () => ({ useFamilyElders: vi.fn() }))
vi.mock('../components/selectedElder', () => ({ useSelectedElder: vi.fn() }))

const mockedCreate = vi.mocked(createCaregiverReview)
const mockedReviews = vi.mocked(fetchCaregiverReviews)
const mockedCaregivers = vi.mocked(fetchReviewableCaregivers)
const mockedElders = vi.mocked(useFamilyElders)
const mockedSelected = vi.mocked(useSelectedElder)

const caregiver = { id: 31, fullName: 'John Tan' }
const existing = {
  id: 51, familyMemberId: 11, elderId: 21, caregiverId: 31,
  caregiverName: 'John Tan', periodStart: '2026-08-01', periodEnd: '2026-08-31',
  overallRating: 4, punctualityScore: 4, careQualityScore: 5,
  feedbackNotes: 'Good care', renewalDecision: 'RENEW_CURRENT' as const,
  createdAt: '2026-09-01T10:00:00',
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function prepare() {
  mockedSelected.mockReturnValue({ elderId: 21, setElderId: vi.fn() })
  mockedElders.mockReturnValue({ data: [{ id: 21, fullName: 'Test Elder' }] } as ReturnType<typeof useFamilyElders>)
  mockedCaregivers.mockResolvedValue([caregiver])
  mockedReviews.mockResolvedValue([])
}

describe('FM09 caregiver review and renewal decision', () => {
  it('loads reviewable caregivers and existing review history', async () => {
    prepare()
    mockedReviews.mockResolvedValue([existing])

    render(<FamilyCaregiverReviewsPage />)

    expect(await screen.findByRole('option', { name: 'John Tan' })).toBeInTheDocument()
    expect(screen.getByText('Good care')).toBeInTheDocument()
    expect(mockedCaregivers).toHaveBeenCalledWith(21)
    expect(mockedReviews).toHaveBeenCalledWith(21)
  })

  it('requires a review period before submission', async () => {
    prepare()
    render(<FamilyCaregiverReviewsPage />)
    await screen.findByRole('option', { name: 'John Tan' })

    await userEvent.setup().click(screen.getByRole('button', { name: 'Submit review' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Choose a caregiver and review period.')
    expect(mockedCreate).not.toHaveBeenCalled()
  })

  it('submits ratings feedback and renewal decision then adds the review to history', async () => {
    prepare()
    mockedCreate.mockResolvedValue({
      id: 52, familyMemberId: 11, elderId: 21, caregiverId: 31,
      caregiverName: 'John Tan', periodStart: '2026-09-01', periodEnd: '2026-09-30',
      overallRating: 5, punctualityScore: 4, careQualityScore: 5,
      feedbackNotes: 'Very patient.', renewalDecision: 'REQUEST_CHANGE',
      createdAt: '2026-10-07T20:00:00',
    })
    render(<FamilyCaregiverReviewsPage />)
    const user = userEvent.setup()
    await screen.findByRole('option', { name: 'John Tan' })

    await user.type(screen.getByLabelText('Period start'), '2026-09-01')
    await user.type(screen.getByLabelText('Period end'), '2026-09-30')
    await user.selectOptions(screen.getByLabelText('Punctuality'), '4')
    await user.type(screen.getByLabelText('Feedback'), '  Very patient.  ')
    await user.click(screen.getByRole('radio', { name: 'Request a different caregiver' }))
    await user.click(screen.getByRole('button', { name: 'Submit review' }))

    await waitFor(() => expect(mockedCreate).toHaveBeenCalledWith({
      elderId: 21,
      caregiverId: 31,
      periodStart: '2026-09-01',
      periodEnd: '2026-09-30',
      overallRating: 5,
      punctualityScore: 4,
      careQualityScore: 5,
      feedbackNotes: 'Very patient.',
      renewalDecision: 'REQUEST_CHANGE',
    }))
    expect(await screen.findByRole('status')).toHaveTextContent('Review submitted successfully.')
    expect(screen.getByText('Very patient.')).toBeInTheDocument()
    expect(screen.getByText('REQUEST_CHANGE')).toBeInTheDocument()
  })

  it('shows the empty caregiver state', async () => {
    prepare()
    mockedCaregivers.mockResolvedValue([])
    render(<FamilyCaregiverReviewsPage />)

    expect(await screen.findByText('No caregiver with a visit relationship is available for review.')).toBeInTheDocument()
  })

  it('shows loading failure', async () => {
    prepare()
    mockedCaregivers.mockRejectedValue(new Error('network'))
    render(<FamilyCaregiverReviewsPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load caregiver reviews.')
  })
})
