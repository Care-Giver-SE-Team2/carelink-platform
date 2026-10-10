package sg.nus.carelink.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * An event's body reads back the same, delivered raw or wrapped in SNS's notification JSON, and a
 * message that did not come through an outbox reads with no sequence.
 */
class EnvelopeTest {

	private final JsonMapper json = JsonMapper.builder().build();

	private final Envelope event = new Envelope("4f7e2c1a-0000-4000-8000-000000000001", "VisitMissed", "visit",
			Instant.parse("2026-10-10T01:15:00Z"), 41L, json.valueToTree(Map.of("visitId", 101, "elderId", 3)));

	@Test
	void aRawDeliveryReadsBackTheSame() {
		assertThat(Envelope.read(event.toJson(json), json)).isEqualTo(event);
	}

	@Test
	void anSnsNotificationIsUnwrapped() {
		String notification = json.writeValueAsString(Map.of(
				"Type", "Notification",
				"MessageId", "a6c5d6a4-1111-4000-8000-000000000002",
				"TopicArn", "arn:aws:sns:ap-southeast-1:000000000000:carelink-events",
				"Message", event.toJson(json)));

		assertThat(Envelope.read(notification, json)).isEqualTo(event);
	}

	@Test
	void aMessageThatDidNotComeThroughAnOutboxHasNoSequence() {
		String scheduled = "{\"id\":\"5c2a7e10-0000-4000-8000-000000000003\",\"type\":\"ReportRequested\","
				+ "\"source\":\"scheduler\",\"occurredAt\":\"2026-10-11T18:00:00Z\",\"payload\":{}}";

		assertThat(Envelope.read(scheduled, json).metadata().sequence()).isNull();
	}

	@Test
	void aBodyThatIsNotAnEventIsRefused() {
		assertThatThrownBy(() -> Envelope.read("{\"hello\":\"world\"}", json))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Envelope.read("{\"id\":\"1\",\"type\":\"VisitMissed\",\"payload\":{}}", json))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("source");
	}

	@Test
	void theMetadataIsWhatTheHandlerSees() {
		assertThat(event.metadata()).isEqualTo(new EventMetadata(event.id(), "VisitMissed", "visit",
				Instant.parse("2026-10-10T01:15:00Z"), 41L));
	}

}
