package sg.nus.carelink.incident.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
* Producer handoff: call inside the transaction that saves the incident; observers run only after commit.
* Reuse the eventId and facts on replay. No family notification logic is needed at the call site.
*
* @author Wang Zhili
*/
public interface IncidentFamilyEvents {
	void raised(UUID eventId, Long incidentId, Long elderId, OffsetDateTime occurredAt);
	void unresolved(UUID eventId, Long incidentId, Long elderId, OffsetDateTime occurredAt);
}
