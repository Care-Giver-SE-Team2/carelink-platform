package sg.nus.carelink.report.domain.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.VisitFact;
import sg.nus.carelink.report.domain.model.VitalFact;

/**
 * Turns one period's facts into what one kind of reader is told: the Template Method at the
 * centre of DP5.
 *
 * <p>UC-MG07 wants one set of facts to become three reports that "share a skeleton and differ
 * in their filtering rules" (骨架相同、过滤规则不同). The skeleton is {@link #assemble}: state
 * whether the facts were complete, then Service completion, Vital signs, Observations and
 * Incidents in that order, then the disclaimer. It is {@code final}. No reader's version can
 * reorder the sections, drop one or add another, so "the same skeleton" is something the
 * compiler holds rather than something three classes have to agree on.
 *
 * <p>What a subclass decides is how much of each step its reader sees, through the five hooks
 * and nothing else:
 * <ul>
 *   <li>{@link #describeCaregiver} - a name, a number, or both;</li>
 *   <li>{@link #vitalSigns} - a range per metric, or every reading;</li>
 *   <li>{@link #observations} - the caregivers' words, or only how many there were;</li>
 *   <li>{@link #incidents} - what happened, or also who did what about it and when;</li>
 *   <li>{@link #disclaimer} - the family's fixed medical disclaimer, or none.</li>
 * </ul>
 * Everything else is written once, here: the completeness check and how a gap is worded, the
 * service-completion section, and what a section says when the period had nothing for it.
 * Those parts are private, so a reader's version cannot quietly word a gap differently.
 *
 * <p><strong>Why not Strategy</strong> - one filter interface with three implementations,
 * held by a generator. What varies between readers is not the algorithm but a few of its
 * steps. With Strategy each filter would either repeat the skeleton, and the three copies
 * would drift, or the skeleton would move into the generator and the filters would shrink to
 * a bag of unrelated callbacks with nothing tying them to the order they are called in.
 * Template Method keeps the invariant part and the variable parts in one type, with the line
 * between them drawn by {@code final} and {@code abstract}.
 *
 * <p>Which subclass serves which reader is decided in one place, {@link #forAudience}, so no
 * caller names a subclass and adding a fourth reader is a new subclass plus one line there.
 * The content itself is put together through {@link ReportContentBuilder}, the Builder half
 * of the same design problem.
 */
public abstract class ReportAssembler {

	/** Joins the parts of one line. The screens show the body as plain text. */
	protected static final String SEPARATOR = " · ";

