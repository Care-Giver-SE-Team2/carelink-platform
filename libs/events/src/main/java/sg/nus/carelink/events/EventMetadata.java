package sg.nus.carelink.events;

import java.time.Instant;

/**
 * What every event carries besides its payload.
 *
 * @param id the event's id, the same for every delivery of it
 * @param type the event's name
 * @param source the service that published it
 * @param occurredAt when it was published, that is, when its transaction wrote it
 * @param sequence the number of its row in the publisher's outbox: it increases within a source, so of
 *     two changes to one record the later one has the larger number. Null for a message that did not
 *     come through an outbox, such as a scheduler's
 */
public record EventMetadata(String id, String type, String source, Instant occurredAt, Long sequence) {
}
