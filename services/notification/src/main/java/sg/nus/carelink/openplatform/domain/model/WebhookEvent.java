package sg.nus.carelink.openplatform.domain.model;

import java.util.Arrays;

/**
 * The events CareLink sends a provider's webhook endpoint (docs/api/webhooks.md, section 2), by
 * the name they travel under. A provider subscribes to the job events; {@code ping} is sent
 * whenever it asks for a test, whatever it subscribed to.
 */
public enum WebhookEvent {

	JOB_DISPATCHED("job.dispatched"),
	JOB_CANCELLED("job.cancelled"),
	PING("ping");

	private final String wireName;

	WebhookEvent(String wireName) {
		this.wireName = wireName;
	}

	/** The name in the {@code X-CareLink-Event} header and the body's {@code type}. */
	public String wireName() {
		return wireName;
	}

	/** The event a wire name names. */
	public static WebhookEvent fromWireName(String name) {
		return Arrays.stream(values())
				.filter(event -> event.wireName.equals(name))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown webhook event: " + name));
	}
}
