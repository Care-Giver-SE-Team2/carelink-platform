import { api } from '../../shared/api/client'
import type { CaregiverReview, CaregiverReviewCreate, ReviewableCaregiver } from './types'

export function fetchCaregiverReviews(elderId: number): Promise<CaregiverReview[]> {
  return api<CaregiverReview[]>(`/family/caregiver-reviews?elderId=${elderId}`)
}

export function fetchReviewableCaregivers(elderId: number): Promise<ReviewableCaregiver[]> {
  return api<ReviewableCaregiver[]>(`/family/caregiver-reviews/caregivers?elderId=${elderId}`)
}

export function createCaregiverReview(request: CaregiverReviewCreate): Promise<CaregiverReview> {
  return api<CaregiverReview>('/family/caregiver-reviews', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
