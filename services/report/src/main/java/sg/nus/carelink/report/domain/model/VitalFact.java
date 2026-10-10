package sg.nus.carelink.report.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One vital-sign reading taken on a visit in the period.
 *
 * @param metric     as the caregiver's form recorded it: systolic, diastolic, pulse, temperature
 * @param unit       may be null; a range takes the unit of its first reading
 * @param outOfRange flagged at entry time (story C6), not recomputed here
 */
public record VitalFact(
		Long visitId,
		String metric,
		BigDecimal value,
		String unit,
		boolean outOfRange,
		LocalDateTime recordedAt) {

	public VitalFact {
		Objects.requireNonNull(metric, "metric");
		Objects.requireNonNull(value, "value");
		Objects.requireNonNull(recordedAt, "recordedAt");
	}
}
