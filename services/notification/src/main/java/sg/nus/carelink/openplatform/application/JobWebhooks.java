package sg.nus.carelink.openplatform.application;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.eventtypes.ProviderJobUpdated;
import sg.nus.carelink.eventtypes.SingaporeTime;
import sg.nus.carelink.openplatform.domain.model.Delivery;
import sg.nus.carelink.openplatform.domain.model.WebhookEvent;

/**
 * What report's {@code ProviderJobUpdated} becomes for the provider (docs/api/webhooks.md, section 2):
 * {@code job.dispatched} for a new job, {@code job.cancelled} for one called off. The provider's own
 * answers (accepted, declined, completed) send nothing. The body's {@code data.job} is the job API's
 * {@code JobSummary} at the change.
 */
@Service
public class JobWebhooks {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

	private final WebhookDeliveryService deliveries;

	public JobWebhooks(WebhookDeliveryService deliveries) {
		this.deliveries = deliveries;
	}

	/**
	 * @param eventId the event's id: the same event handled twice makes one delivery
	 * @return the delivery made; empty when the change sends nothing, or the provider does not receive it
	 */
	@Transactional
	public Optional<Delivery> jobChanged(ProviderJobUpdated job, String eventId) {
		WebhookEvent event = switch (job.status()) {
			case "NEW" -> WebhookEvent.JOB_DISPATCHED;
			case "CANCELLED" -> WebhookEvent.JOB_CANCELLED;
			default -> null;
		};
		if (event == null) {
			return Optional.empty();
		}
		String jobId = "JOB-" + job.jobId();
		return deliveries.enqueue(job.providerId(), event, jobId, Map.of("job", summary(jobId, job)), eventId);
	}

	static Map<String, Object> summary(String jobId, ProviderJobUpdated job) {
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("id", jobId);
		summary.put("version", job.version());
		summary.put("status", job.status());
		summary.put("serviceName", job.serviceName());
		summary.put("district", job.district());
		summary.put("areaName", job.areaName());
		summary.put("scheduledStart", time(job.scheduledStart()));
		summary.put("durationMinutes", job.durationMinutes());
		summary.put("respondBy", time(job.respondBy()));
		summary.put("receivedAt", time(job.receivedAt()));
		summary.put("cancelReason", job.cancelReason());
		return summary;
	}

	/** On Singapore's offset, whatever offset the event was read back with. */
	private static String time(OffsetDateTime at) {
		return at == null ? null
				: TIME.format(at.atZoneSameInstant(SingaporeTime.ZONE));
	}
}
