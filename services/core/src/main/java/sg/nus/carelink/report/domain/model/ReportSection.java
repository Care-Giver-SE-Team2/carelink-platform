package sg.nus.carelink.report.domain.model;

import java.util.Objects;

/**
 * One titled section of a report. The body is plain text with one line per item; the reader's
 * screen decides how to lay it out.
 */
public record ReportSection(String title, String body) {

	public ReportSection {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(body, "body");
	}
}
