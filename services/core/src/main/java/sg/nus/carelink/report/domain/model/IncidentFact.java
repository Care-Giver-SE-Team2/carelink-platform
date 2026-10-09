package sg.nus.carelink.report.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One care exception reported in the period, with its timeline.
 *
 * <p>A reading of the incident tables, not the incident module's aggregate, for the same
 * reason as {@link VisitFact}. Category, severity and status are kept as the text the table
 * holds: the report only ever shows them, and restating a second module's enums here would
 * be one more thing to keep in step for nothing.
 *
 * @param timeline every entry, oldest first; the audit version of a report is built from it
 */
public record IncidentFact(
		Long id,
		String category,
		String severity,
		String status,
		String description,
		LocalDateTime reportedAt,
		LocalDateTime resolvedAt,
		List<IncidentFact.Step> timeline) {

	private static final String RESOLVED = "RESOLVED";

	/**
	 * How the incident module writes a closing entry: the outcome code, this separator, then
	 * the manager's note ({@code IncidentService.resolve}). The only structure in the text.
	 */
	private static final String OUTCOME_SEPARATOR = " :: ";

	public IncidentFact {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(severity, "severity");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(reportedAt, "reportedAt");
		timeline = timeline == null ? List.of() : List.copyOf(timeline);
	}

	public boolean isResolved() {
		return RESOLVED.equals(status);
	}

	/**
	 * The conclusion written when the incident was closed ("处置结论"), taken from the last
	 * closing entry of the timeline. Empty while the incident is still open.
	 */
	public Optional<String> resolution() {
		for (int i = timeline.size() - 1; i >= 0; i--) {
			Step step = timeline.get(i);
			if (RESOLVED.equals(step.action()) && step.detail() != null) {
				return Optional.of(step.detail());
			}
		}
		return Optional.empty();
	}

	/**
	 * Just the outcome code from the conclusion - HANDLED_ON_SITE, REFERRED_TO_MEDICAL_CARE,
	 * FALSE_ALARM - without the note that follows it. What a reader who may not see the
	 * manager's own words can still be told.
	 */
	public Optional<String> outcome() {
		return resolution()
				.filter(text -> text.contains(OUTCOME_SEPARATOR))
				.map(text -> text.substring(0, text.indexOf(OUTCOME_SEPARATOR)).strip());
	}

	/** One timeline entry: who did what, and when. */
	public record Step(String actor, String action, String detail, LocalDateTime occurredAt) {

		public Step {
			Objects.requireNonNull(action, "action");
			Objects.requireNonNull(occurredAt, "occurredAt");
		}
	}
}
