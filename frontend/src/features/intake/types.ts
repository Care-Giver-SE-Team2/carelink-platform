export type IntakeStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED'
export type MobilityLevel = 'INDEPENDENT' | 'ASSISTIVE_CANE' | 'WHEELCHAIR_BEDBOUND'

export interface IntakeListQuery {
  page: number
  size: number
  status?: IntakeStatus
}

/**
 * Family projection returned by the intake API.
 * @author Wang Zhili
 */
export interface IntakeApplication {
  id: number
  applicantFamilyMemberId: number
  targetElderName: string
  targetElderAge: number | null
  targetAddress: string
  postalCode: string
  mobilityLevel: MobilityLevel
  preferredDialects: string | null
  careNeeds: string[]
  medicalNotes: string | null
  status: IntakeStatus
  reviewRemarks: string | null
  createdAt: string
  reviewedAt: string | null
  elderId: number | null
}

export interface IntakeApplicationPage {
  items: IntakeApplication[]
  page: number
  size: number
  totalElements: number
}
