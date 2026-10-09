package sg.nus.carelink.careplan.domain.model;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The single place that knows how {@code CarePlanNode.scheduleDays} is encoded as a string
 * ("DAILY", or a comma-joined list of 3-letter day codes like "MON,WED,FRI"). CarePlanService
 * encodes visits into this format when a plan is published; CarePlanLookupService decodes it
 * back into DayOfWeek values to compute the next visit date. Keeping both directions here
 * means the two can never drift out of sync with each other.
 */
public final class ScheduleDays {

	private static final String DAILY = "DAILY";
	private static final Set<DayOfWeek> ALL_DAYS = EnumSet.allOf(DayOfWeek.class);

	private ScheduleDays() {
	}

	/** All seven days encode as "DAILY"; anything else as a comma-joined list of 3-letter codes. */
	public static String encode(Set<DayOfWeek> days) {
		if (days.size() == 7) {
			return DAILY;
		}
		return days.stream()
				.sorted()
				.map(ScheduleDays::codeOf)
				.collect(Collectors.joining(","));
	}

	/** The inverse of {@link #encode}. Blank/null decodes to no days. */
	public static Set<DayOfWeek> decode(String scheduleDays) {
		if (scheduleDays == null || scheduleDays.isBlank()) {
			return Set.of();
		}
		if (DAILY.equals(scheduleDays)) {
			return ALL_DAYS;
		}
		Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
		for (String code : scheduleDays.split(",")) {
			days.add(dayOf(code));
		}
		return days;
	}

	/**
	 * Parses a day label case-insensitively from just its first 3 letters, so it accepts both
	 * the stored codes ("MON") and the day labels visit inputs arrive with ("Mon", "Monday").
	 */
	public static DayOfWeek dayOf(String label) {
		String trimmed = label.strip();
		String code = trimmed.substring(0, Math.min(3, trimmed.length())).toUpperCase();
		return switch (code) {
			case "MON" -> DayOfWeek.MONDAY;
			case "TUE" -> DayOfWeek.TUESDAY;
			case "WED" -> DayOfWeek.WEDNESDAY;
			case "THU" -> DayOfWeek.THURSDAY;
			case "FRI" -> DayOfWeek.FRIDAY;
			case "SAT" -> DayOfWeek.SATURDAY;
			case "SUN" -> DayOfWeek.SUNDAY;
			default -> throw new IllegalArgumentException("Unknown schedule day: " + label);
		};
	}

	private static String codeOf(DayOfWeek day) {
		return switch (day) {
			case MONDAY -> "MON";
			case TUESDAY -> "TUE";
			case WEDNESDAY -> "WED";
			case THURSDAY -> "THU";
			case FRIDAY -> "FRI";
			case SATURDAY -> "SAT";
			case SUNDAY -> "SUN";
		};
	}
}
