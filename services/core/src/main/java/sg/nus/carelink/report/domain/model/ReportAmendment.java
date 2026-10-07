package sg.nus.carelink.report.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A correction appended to a report: dated, signed, and never edited afterwards.
 *
 * <p>Created only through {@link Report#amend}, which is where the rules about what a
 * correction must say are checked.
 *
 * @param id null until it has been stored
 */
public record ReportAmendment(
		Long id,
		Long reportId,
		String note,
		Long authorUserId,
		LocalDateTime createdAt) {

	/** report_amendment.note is VARCHAR(1000). */
	public static final int MAX_NOTE_LENGTH = 1000;

	public ReportAmendment {
		Objects.requireNonNull(reportId, "reportId");
		Objects.requireNonNull(note, "note");
		Objects.requireNonNull(authorUserId, "authorUserId");
		Objects.requireNonNull(createdAt, "createdAt");
	}
}
