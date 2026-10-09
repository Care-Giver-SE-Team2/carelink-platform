package sg.nus.carelink.incident.domain.repository;

import sg.nus.carelink.incident.domain.model.Incident;

/**
 * Staff alerts for incident routing and responder handovers.
 *
 * <p>Separate from the escalation chain on purpose, and the distinction is the whole point:
 * <strong>broadcasting is about who finds out, the chain is about who is answerable.</strong>
 * Staff who could act hear about an incident before routing. Exactly one of them is
 * then named as responsible, with a deadline, because three people who all received the same
 * alert will each assume one of the other two went.
 *
 * <p>Family alerts use the separate after-commit IncidentFamilyEvents Observer contract.
 * Ordinary responder handovers do not broadcast another family event.
 */
public interface IncidentAlert {

	/**
	 * Tells every manager and the caregiver on the elder's most recent visit.
	 *
	 * @return how many people were told
	 */
	int broadcastRaised(Incident incident);

	/**
	 * Tells the new responder that an incident is now theirs, and the previous one that it
	 * has moved on. Nobody else: this is a change of ownership, not news.
	 */
	void handedOver(Incident incident, Long fromUserId, Long toUserId);

}