	protected static final String NO_VISITS = "No visits were scheduled in this period.";
	protected static final String NO_VITALS = "No vital signs were recorded in this period.";
	protected static final String NO_OBSERVATIONS = "No observations were recorded in this period.";
	protected static final String NO_INCIDENTS = "No incidents were reported in this period.";

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter DAY_AND_TIME =
			DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH);

	protected ReportAssembler() {
	}

	/**
	 * The assembler for one kind of reader.
	 *
	 * @param audience who the report is for
	 * @return a new assembler; they hold no state, so there is no reason to share one
	 */
	public static ReportAssembler forAudience(Report.Audience audience) {
		return switch (audience) {
			case FAMILY -> new FamilyReportAssembler();
			case REGULATOR -> new RegulatorReportAssembler();
			case INTERNAL -> new InternalReportAssembler();
		};
	}

	/**
	 * The skeleton every reader's report follows.
	 *
	 * <p>A section with nothing to report still appears, saying so, and the hooks are only
	 * asked about sections that have something in them: every reader gets all four sections,
	 * because a version that left one out would not be sharing the skeleton any more.
	 *
	 * @param facts one elder's period, the same instance for all three readers
	 * @return this reader's content, ready to be archived
	 */
	public final ReportContent assemble(ReportFacts facts) {
		return ReportContentBuilder.create()
				.completeness(facts.unclosedVisits(), this::describeMissing)
				.section("Service completion", serviceCompletion(facts))
				.section("Vital signs", facts.vitals().isEmpty() ? NO_VITALS : vitalSigns(facts))
				.section("Observations", facts.observations().isEmpty() ? NO_OBSERVATIONS : observations(facts))
				.section("Incidents", facts.incidents().isEmpty() ? NO_INCIDENTS : incidents(facts))
				.disclaimer(disclaimer())
				.generatedBy(ReportContent.GeneratedBy.TEMPLATE)
				.build();
	}

	// -------------------------------------------------------------------------- hooks ---

	/** How a visit's caregiver is named to this reader. Only asked about visits that have one. */
	protected abstract String describeCaregiver(VisitFact visit);

	/** The period's readings, for a period that has at least one. */
	protected abstract String vitalSigns(ReportFacts facts);

	/** The caregivers' notes, for a period that has at least one. */
	protected abstract String observations(ReportFacts facts);

	/** The period's incidents, for a period that has at least one. */
	protected abstract String incidents(ReportFacts facts);

	/** Fixed text shown under the report, or null when this reader gets none. */
	protected abstract String disclaimer();

	// ---------------------------------------------------------------- invariant steps ---

	/**
	 * One line per visit that is not closed. Worded the same for every reader - the contract's
	 * example is "Visit 8812 on 2026-09-03 not closed" - because a gap is a fact about the
	 * records, not about who is reading them.
	 */
	private String describeMissing(VisitFact visit) {
		return "Visit %d on %s not closed".formatted(visit.id(), visit.scheduledStart().toLocalDate());
	}

	/**
	 * How many visits there were and how each one ended. The same for every reader except for
	 * how the caregiver is named, which is the one thing this step asks a hook.
	 */
	private String serviceCompletion(ReportFacts facts) {
		List<VisitFact> visits = facts.visits();
		if (visits.isEmpty()) {
			return NO_VISITS;
		}

		List<String> lines = new ArrayList<>();
		lines.add(tally(visits));
		for (VisitFact visit : visits) {
			lines.add(String.join(SEPARATOR,
					timeOf(visit.scheduledStart()),
					visit.serviceType() == null || visit.serviceType().isBlank() ? "Visit" : visit.serviceType(),
					caregiverOf(visit),
					outcome(visit.status()),
					evidence(visit)));
		}
		return lines(lines);
	}

	/** "3 visits: 1 scheduled, 2 verified." - counted in the order a visit moves through its states. */
	private static String tally(List<VisitFact> visits) {
		Map<VisitFact.Status, Long> byStatus = new EnumMap<>(VisitFact.Status.class);
		visits.forEach(visit -> byStatus.merge(visit.status(), 1L, Long::sum));
		String breakdown = byStatus.entrySet().stream()
				.map(entry -> entry.getValue() + " " + outcome(entry.getKey()))
				.collect(Collectors.joining(", "));
		return "%s: %s.".formatted(counted(visits.size(), "visit", "visits"), breakdown);
	}

	private static String outcome(VisitFact.Status status) {
		return switch (status) {
			case SCHEDULED -> "scheduled";
			case ARRIVED -> "caregiver arrived";
			case IN_PROGRESS -> "in progress";
			case COMPLETED -> "awaiting the elder's confirmation";
			case VERIFIED -> "verified";
			case AUTO_CLOSED -> "closed without the elder's confirmation";
			case EXCEPTION -> "ended in an exception";
			case CANCELLED -> "cancelled";
		};
	}

	private static String evidence(VisitFact visit) {
		return visit.evidenceCount() == 0
				? "no evidence"
				: "evidence %d of %d verified".formatted(visit.verifiedEvidenceCount(), visit.evidenceCount());
	}

	// --------------------------------------------------------- helpers for the hooks ---

	/** The caregiver as this reader sees them, or a plain statement that there was none. */
	protected final String caregiverOf(VisitFact visit) {
		return visit.hasCaregiver() ? describeCaregiver(visit) : "no caregiver assigned";
	}

	/**
	 * Every reading on its own line, flagged when it was out of range at entry. For the readers
	 * who are shown the measurements themselves rather than a range.
	 */
	protected static String eachReading(ReportFacts facts) {
		return lines(facts.vitals().stream()
				.map(reading -> String.join(SEPARATOR,
						timeOf(reading.recordedAt()),
						"visit " + reading.visitId(),
						measurement(reading))
						+ (reading.outOfRange() ? SEPARATOR + "out of range" : ""))
				.toList());
	}

	/** "Systolic 142 mmHg". */
	protected static String measurement(VitalFact reading) {
		return withUnit(metricName(reading.metric()) + " " + amount(reading.value()), reading.unit());
	}

	/** "systolic" becomes "Systolic"; the form stores metrics in lower case. */
	protected static String metricName(String metric) {
		return metric.isEmpty() ? metric : Character.toUpperCase(metric.charAt(0)) + metric.substring(1);
	}

	/** A reading without the trailing zeros DECIMAL(8,2) gives it: 128.00 reads as 128, 36.80 as 36.8. */
	protected static String amount(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	protected static String withUnit(String text, String unit) {
		return unit == null || unit.isBlank() ? text : text + " " + unit;
	}

	/** "Tue 15 Sep". */
	protected static String dayOf(LocalDateTime moment) {
		return DAY.format(moment);
	}

	/** "Tue 15 Sep 09:00". */
	protected static String timeOf(LocalDateTime moment) {
		return DAY_AND_TIME.format(moment);
	}

	/** "1 visit", "2 visits". */
	protected static String counted(long count, String one, String many) {
		return count + " " + (count == 1 ? one : many);
	}

	protected static String lines(List<String> lines) {
		return String.join("\n", lines);
	}
}
