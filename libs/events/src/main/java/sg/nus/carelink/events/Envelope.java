package sg.nus.carelink.events;

import java.time.Instant;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * An event as it travels: its metadata and its payload in one JSON body. The SNS message body is
 * this JSON, so a receiver needs nothing else, whether its subscription delivers the message raw
 * or wrapped in SNS's own notification JSON.
 */
record Envelope(String id, String type, String source, Instant occurredAt, JsonNode payload) {

	EventMetadata metadata() {
		return new EventMetadata(id, type, source, occurredAt);
	}

	String toJson(JsonMapper json) {
		ObjectNode body = json.createObjectNode();
		body.put("id", id);
		body.put("type", type);
		body.put("source", source);
		body.put("occurredAt", occurredAt.toString());
		body.set("payload", payload);
		return json.writeValueAsString(body);
	}

	/**
	 * Reads a message body: the envelope itself (raw delivery) or SNS's notification JSON with the
	 * envelope in its {@code Message} field.
	 *
	 * @throws IllegalArgumentException when the body is not an event
	 */
	static Envelope read(String body, JsonMapper json) {
		JsonNode node = json.readTree(body);
		if (node.has("Message") && "Notification".equals(text(node, "Type"))) {
			node = json.readTree(text(node, "Message"));
		}
		JsonNode payload = node.get("payload");
		if (payload == null) {
			throw new IllegalArgumentException("Not an event: no payload");
		}
		return new Envelope(required(node, "id"), required(node, "type"), required(node, "source"),
				Instant.parse(required(node, "occurredAt")), payload);
	}

	private static String required(JsonNode node, String field) {
		String value = text(node, field);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Not an event: no " + field);
		}
		return value;
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asString();
	}

}
