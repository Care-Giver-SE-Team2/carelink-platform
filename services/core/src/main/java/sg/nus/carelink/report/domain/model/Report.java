package sg.nus.carelink.report.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * A periodic report for one elder and one kind of reader: the aggregate root of UC-MG07.
 *
 * <p>A report is written once and then only ever added to. The use case's rule is that an
 * archived report "不可删除、不可原地修改，更正只能以附加说明的形式追加", and this type is
 * shaped so that the rule does not depend on anybody remembering it:
 * <ul>
 *   <li>it is a record, so there is no setter to call;</li>
 *   <li>{@link #generate} is the only way to make one, and it is born PUBLISHED - there is no
 *       draft stage in which the text could still be edited;</li>
 *   <li>{@link #amend} is the only operation on an existing report. It returns a copy with
 *       one more correction on the end and every other component exactly as it was.</li>
 * </ul>
 * There is no delete here, and no endpoint that could ask for one.
 *
 * <p>Plain Java: no JPA, no Spring, no Jackson, so the rules can be exercised without a
 * database. ArchUnit fails the build if that changes.
 *
 * @param generatedByUserId the manager who asked for it; null when the weekly schedule did
 * @param amendments        oldest first
 */
public record Report(
		Long id,
		Long elderId,
		Long generatedByUserId,
		Report.Audience audience,
		ReportPeriod period,
		Report.Status status,
		ReportContent content,
		List<ReportAmendment> amendments,
		LocalDateTime createdAt) {

	public Report {
		Objects.requireNonNull(elderId, "elderId");
		Objects.requireNonNull(audience, "audience");
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(content, "content");
		amendments = amendments == null ? List.of() : List.copyOf(amendments);
	}

	/**
	 * Files a newly assembled report.
	 *
	 * <p>Straight to PUBLISHED: generating a report is archiving it (UC-MG07 steps 3 and 4 are
	 * one act here), so there is no moment at which the content exists but may still change.
	 *
	 * @param generatedByUserId who asked for it, or null for the scheduled run
	 * @param now               the application clock's reading, so the report and its
	 *                          corrections are dated on the same clock
	 */
	public static Report generate(
			Long elderId,
			Audience audience,
			ReportPeriod period,
			ReportContent content,
			Long generatedByUserId,
			LocalDateTime now) {

		return new Report(
				null,
				elderId,
				generatedByUserId,
				audience,
				period,
				Status.PUBLISHED,
				content,
				List.of(),
				Objects.requireNonNull(now, "now"));
	}

	/**
	 * Appends a correction. The one way a report changes after it has been filed.
	 *
	 * <p>The original text is not touched and the correction does not replace anything: a
	 * reader sees what was generated and, underneath it, what was said about it afterwards,
	 * by whom and when.
	 *
	 * @return a copy of this report with the correction added last; this one is unchanged
	 * @throws BusinessRuleViolation when the note is empty or longer than the column allows
	 * @throws IllegalStateException when the report has not been stored yet and so has no id
	 *                               a correction could refer to
	 */
	public Report amend(String note, Long authorUserId, LocalDateTime now) {
		if (id == null) {
			throw new IllegalStateException("A report has to be stored before it can be amended");
		}
		if (note == null || note.isBlank()) {
			throw new BusinessRuleViolation(
					"REPORT_AMENDMENT_NOTE_REQUIRED",
					"A correction has to say what it corrects");
		}
		String text = note.strip();
		if (text.length() > ReportAmendment.MAX_NOTE_LENGTH) {
			throw new BusinessRuleViolation(
					"REPORT_AMENDMENT_TOO_LONG",
					"A correction can be at most %d characters".formatted(ReportAmendment.MAX_NOTE_LENGTH));
		}

		List<ReportAmendment> appended = new ArrayList<>(amendments);
		appended.add(new ReportAmendment(
				null,
				id,
				text,
				Objects.requireNonNull(authorUserId, "authorUserId"),
				Objects.requireNonNull(now, "now")));

		return new Report(
				id, elderId, generatedByUserId, audience, period, status, content, appended, createdAt);
	}

	/** Who a report is for. The same facts are filtered differently for each. */
	public enum Audience {
		FAMILY, REGULATOR, INTERNAL
	}

	/**
	 * The table's states. Only PUBLISHED is ever produced: reports are archived as they are
	 * generated, and there is no step that sends one back to DRAFT or on to ARCHIVED.
	 */
	public enum Status {
		DRAFT, PUBLISHED, ARCHIVED
	}
}
