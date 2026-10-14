package sg.nus.carelink.openplatform.controller.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Times as the Open API shows them: Singapore wall-clock time with its offset, to the second. */
public final class OpenTimes {

	private static final ZoneOffset SINGAPORE = ZoneOffset.ofHours(8);

	private OpenTimes() {
	}

	public static OffsetDateTime of(Instant instant) {
		return instant == null ? null : instant.truncatedTo(ChronoUnit.SECONDS).atOffset(SINGAPORE);
	}
}
