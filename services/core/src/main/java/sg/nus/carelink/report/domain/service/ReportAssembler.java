package sg.nus.carelink.report.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import sg.nus.carelink.report.domain.model.ConfirmationFact;
import sg.nus.carelink.report.domain.model.ElderProfile;
import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportFigure;
import sg.nus.carelink.report.domain.model.ReportMetrics;
import sg.nus.carelink.report.domain.model.ReportSection;
import sg.nus.carelink.report.domain.model.ReportSeries;
import sg.nus.carelink.report.domain.model.SpotCheckFact;
import sg.nus.carelink.report.domain.model.VisitFact;
import sg.nus.carelink.report.domain.model.VitalFact;

/**
 * Turns one period's facts into what one kind of reader is told: the Template Method at the
 * centre of DP5.
 *
 * <p>UC-MG07 wants one set of facts to become three reports that "share a skeleton and differ
 * in their filtering rules" (骨架相同、过滤规则不同). The skeleton is {@link #assemble}: state
 * whether the facts were complete, then Overview, Services, Service completion, Vital signs,
 * Observations, Incidents and Ratings and spot checks in that order, then the disclaimer. It
 * is {@code final}. No reader's version can reorder the sections, drop one or add another, so
 * "the same skeleton" is something the compiler holds rather than something three classes
 * have to agree on.
 *
 * <p>What a subclass decides is how much of each step its reader sees, through the hooks and
 * nothing else:
 * <ul>
 *   <li>{@link #describeCaregiver} - a name, a number, or both;</li>
 *   <li>{@link #aboutTheElder} - the plan alone, everything on file, or a profile nobody can
 *       be recognised from;</li>
 *   <li>{@link #serviceDetail} - what changed in words the family used, everyone's workload
 *       and every change, or the same in counts;</li>
 *   <li>{@link #vitalSigns} and {@link #vitalSeries} - a range per metric and a point per day,
 *       or every reading;</li>
 *   <li>{@link #observations} - the caregivers' words, or only how many there were;</li>
 *   <li>{@link #incidents} - what happened, or also who did what about it and when;</li>
 *   <li>{@link #ratings} - what the family took part in, every answer with its words, or
 *       counts and conclusions without them;</li>
 *   <li>{@link #disclaimer} - the family's fixed medical disclaimer, or none.</li>
 * </ul>
 * Everything else is written once, here: the completeness check and how a gap is worded, the
 * period's numbers and the sentence that states them, the tally per service, the
 * service-completion section, the figures every section carries, and what a section says
 * when the period had nothing for it. Those parts are private, so a reader's version cannot
 * quietly count or word them differently.
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

	/** The skeleton's section titles, in order. The family's screens find four of them by title. */
	public static final String OVERVIEW = "Overview";
	public static final String SERVICES = "Services";
	public static final String SERVICE_COMPLETION = "Service completion";
	public static final String VITAL_SIGNS = "Vital signs";
	public static final String OBSERVATIONS = "Observations";
	public static final String INCIDENTS = "Incidents";
	public static final String RATINGS = "Ratings and spot checks";

	/** Joins the parts of one line. The screens show the body as plain text. */
	protected static final String SEPARATOR = " · ";

	protected static final String NO_VISITS = "No visits were scheduled in this period.";
	protected static final String NO_VITALS = "No vital signs were recorded in this period.";
	protected static final String NO_OBSERVATIONS = "No observations were recorded in this period.";
	protected static final String NO_INCIDENTS = "No incidents were reported in this period.";
	protected static final String NO_RATINGS = "No ratings, reviews or spot checks were recorded in this period.";

	/** The top of the rating scale the elder and the family rate on (UC-EL01, UC-FM09). */
	protected static final BigDecimal RATING_SCALE = BigDecimal.valueOf(5);

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter DAY_AND_TIME =
			DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

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
	 * asked about sections that have something in them: every reader gets every section,
	 * because a version that left one out would not be sharing the skeleton any more.
	 *
	 * @param facts one elder's period, the same instance for all three readers
	 * @return this reader's content, ready to be archived
	 */
	public final ReportContent assemble(ReportFacts facts) {
		ReportMetrics metrics = ReportMetrics.of(facts);
		return ReportContentBuilder.create()
				.completeness(facts.unclosedVisits(), this::describeMissing)
				.section(OVERVIEW, overview(facts, metrics), overviewFigures(facts, metrics), List.of())
				.section(SERVICES, services(facts), serviceFigures(facts), List.of())
				.section(SERVICE_COMPLETION, serviceCompletion(facts))
				.section(VITAL_SIGNS,
						facts.vitals().isEmpty() ? NO_VITALS : vitalSigns(facts),
						List.of(),
						facts.vitals().isEmpty() ? List.of() : vitalSeries(facts))
				.section(OBSERVATIONS, facts.observations().isEmpty() ? NO_OBSERVATIONS : observations(facts))
				.section(INCIDENTS, facts.incidents().isEmpty() ? NO_INCIDENTS : incidents(facts))
				.section(RATINGS,
						facts.quality().isEmpty() ? NO_RATINGS : ratings(facts, metrics),
						ratingFigures(facts, metrics),
						List.of())
				.disclaimer(disclaimer())
				.generatedBy(ReportContent.GeneratedBy.TEMPLATE)
				.build();
	}

	// -------------------------------------------------------------------------- hooks ---

	/** How a caregiver is named to this reader. Only asked about caregivers who are on record. */
	protected abstract String describeCaregiver(Long caregiverId, String caregiverName);

	/**
	 * What this reader is told about the elder and the plan in force, as lines; empty for
	 * nothing. Only asked when the elder's profile was read.
	 */
	protected abstract String aboutTheElder(ReportFacts facts);

	/**
	 * What this reader is told about the service beyond the tally per service - who did the
	 * work, what changed and what was asked for - as lines; empty for nothing.
	 */
	protected abstract String serviceDetail(ReportFacts facts);

	/** The period's readings, for a period that has at least one. */
	protected abstract String vitalSigns(ReportFacts facts);

	/** The period's readings as one series per metric, for a period that has at least one. */
	protected abstract List<ReportSeries> vitalSeries(ReportFacts facts);

	/** The caregivers' notes, for a period that has at least one. */
	protected abstract String observations(ReportFacts facts);

	/** The period's incidents, for a period that has at least one. */
	protected abstract String incidents(ReportFacts facts);

	/** The elder's answers, the family's reviews and the spot checks, for a period with at least one. */
	protected abstract String ratings(ReportFacts facts, ReportMetrics metrics);

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

	/** What this reader is told about the elder, then the period's numbers in the same words for everyone. */
	private String overview(ReportFacts facts, ReportMetrics metrics) {
		List<String> lines = new ArrayList<>();
		if (!ElderProfile.UNKNOWN.equals(facts.elder())) {
			addIfPresent(lines, aboutTheElder(facts));
		}
		lines.add(visitsSentence(facts, metrics));
		lines.add(vitalsSentence(facts, metrics));
		lines.add(incidentsSentence(facts));
		lines.add(metrics.averageElderRating() == null
				? "Visit ratings: none given."
				: "Visit ratings: average %s out of 5 from %s.".formatted(
						amount(metrics.averageElderRating()), counted(metrics.ratingCount(), "rating", "ratings")));
		return lines(lines);
	}

	private static String visitsSentence(ReportFacts facts, ReportMetrics metrics) {
		if (facts.visits().isEmpty()) {
			return "Visits: none scheduled.";
		}
		long cancelled = facts.visits().size() - (long) metrics.visitsPlanned();
		String planned = metrics.visitsPlanned() == 0
				? "Visits: none planned"
				: "Visits: %d of %d carried out (%s%%)".formatted(
						metrics.visitsCompleted(), metrics.visitsPlanned(), amount(metrics.fulfilmentRate()));
		return planned + (cancelled == 0 ? "." : "; %d cancelled.".formatted(cancelled));
	}

	private static String vitalsSentence(ReportFacts facts, ReportMetrics metrics) {
		return facts.vitals().isEmpty()
				? "Vital signs: none recorded."
				: "Vital signs: %s, %d out of range.".formatted(
						counted(facts.vitals().size(), "reading", "readings"), metrics.vitalsOutOfRange());
	}

	private static String incidentsSentence(ReportFacts facts) {
		long resolved = facts.incidents().stream().filter(IncidentFact::isResolved).count();
		return facts.incidents().isEmpty()
				? "Incidents: none reported."
				: "Incidents: %d reported, %d resolved.".formatted(facts.incidents().size(), resolved);
	}

	/** The overview's numbers, the same for every reader. */
	private static List<ReportFigure> overviewFigures(ReportFacts facts, ReportMetrics metrics) {
		List<ReportFigure> figures = new ArrayList<>();
		figures.add(ReportFigure.share("visits", "Visits carried out", metrics.visitsCompleted(), metrics.visitsPlanned()));
		if (metrics.fulfilmentRate() != null) {
			figures.add(new ReportFigure("fulfilment", "Fulfilment", metrics.fulfilmentRate(), null, "%"));
		}
		figures.add(ReportFigure.share("out-of-range", "Readings out of range",
				metrics.vitalsOutOfRange(), facts.vitals().size()));
		figures.add(ReportFigure.count("incidents", "Incidents", metrics.incidentCount()));
		if (metrics.averageElderRating() != null) {
			figures.add(new ReportFigure("rating", "Average visit rating", metrics.averageElderRating(), RATING_SCALE, null));
		}
		return figures;
	}

	/** One line per kind of service, how many were carried out of how many planned, then the reader's detail. */
	private String services(ReportFacts facts) {
		List<String> lines = new ArrayList<>();
		if (facts.visits().isEmpty()) {
			lines.add(NO_VISITS);
		}
		byService(facts.visits()).forEach((service, visits) -> {
			long planned = visits.stream().filter(VisitFact::isPlanned).count();
			long completed = visits.stream().filter(VisitFact::isCompleted).count();
			long cancelled = visits.size() - planned;
			if (planned == 0) {
				lines.add("%s: %d cancelled".formatted(service, cancelled));
			} else {
				lines.add("%s: %d of %d carried out%s".formatted(
						service, completed, planned, cancelled == 0 ? "" : ", %d cancelled".formatted(cancelled)));
			}
		});
		addIfPresent(lines, serviceDetail(facts));
		return lines(lines);
	}

	/** Carried out of planned, per kind of service, the same for every reader. */
	private static List<ReportFigure> serviceFigures(ReportFacts facts) {
		List<ReportFigure> figures = new ArrayList<>();
		byService(facts.visits()).forEach((service, visits) -> figures.add(ReportFigure.share(
				ReportSection.keyOf(service), service,
				visits.stream().filter(VisitFact::isCompleted).count(),
				visits.stream().filter(VisitFact::isPlanned).count())));
		return figures;
	}

	/** The period's visits by kind of service, in the order each kind was first scheduled. */
	private static Map<String, List<VisitFact>> byService(List<VisitFact> visits) {
		Map<String, List<VisitFact>> byService = new LinkedHashMap<>();
		for (VisitFact visit : visits) {
			byService.computeIfAbsent(serviceName(visit), name -> new ArrayList<>()).add(visit);
		}
		return byService;
	}

	private static String serviceName(VisitFact visit) {
		return visit.serviceType() == null || visit.serviceType().isBlank() ? "Visit" : visit.serviceType();
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
					serviceName(visit),
					caregiverOf(visit),
					outcome(visit),
					evidence(visit)));
		}
		return lines(lines);
	}

	/**
	 * "3 visits: 1 scheduled, 2 verified." - counted in the order a visit moves through its states.
	 * A visit the family skipped is counted on its own, after the other cancellations: part of the
	 * period's record, and never a missed visit (UC-MG04 alternative 4c).
	 */
	private static String tally(List<VisitFact> visits) {
		Map<String, Long> byOutcome = new LinkedHashMap<>();
		visits.stream()
				.sorted(Comparator.comparing(VisitFact::status).thenComparing(VisitFact::cancelledByFamily))
				.forEach(visit -> byOutcome.merge(outcome(visit), 1L, Long::sum));
		String breakdown = byOutcome.entrySet().stream()
				.map(entry -> entry.getValue() + " " + entry.getKey())
				.collect(Collectors.joining(", "));
		return "%s: %s.".formatted(counted(visits.size(), "visit", "visits"), breakdown);
	}

	private static String outcome(VisitFact visit) {
		return visit.cancelledByFamily() ? "cancelled at the family's request" : outcome(visit.status());
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

	/** The elder's answers, reviews and spot checks counted, the same for every reader. */
	private static List<ReportFigure> ratingFigures(ReportFacts facts, ReportMetrics metrics) {
		List<ReportFigure> figures = new ArrayList<>();
		if (metrics.averageElderRating() != null) {
			figures.add(new ReportFigure("rating", "Average visit rating", metrics.averageElderRating(), RATING_SCALE, null));
		}
		figures.add(ReportFigure.count("ratings", "Visit ratings", metrics.ratingCount()));
		figures.add(ReportFigure.count("disputed", "Visits disputed",
				facts.quality().confirmations().stream().filter(ConfirmationFact::disputed).count()));
		figures.add(ReportFigure.count("reviews", "Periodic reviews", facts.quality().reviews().size()));
		figures.add(ReportFigure.count("spot-checks", "Spot checks", facts.quality().spotChecks().size()));
		return figures;
	}

	// --------------------------------------------------------- helpers for the hooks ---

	/** The caregiver as this reader sees them, or a plain statement that there was none. */
	protected final String caregiverOf(VisitFact visit) {
		return caregiver(visit.caregiverId(), visit.caregiverName());
	}

	/** Any caregiver on record, as this reader sees them; "no caregiver assigned" without an id. */
	protected final String caregiver(Long caregiverId, String caregiverName) {
		return caregiverId == null ? "no caregiver assigned" : describeCaregiver(caregiverId, caregiverName);
	}

	/**
	 * One line per caregiver who had a visit in the period: how many, how many were carried
	 * out, and the hours on site against the hours planned. Named as this reader names them.
	 */
	protected final List<String> workload(ReportFacts facts) {
		Map<Long, List<VisitFact>> byCaregiver = new LinkedHashMap<>();
		for (VisitFact visit : facts.visits()) {
			if (visit.hasCaregiver() && visit.isPlanned()) {
				byCaregiver.computeIfAbsent(visit.caregiverId(), id -> new ArrayList<>()).add(visit);
			}
		}
		List<String> lines = new ArrayList<>();
		byCaregiver.forEach((caregiverId, visits) -> {
			int worked = visits.stream().map(VisitFact::workedMinutes).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
			int planned = visits.stream().map(VisitFact::plannedMinutes).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
			lines.add("%s: %s, %d carried out%s%s".formatted(
					caregiverOf(visits.getFirst()),
					counted(visits.size(), "visit", "visits"),
					visits.stream().filter(VisitFact::isCompleted).count(),
					worked == 0 ? "" : SEPARATOR + hours(worked) + " on site",
					planned == 0 ? "" : SEPARATOR + hours(planned) + " planned"));
		});
		return lines;
	}

	/**
	 * Every reading as its own point, for the readers who are shown the measurements
	 * themselves. One series per metric, in the order the metrics were first recorded.
	 */
	protected static List<ReportSeries> seriesOfReadings(ReportFacts facts) {
		List<ReportSeries> series = new ArrayList<>();
		byMetric(facts.vitals()).forEach((metric, readings) -> series.add(new ReportSeries(
				metric, metricName(metric), unitOf(readings),
				readings.stream()
						.map(reading -> new ReportSeries.Point(
								reading.recordedAt().toString(), reading.value(), reading.value(), reading.outOfRange()))
						.toList())));
		return series;
	}

	/**
	 * One point per day - the day's lowest and highest reading, flagged when any of them was -
	 * for the reader who is shown ranges rather than readings.
	 */
	protected static List<ReportSeries> seriesOfDays(ReportFacts facts) {
		List<ReportSeries> series = new ArrayList<>();
		byMetric(facts.vitals()).forEach((metric, readings) -> {
			Map<LocalDate, List<VitalFact>> byDay = new LinkedHashMap<>();
			for (VitalFact reading : readings) {
				byDay.computeIfAbsent(reading.recordedAt().toLocalDate(), day -> new ArrayList<>()).add(reading);
			}
			List<ReportSeries.Point> points = new ArrayList<>();
			byDay.forEach((day, ofDay) -> points.add(new ReportSeries.Point(
					day.toString(),
					ofDay.stream().map(VitalFact::value).min(Comparator.naturalOrder()).orElseThrow(),
					ofDay.stream().map(VitalFact::value).max(Comparator.naturalOrder()).orElseThrow(),
					ofDay.stream().anyMatch(VitalFact::outOfRange))));
			series.add(new ReportSeries(metric, metricName(metric), unitOf(readings), points));
		});
		return series;
	}

	private static Map<String, List<VitalFact>> byMetric(List<VitalFact> vitals) {
		Map<String, List<VitalFact>> byMetric = new LinkedHashMap<>();
		for (VitalFact reading : vitals) {
			byMetric.computeIfAbsent(reading.metric(), metric -> new ArrayList<>()).add(reading);
		}
		return byMetric;
	}

	private static String unitOf(List<VitalFact> readings) {
		return readings.stream().map(VitalFact::unit).filter(Objects::nonNull).findFirst().orElse(null);
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

	/** How a spot check stands, in words every reader can be given. */
	protected static String spotCheckState(SpotCheckFact check) {
		if (check.outcome() != null) {
			return switch (check.outcome()) {
				case "COMPLETED" -> "NEEDS_IMPROVEMENT".equals(check.result()) ? "needs improvement" : "meets the standard";
				case "CAREGIVER_NO_SHOW" -> "the caregiver did not turn up";
				case "WITHDRAWN" -> "withdrawn";
				default -> check.outcome().toLowerCase(Locale.ROOT);
			};
		}
		return switch (check.approvalStatus()) {
			case "PENDING_APPROVAL" -> "awaiting the family's consent";
			case "REJECTED" -> "declined by the family";
			default -> "planned";
		};
	}

	/** A family's renewal decision in words. */
	protected static String renewal(String decision) {
		return switch (decision) {
			case "RENEW_CURRENT" -> "keep the current caregiver";
			case "REQUEST_CHANGE" -> "change the caregiver";
			case "CANCEL_SERVICE" -> "cancel the service";
			default -> decision.toLowerCase(Locale.ROOT);
		};
	}

	/** How a value-added request stands, in words. */
	protected static String requestState(String status) {
		return switch (status) {
			case "PENDING_APPROVAL" -> "awaiting the family's approval";
			case "APPROVED" -> "approved";
			case "REJECTED" -> "declined";
			case "DISPATCHED" -> "approved and booked";
			case "COMPLETED" -> "done";
			case "CANCELLED" -> "cancelled";
			default -> status.toLowerCase(Locale.ROOT);
		};
	}

	/** The plan in force in a line: "Care plan version 3 · 6.5 h a week". */
	protected static String planLine(ElderProfile elder) {
		if (!elder.hasPlan()) {
			return "No care plan in force";
		}
		return "Care plan version " + elder.planVersion()
				+ (elder.planWeeklyHours() == null ? "" : SEPARATOR + amount(elder.planWeeklyHours()) + " h a week");
	}

	/** The table's mobility level in words. */
	protected static String mobility(String level) {
		return switch (level) {
			case "INDEPENDENT" -> "moves independently";
			case "ASSISTIVE_CANE" -> "walks with a cane";
			case "WHEELCHAIR_BEDBOUND" -> "wheelchair or bed-bound";
			default -> level.toLowerCase(Locale.ROOT);
		};
	}

	/** "Systolic 142 mmHg". */
	protected static String measurement(VitalFact reading) {
		return withUnit(metricName(reading.metric()) + " " + amount(reading.value()), reading.unit());
	}

	/** "systolic" becomes "Systolic"; the form stores metrics in lower case. */
	protected static String metricName(String metric) {
		return metric.isEmpty() ? metric : Character.toUpperCase(metric.charAt(0)) + metric.substring(1);
	}

	/** A number without the trailing zeros its column gives it: 128.00 reads as 128, 36.80 as 36.8. */
	protected static String amount(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	protected static String withUnit(String text, String unit) {
		return unit == null || unit.isBlank() ? text : text + " " + unit;
	}

	/** "2 h", "1.5 h", "0.75 h": minutes as hours, to two places at most. */
	protected static String hours(int minutes) {
		return amount(BigDecimal.valueOf(minutes).divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP)) + " h";
	}

	/** "Tue 15 Sep". */
	protected static String dayOf(LocalDateTime moment) {
		return DAY.format(moment);
	}

	/** "Tue 15 Sep 09:00". */
	protected static String timeOf(LocalDateTime moment) {
		return DAY_AND_TIME.format(moment);
	}

	/** "14 Sep – 20 Sep". */
	protected static String span(LocalDate from, LocalDate to) {
		return DATE.format(from) + " – " + DATE.format(to);
	}

	/** "1 visit", "2 visits". */
	protected static String counted(long count, String one, String many) {
		return count + " " + (count == 1 ? one : many);
	}

	protected static String lines(List<String> lines) {
		return String.join("\n", lines);
	}

	/** Adds a hook's lines unless it had nothing to say. */
	protected static void addIfPresent(List<String> lines, String text) {
		if (text != null && !text.isBlank()) {
			lines.add(text);
		}
	}
}
