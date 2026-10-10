package sg.nus.carelink.events;

/**
 * Handles one type of event arriving from this service's SQS queue. Declare it as a bean; the
 * library calls it once per event, even when SQS delivers a message twice.
 *
 * <p>{@link #handle} runs in a transaction together with the record that the event was handled.
 * If it throws, both roll back and SQS delivers the event again after the queue's visibility
 * timeout; after the queue's maximum number of receives, SQS moves it to the dead-letter queue.
 *
 * <pre>{@code
 * @Component
 * class VisitMissedHandler implements EventHandler<VisitMissed> {
 *     public String type() { return "VisitMissed"; }
 *     public Class<VisitMissed> payloadType() { return VisitMissed.class; }
 *     public void handle(VisitMissed event, EventMetadata metadata) { ... }
 * }
 * }</pre>
 *
 * @param <T> the payload, read from the event's JSON
 */
public interface EventHandler<T> {

	/** The event's name, for example {@code VisitMissed}. One handler per type in a service. */
	String type();

	/** The class the payload is read into. */
	Class<T> payloadType();

	void handle(T payload, EventMetadata metadata);

}
