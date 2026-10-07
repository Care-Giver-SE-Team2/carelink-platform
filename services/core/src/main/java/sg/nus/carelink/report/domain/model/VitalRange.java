package sg.nus.carelink.report.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The lowest and highest reading of one metric over a period.
 *
 * <p>What a family is shown in place of the readings themselves. The contract's family rules
 * say "vital ranges rather than raw measurements": a relative can see that systolic pressure
 * stayed between 128 and 142 without being handed a column of numbers and left to decide
 * which one to worry about.
 *
 * @param unit     the unit of the metric's first reading that has one; readings are taken on one
 *                 form, so a metric does not change units part way through a week
 * @param readings how many readings the range was taken over
 */
public record VitalRange(String metric, BigDecimal lowest, BigDecimal highest, String unit, int readings) {

	public VitalRange {
		Objects.requireNonNull(metric, "metric");
		Objects.requireNonNull(lowest, "lowest");
		Objects.requireNonNull(highest, "highest");
	}

	/**
	 * One range per metric that has at least one reading, in the order the metrics were first
	 * recorded. A metric nobody measured does not appear at all rather than as an empty row.
	 *
	 * @param vitals the period's readings, oldest first
	 * @return the ranges, never null
	 */
	public static List<VitalRange> summarise(List<VitalFact> vitals) {
		Map<String, List<VitalFact>> byMetric = new LinkedHashMap<>();
		for (VitalFact reading : vitals) {
			byMetric.computeIfAbsent(reading.metric(), metric -> new ArrayList<>()).add(reading);
		}

		List<VitalRange> ranges = new ArrayList<>();
		byMetric.forEach((metric, readings) -> ranges.add(new VitalRange(
				metric,
				readings.stream().map(VitalFact::value).min(Comparator.naturalOrder()).orElseThrow(),
				readings.stream().map(VitalFact::value).max(Comparator.naturalOrder()).orElseThrow(),
				readings.stream().map(VitalFact::unit).filter(Objects::nonNull).findFirst().orElse(null),
				readings.size())));
		return List.copyOf(ranges);
	}

	/** True when every reading was the same, so there is one value rather than a span to show. */
	public boolean isSingleValue() {
		return lowest.compareTo(highest) == 0;
	}
}
