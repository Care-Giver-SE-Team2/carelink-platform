export type RenewalDecision = 'RENEW_CURRENT' | 'REQUEST_CHANGE' | 'CANCEL_SERVICE'

export interface ReviewableCaregiver {
  id: number
  fullName: string
}

export interface CaregiverReview {
  id: number
  familyMemberId: number
  elderId: number
  caregiverId: number
  caregiverName: string
  periodStart: string
  periodEnd: string
  overallRating: number
  punctualityScore: number | null
  careQualityScore: number | null
  feedbackNotes: string | null
  renewalDecision: RenewalDecision
  createdAt: string
}

export interface CaregiverReviewCreate {
  elderId: number
  caregiverId: number
  periodStart: string
  periodEnd: string
  overallRating: number
  punctualityScore: number | null
  careQualityScore: number | null
  feedbackNotes: string | null
  renewalDecision: RenewalDecision
}
