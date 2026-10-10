package sg.nus.carelink.events;

import java.time.Instant;

/**
 * What every event carries besides its payload.
 *
 * @param id the event's id, the same for every delivery of it
 * @param type the event's name
 * @param source the service that published it
 * @param occurredAt when it was published, that is, when its transaction wrote it
 */
public record EventMetadata(String id, String type, String source, Instant occurredAt) {
}
