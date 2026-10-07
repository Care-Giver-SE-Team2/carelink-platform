package sg.nus.carelink.incident.domain.repository;

import sg.nus.carelink.incident.domain.model.Incident;

/**
 * Telling people an incident has happened.
 *
 * <p>Separate from the escalation chain on purpose, and the distinction is the whole point:
 * <strong>broadcasting is about who finds out, the chain is about who is answerable.</strong>
 * Everyone who could act hears about an incident in the same second. Exactly one of them is
 * then named as responsible, with a deadline, because three people who all received the same
 * alert will each assume one of the other two went.
 *
 * <p>So the chain never delays a single message. It decides accountability afterwards, and
 * an escalation does not broadcast again - the family already knows.
 */
public interface IncidentAlert {

	/**
	 * Tells every manager, the caregiver on the elder's most recent visit, and the family
	 * members bound to that elder, all at once.
	 *
	 * @return how many people were told
	 */
	int broadcastRaised(Incident incident);

	/**
	 * Tells the new responder that an incident is now theirs, and the previous one that it
	 * has moved on. Nobody else: this is a change of ownership, not news.
	 */
	void handedOver(Incident incident, Long fromUserId, Long toUserId);

	/**
	 * Tells the family that nobody took the incident on. The only escalation step that
	 * reaches outside the institution, because it is the one the family has to act on.
	 */
	void chainExhausted(Incident incident);
}
