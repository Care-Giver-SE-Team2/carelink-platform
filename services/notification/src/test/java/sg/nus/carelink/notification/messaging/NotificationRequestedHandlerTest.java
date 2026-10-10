package sg.nus.carelink.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import sg.nus.carelink.events.EventMetadata;
import sg.nus.carelink.eventtypes.NotificationRequested;
import sg.nus.carelink.notification.application.NotificationRequests;
import sg.nus.carelink.notification.domain.model.Notification;

/** The handler takes its event by name and keeps it as one message, at the time it was requested in Singapore. */
class NotificationRequestedHandlerTest {

	private final NotificationRequests requests = mock(NotificationRequests.class);

	private final NotificationRequestedHandler handler = new NotificationRequestedHandler(requests);

	@Test
	void itHandlesTheCataloguesEvent() {
		assertThat(handler.type()).isEqualTo("NotificationRequested");
		assertThat(handler.payloadType()).isEqualTo(NotificationRequested.class);
	}

	@Test
	void anEventBecomesOneMessageAtTheTimeItWasRequested() {
		NotificationRequested event = new NotificationRequested(7L, "INCIDENT_RAISED", "IN_APP", "Urgent care alert: HIGH",
				"A FALL incident has been reported.", "INCIDENT", 601L, 101L,
				OffsetDateTime.parse("2026-10-12T01:35:00Z"));

		handler.handle(event, new EventMetadata("4f7e2c1a-0000-4000-8000-000000000001", "NotificationRequested", "core",
				Instant.parse("2026-10-12T01:35:01Z"), 12L));

		verify(requests).keep(7L, "INCIDENT_RAISED", Notification.Channel.IN_APP, "Urgent care alert: HIGH",
				"A FALL incident has been reported.", "INCIDENT", 601L, LocalDateTime.of(2026, 10, 12, 9, 35));
	}

}
