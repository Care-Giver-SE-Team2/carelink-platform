package sg.nus.carelink.report.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A note appended to a report: dated, signed, and never edited afterwards.
 *
 * <p>Created only through {@link Report#amend}, which is where the rules about what a note
 * must say are checked.
 *
 * @param id   null until it has been stored
 * @param kind whether the note corrects the report or follows up on something it reported
 */
public record ReportAmendment(
		Long id,
		Long reportId,
		ReportAmendment.Kind kind,
		String note,
		Long authorUserId,
		LocalDateTime createdAt) {

	/** report_amendment.note is VARCHAR(1000). */
	public static final int MAX_NOTE_LENGTH = 1000;

	public ReportAmendment {
		Objects.requireNonNull(reportId, "reportId");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(note, "note");
		Objects.requireNonNull(authorUserId, "authorUserId");
		Objects.requireNonNull(createdAt, "createdAt");
	}

	/** A correction: what every note was before follow-ups existed. */
	public ReportAmendment(Long id, Long reportId, String note, Long authorUserId, LocalDateTime createdAt) {
		this(id, reportId, Kind.CORRECTION, note, authorUserId, createdAt);
	}

	/**
	 * What a note is. Both kinds are appended the same way and neither changes the report; they
	 * differ in what they tell its reader.
	 */
	public enum Kind {
		/** The report said something that was wrong or missing, and this is what is right. */
		CORRECTION,
		/** Something the report recorded was acted on afterwards, and this is what was done. */
		FOLLOW_UP
	}
}
