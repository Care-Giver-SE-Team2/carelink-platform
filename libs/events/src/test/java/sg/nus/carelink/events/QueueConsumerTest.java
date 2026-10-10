package sg.nus.carelink.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

class QueueConsumerTest {

	@Test
	void twoHandlersForOneTypeAreRefusedAtStartUp() {
		EventsProperties properties = new EventsProperties("visit", null, "http://queue", null, "ap-southeast-1",
				Duration.ofSeconds(1), 50, Duration.ofSeconds(10));

		assertThatThrownBy(() -> new QueueConsumer(mock(SqsClient.class), mock(JdbcTemplate.class),
				mock(TransactionTemplate.class), JsonMapper.builder().build(), Clock.systemUTC(), "visit", properties,
				List.of(new Named("VisitMissed"), new Named("VisitMissed"))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Two handlers for VisitMissed");
	}

	private record Named(String type) implements EventHandler<Object> {

		@Override
		public Class<Object> payloadType() {
			return Object.class;
		}

		@Override
		public void handle(Object payload, EventMetadata metadata) {
		}

	}

}
