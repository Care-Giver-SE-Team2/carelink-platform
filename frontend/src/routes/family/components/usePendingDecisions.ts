import { useFamilyChanges } from '../../../features/absences/useAbsenceQueries'
import { useFamilyElders } from '../../../features/family-account/useFamilyAccount'
import { useSpotChecks } from '../../../features/spot-checks/useSpotCheckQueries'
import { useFamilyValueAddedRequests } from '../../../features/value-added-services/useValueAddedServiceQueries'
import { useSelectedElder } from './selectedElder'

/**
 * Everything waiting on the family's answer, read from the same queries the answering pages use so
 * the counts follow their writes: visit changes and spot checks across every linked elder, and extra
 * service requests for the elder being followed (the requests page shows one elder at a time).
 * A list that fails to load counts as nothing waiting; its own page shows the error.
 */
export function usePendingDecisions() {
  const elders = useFamilyElders()
  const { elderId } = useSelectedElder()
  const followed = elderId ?? elders.data?.[0]?.id ?? null
  const changes = useFamilyChanges()
  const spotChecks = useSpotChecks()
  const requests = useFamilyValueAddedRequests(followed)

  const waitingChanges = (changes.data ?? []).filter((change) => change.status === 'AWAITING_FAMILY')
  const waitingSpotChecks = (spotChecks.data ?? []).filter((check) => check.stage === 'AWAITING_FAMILY')
  const waitingRequests = (requests.data ?? []).filter((request) => request.status === 'PENDING_APPROVAL')

  return {
    changes: waitingChanges,
    spotChecks: waitingSpotChecks,
    requests: waitingRequests,
    total: waitingChanges.length + waitingSpotChecks.length + waitingRequests.length,
  }
}
