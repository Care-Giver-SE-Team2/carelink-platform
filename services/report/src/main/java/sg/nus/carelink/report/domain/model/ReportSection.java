package sg.nus.carelink.report.domain.model;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One titled section of a report. The body is plain text with one line per item; the reader's
 * screen decides how to lay it out.
 *
 * <p>Beside the text a section may carry the same content in a form a screen can lay out or
 * draw without reading the text back: the numbers it states ({@link ReportFigure}) and the
 * measurements it summarises ({@link ReportSeries}). They are filtered for the reader exactly
 * as the text is - a figure or a point never says more than the body beside it would let
 * that reader see.
 *
 * @param key     stable identifier of the section, derived from its title: "vital-signs"
 * @param figures the numbers the section states, in the order it states them
 * @param series  one per measured metric, for the sections that summarise readings
 */
public record ReportSection(String key, String title, String body, List<ReportFigure> figures, List<ReportSeries> series) {

	public ReportSection {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(body, "body");
		figures = figures == null ? List.of() : List.copyOf(figures);
		series = series == null ? List.of() : List.copyOf(series);
	}

	/** A section of text only, as every section was before figures and series existed. */
	public ReportSection(String title, String body) {
		this(keyOf(Objects.requireNonNull(title, "title")), title, body, List.of(), List.of());
	}

	/**
	 * The key a title gives: lower case, words joined by hyphens. "Ratings and spot checks"
	 * becomes "ratings-and-spot-checks". Also how a section stored before keys existed is
	 * keyed when it is read back, so old and new reports are found the same way.
	 */
	public static String keyOf(String title) {
		StringBuilder key = new StringBuilder();
		boolean gap = false;
		for (char c : title.toLowerCase(Locale.ROOT).toCharArray()) {
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
				if (gap && !key.isEmpty()) {
					key.append('-');
				}
				key.append(c);
				gap = false;
			} else {
				gap = true;
			}
		}
		return key.toString();
	}
}
