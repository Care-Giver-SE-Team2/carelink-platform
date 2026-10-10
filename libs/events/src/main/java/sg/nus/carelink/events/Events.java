package sg.nus.carelink.events;

/**
 * Publishes an event to the other services. The event is written to this service's outbox in the
 * caller's transaction, so it is published when, and only when, that transaction commits: data and
 * event never disagree. A relay then sends it to the SNS topic, and each subscribed service gets it
 * from its own SQS queue (see {@link EventHandler}).
 *
 * <pre>{@code
 * @Transactional
 * public void markMissed(Long visitId) {
 *     visit.markMissed();
 *     events.publish("VisitMissed", new VisitMissed(visitId, visit.elderId()));
 * }
 * }</pre>
 */
public interface Events {

	/**
	 * Adds an event to the outbox.
	 *
	 * @param type the event's name, as the event catalogue has it, for example {@code VisitMissed}
	 * @param payload the event's content, written as JSON: ids and the fields the receivers need
	 * @throws org.springframework.transaction.IllegalTransactionStateException when no transaction
	 *     is active: an event belongs to the change that caused it
	 */
	void publish(String type, Object payload);

}
