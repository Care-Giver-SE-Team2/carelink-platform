package sg.nus.carelink.openplatform.domain.model;

import java.time.Duration;
import java.util.List;

/**
 * When CareLink tries a webhook again (docs/api/webhooks.md, section 7): at once, then after 1, 5,
 * 30, 120 and 360 minutes, waiting longer after each failure. Six attempts over about 8.6 hours;
 * then CareLink gives up. The portal's Docs page and mock show the same numbers
 * (frontend/src/features/provider-platform/webhooks.ts).
 */
public final class RetrySchedule {

	/** How long to wait after attempt n fails, for n from 1. */
	public static final List<Duration> DELAYS = List.of(
			Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
			Duration.ofHours(2), Duration.ofHours(6));

	/** The first try and every retry. */
	public static final int MAX_ATTEMPTS = DELAYS.size() + 1;

	private RetrySchedule() {
	}

	/** How long to wait after attempt {@code attemptNo} (from 1) fails, before the next. */
	public static Duration delayAfter(int attemptNo) {
		if (attemptNo < 1) {
			throw new IllegalArgumentException("Attempts count from 1");
		}
		// A resent delivery's attempts come after the schedule's; each waits as long as the last.
		return DELAYS.get(Math.min(attemptNo, DELAYS.size()) - 1);
	}
}
