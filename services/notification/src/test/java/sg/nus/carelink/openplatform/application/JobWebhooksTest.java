package sg.nus.carelink.openplatform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import sg.nus.carelink.eventtypes.ProviderJobUpdated;
import sg.nus.carelink.openplatform.domain.model.WebhookEvent;

/** Which of report's job changes reach the provider, and as what (docs/api/webhooks.md, section 2). */
class JobWebhooksTest {

	private static final OffsetDateTime DISPATCHED = OffsetDateTime.parse("2026-10-16T09:02:11+08:00");

	private final WebhookDeliveryService deliveries = mock(WebhookDeliveryService.class);
	private final JobWebhooks webhooks = new JobWebhooks(deliveries);

	private static ProviderJobUpdated job(String status, OffsetDateTime respondBy, String cancelReason) {
		return new ProviderJobUpdated(24081L, 509L, 812L, status, "Hospital escort", "19", "Hougang",
				OffsetDateTime.parse("2026-10-17T10:00:00+08:00"), 180, respondBy, DISPATCHED, cancelReason, 0,
				DISPATCHED);
	}

	@Test
	@SuppressWarnings("unchecked")
	void aNewJobIsSentAsJobDispatchedWithItsSummaryOnSingaporesOffset() {
		// Read back from the event in UTC, as a JSON reader may hand it
		ProviderJobUpdated job = job("NEW", OffsetDateTime.parse("2026-10-17T01:02:11Z"), null);

		webhooks.jobChanged(job, "evt-1");

		ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
		verify(deliveries).enqueue(eq(509L), eq(WebhookEvent.JOB_DISPATCHED), eq("JOB-24081"), data.capture(),
				eq("evt-1"));
		Map<String, Object> summary = (Map<String, Object>) ((Map<String, Object>) data.getValue()).get("job");
		assertThat(summary).containsOnlyKeys("id", "version", "status", "serviceName", "district", "areaName",
				"scheduledStart", "durationMinutes", "respondBy", "receivedAt", "cancelReason");
		assertThat(summary).containsEntry("id", "JOB-24081")
				.containsEntry("respondBy", "2026-10-17T09:02:11+08:00")
				.containsEntry("receivedAt", "2026-10-16T09:02:11+08:00")
				.containsEntry("cancelReason", null);
	}

	@Test
	void aCancelledJobIsSentAsJobCancelled() {
		webhooks.jobChanged(job("CANCELLED", null, "The elder withdrew it."), "evt-2");

		verify(deliveries).enqueue(eq(509L), eq(WebhookEvent.JOB_CANCELLED), eq("JOB-24081"), any(), eq("evt-2"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "ACCEPTED", "DECLINED", "COMPLETED" })
	void theProvidersOwnAnswersSendNothing(String status) {
		assertThat(webhooks.jobChanged(job(status, null, null), "evt-3")).isEmpty();

		verifyNoInteractions(deliveries);
	}
}
