package sg.nus.carelink.report.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One number a report section states, with what it is out of when that matters: 2 of 3 visits
 * carried out, a fulfilment of 66.67 %, an average rating of 4.5 out of 5.
 *
 * <p>The same number is in the section's text. This is the form a screen can lay out without
 * reading the text back, and it says nothing the text beside it does not.
 *
 * <p>Numbers are kept without trailing zeros - 3.5, not the 3.50 its column holds - as the text
 * states them. The JSON column a report is archived in keeps a number's value but not its
 * scale, so a figure that kept the zeros would not read back as the one that was filed.
 *
 * @param key   stable within its section, so a screen can find a figure without its label
 * @param outOf what the value is out of; null when it stands alone
 * @param unit  "%" or null
 */
public record ReportFigure(String key, String label, BigDecimal value, BigDecimal outOf, String unit) {

	public ReportFigure {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(label, "label");
		value = plain(Objects.requireNonNull(value, "value"));
		outOf = outOf == null ? null : plain(outOf);
	}

	/** A number without trailing zeros and never in exponent form: 100.00 becomes 100, 36.80 becomes 36.8. */
	public static BigDecimal plain(BigDecimal number) {
		BigDecimal stripped = number.stripTrailingZeros();
		return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
	}

	/** A count on its own: "1 incident". */
	public static ReportFigure count(String key, String label, long value) {
		return new ReportFigure(key, label, BigDecimal.valueOf(value), null, null);
	}

	/** A count out of a total: "2 of 3 visits". */
	public static ReportFigure share(String key, String label, long value, long outOf) {
		return new ReportFigure(key, label, BigDecimal.valueOf(value), BigDecimal.valueOf(outOf), null);
	}
}
