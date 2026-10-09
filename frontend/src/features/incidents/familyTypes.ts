import type { IncidentCategory, IncidentSeverity, IncidentStatus } from './types'

/** Family-only projection; timestamps include offsets, and no handling identities are exposed.
 * @author Wang Zhili
 */
export interface FamilyIncidentAcknowledgement {
  id: number | null
  incidentId: number
  familyMemberId: number
  viewedAt: string | null
  acknowledgedAt: string | null
  responseNote: string | null
}

export interface FamilyIncidentDetail {
  id: number
  elderId: number
  visitId: number | null
  source: 'CAREGIVER' | 'ELDER_SOS' | 'ELDER_SERVICE_DISPUTE' | 'SYSTEM_MISSED_CHECKIN'
  category: IncidentCategory
  severity: IncidentSeverity
  status: IncidentStatus
  description: string | null
  reportedAt: string
  resolvedAt: string | null
  acknowledgeBy: string | null
  acknowledgement: FamilyIncidentAcknowledgement
}
