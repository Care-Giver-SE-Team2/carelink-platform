import { api } from '../../shared/api/client'
import { initialiseCsrf } from '../auth/api'
import type { FamilyIncidentAcknowledgement, FamilyIncidentDetail } from './familyTypes'

/** Reads the dedicated family projection; the server determines the elder and recipient.
 * @author Wang Zhili
 */
export function getFamilyIncident(id: string, signal: AbortSignal): Promise<FamilyIncidentDetail> {
  return api<FamilyIncidentDetail>(`/family/incidents/${encodeURIComponent(id)}`, { signal })
}

/** Records viewing independently of notification read and explicit awareness.
 * @author Wang Zhili
 */
export async function viewFamilyIncident(id: string, signal: AbortSignal): Promise<FamilyIncidentAcknowledgement> {
  await initialiseCsrf(signal)
  signal.throwIfAborted()
  return api<FamilyIncidentAcknowledgement>(`/incidents/${encodeURIComponent(id)}/view`, { method: 'POST', signal })
}

/** Saves the first explicit awareness receipt; identity and time are always server-owned.
 * @author Wang Zhili
 */
export async function acknowledgeFamilyIncident(id: string, responseNote: string, signal: AbortSignal): Promise<FamilyIncidentAcknowledgement> {
  await initialiseCsrf(signal)
  signal.throwIfAborted()
  return api<FamilyIncidentAcknowledgement>(`/incidents/${encodeURIComponent(id)}/acknowledge`, {
    method: 'POST', body: JSON.stringify({ responseNote: responseNote || null }), signal,
  })
}
