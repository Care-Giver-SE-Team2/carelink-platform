package sg.nus.carelink.eventtypes;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Business times as events carry them: Singapore wall-clock time with its offset, to the second,
 * {@code 2026-10-12T09:00:00+08:00}. The services keep these times as {@link LocalDateTime} on
 * Singapore's clock; these two methods convert at the edge.
 */
public final class SingaporeTime {

	public static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

	private SingaporeTime() {
	}

	/**
	 * A Singapore wall-clock time, with its offset, for an event. It is rounded to the second the
	 * way the database rounds it into a {@code DATETIME} column, so the event says what the
	 * database keeps: 09:35:00.6 is 09:35:01. Null stays null.
	 */
	public static OffsetDateTime of(LocalDateTime wallClock) {
		if (wallClock == null) {
			return null;
		}
		return wallClock.plusNanos(500_000_000).truncatedTo(ChronoUnit.SECONDS).atZone(ZONE).toOffsetDateTime();
	}

	/**
	 * Back to Singapore wall-clock time, whatever offset the time arrived with: a JSON reader may
	 * hand an event's time back in UTC. Null stays null.
	 */
	public static LocalDateTime local(OffsetDateTime time) {
		return time == null ? null : time.atZoneSameInstant(ZONE).toLocalDateTime();
	}

}
