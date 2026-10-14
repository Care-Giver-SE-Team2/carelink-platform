package sg.nus.carelink.openplatform.infrastructure.schedule;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.openplatform.application.WebhookDeliveryService;

/** The trigger attempts what is due, and a failed run waits for the next instead of stopping the scheduler. */
class WebhookDeliverySchedulerTest {

	private final WebhookDeliveryService service = mock(WebhookDeliveryService.class);

	@Test
	void eachRunAttemptsTheDeliveriesThatAreDue() {
		when(service.attemptDue()).thenReturn(3);

		new WebhookDeliveryScheduler(service).attemptDue();

		verify(service).attemptDue();
	}

	@Test
	void aFailedRunDoesNotStopTheNext() {
		when(service.attemptDue()).thenThrow(new IllegalStateException("database down")).thenReturn(0);
		WebhookDeliveryScheduler scheduler = new WebhookDeliveryScheduler(service);

		assertThatCode(scheduler::attemptDue).doesNotThrowAnyException();
		assertThatCode(scheduler::attemptDue).doesNotThrowAnyException();
	}
}
