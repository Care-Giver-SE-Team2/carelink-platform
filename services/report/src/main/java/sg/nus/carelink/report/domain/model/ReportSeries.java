package sg.nus.carelink.report.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * One measured metric over the period, as points a screen can draw: the chart behind the
 * text of the vital signs section.
 *
 * <p>How fine the points are is the reader's rule, like everything else in a report: the
 * family's version has one point per day, the lowest and highest reading of that day, in
 * keeping with being shown ranges rather than readings; the other versions have every reading.
 *
 * @param key    the metric as the caregiver's form recorded it: systolic, pulse …
 * @param label  how the reader sees it named: Systolic
 * @param unit   the unit of the metric's first reading that has one; may be null
 * @param points oldest first
 */
public record ReportSeries(String key, String label, String unit, List<ReportSeries.Point> points) {

	public ReportSeries {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(label, "label");
		points = points == null ? List.of() : List.copyOf(points);
	}

	/**
	 * One point: a single reading, or the lowest and highest of one day's readings.
	 *
	 * @param at      ISO-8601 - a date and time for a reading ("2026-09-14T09:10"), a date for a
	 *                day ("2026-09-14"); kept as text because that is all a reader is shown
	 * @param low     the reading, or the day's lowest
	 * @param high    the same as {@code low} for a reading, or the day's highest
	 * @param flagged whether a reading behind the point was flagged out of range when it was
	 *                entered (story C6); never recomputed here
	 */
	public record Point(String at, BigDecimal low, BigDecimal high, boolean flagged) {

		/** Both ends are kept without trailing zeros, as {@link ReportFigure} explains. */
		public Point {
			Objects.requireNonNull(at, "at");
			low = ReportFigure.plain(Objects.requireNonNull(low, "low"));
			high = ReportFigure.plain(Objects.requireNonNull(high, "high"));
		}
	}
}
