package sg.nus.carelink.incident.domain.service;

import java.util.List;

import sg.nus.carelink.incident.domain.model.EscalationLevel;
import sg.nus.carelink.incident.domain.model.EscalationTier;

/**
 * One link of the escalation chain: it either takes the incident or passes it on.
 *
 * <p>This interface is the polymorphic point of the Chain of Responsibility used for
 * UC-MG05 and UC-SYS02. The design problem it answers is that the sender of an incident
 * cannot know who will handle it: the number of levels and their order differ from one
 * incident to the next, because they are derived from severity, the hour of the day and who
 * is on shift. A fixed ladder of {@code if} branches over roles would have to be edited
 * every time the institution changes its escalation policy, and the branches would sit in
 * whichever class happened to raise the incident.
 *
 * <p>With the chain, each tier is a small class that answers one question — "can I take
 * this?" — and knows only its successor. Adding a tier is a new class and one line in
 * {@link EscalationChainBuilder}; no existing handler changes.
 *
 * <p>Implementations are immutable and wired through their constructors, so a chain can be
 * assembled in a test with two lines and no framework.
 */
public interface ResponderHandler {

	/** Which tier this link represents. */
	EscalationTier tier();

	/**
	 * Take the incident, or hand the request to the next link.
	 *
	 * @return an outcome that either names a responder or reports the chain exhausted;
	 *         never {@code null}
	 */
	EscalationOutcome handle(EscalationRequest request);

	/**
	 * The chain as a read-only plan, without changing anything.
	 *
	 * <p>Backs {@code GET /api/incidents/{id}/escalation-chain}. It answers "who would take
	 * this, in what order, with what deadline" so a manager can see the route before it is
	 * walked.
	 */
	List<EscalationLevel> survey(EscalationRequest request);
}
