package sg.nus.carelink.report.domain.service;

import java.util.ArrayList;
import java.util.List;

import sg.nus.carelink.report.domain.model.ConfirmationFact;
import sg.nus.carelink.report.domain.model.ElderProfile;
import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.ObservationFact;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportMetrics;
import sg.nus.carelink.report.domain.model.ReportSeries;
import sg.nus.carelink.report.domain.model.ReviewFact;
import sg.nus.carelink.report.domain.model.RosterChangeFact;
import sg.nus.carelink.report.domain.model.SpotCheckFact;
import sg.nus.carelink.report.domain.model.ValueAddedFact;
import sg.nus.carelink.report.domain.model.VitalRange;

/**
 * The family's version: what happened to their relative, in words, without the working.
 *
 * <p>Follows the family rules the contract spells out for this report: caregivers by name
 * only, no staff-performance data, vital signs as ranges rather than raw measurements - in the
 * text and in the chart alike, which has one point per day - and a medical disclaimer that is
 * always there. Incidents are described - what, when, whether it is over - but not who on the
 * staff handled them or how quickly.
 *
 * <p>Of the rest, the family is told what it took part in: the plan it signed up for, the
 * caregiver changes and extra services it decided on, the reviews it wrote and the spot checks
 * it agreed to - each spot check by its conclusion, never by what the inspector wrote about
 * the caregiver. The elder's medical notes are not repeated back to it.
 */
final class FamilyReportAssembler extends ReportAssembler {

	/** UC-MG07: "家属版必须包含免责声明，明确其不构成医疗建议". Fixed text, never assembled. */
	private static final String DISCLAIMER = "This summary is prepared from care records for information only "
			+ "and does not constitute medical advice.";

	@Override
	protected String describeCaregiver(Long caregiverId, String caregiverName) {
		return caregiverName == null ? "a caregiver" : caregiverName;
	}

	/** The plan in force and the main caregiver: the elder is the family's own, and needs no describing. */
	@Override
	protected String aboutTheElder(ReportFacts facts) {
		ElderProfile elder = facts.elder();
		List<String> lines = new ArrayList<>();
		lines.add(planLine(elder) + ".");
		if (elder.hasPrimaryCaregiver()) {
			lines.add("Main caregiver: " + describeCaregiver(elder.primaryCaregiverId(), elder.primaryCaregiverName()) + ".");
		}
		return lines(lines);
	}

	/** The caregiver changes and extra services the family was asked about, in the words it was asked in. */
	@Override
	protected String serviceDetail(ReportFacts facts) {
		List<String> lines = new ArrayList<>();
		for (RosterChangeFact change : facts.changes().rosterChanges()) {
			lines.add(String.join(SEPARATOR,
					timeOf(change.visitStart()),
					caregiver(change.originalCaregiverId(), change.originalCaregiverName()) + " was away",
					settled(change)));
		}
		for (ValueAddedFact request : facts.changes().valueAdded()) {
			lines.add(String.join(SEPARATOR, request.service(), timeOf(request.requestedFor()), requestState(request.status())));
		}
		return lines(lines);
	}

	private String settled(RosterChangeFact change) {
		if (change.outcome() == null) {
			return "UNCOVERED".equals(change.status()) ? "no replacement found yet" : "waiting for the family's choice";
		}
		return switch (change.outcome()) {
			case "REPLACED" -> caregiver(change.assignedCaregiverId(), change.assignedCaregiverName()) + " took the visit";
			case "RESCHEDULED" -> "moved to another time";
			case "SKIPPED" -> "FAMILY".equals(change.decidedBy()) ? "skipped at the family's request" : "skipped";
			default -> "called off";
		};
	}

	/** "Systolic 128–142 mmHg": one line per metric, lowest to highest over the period. */
	@Override
	protected String vitalSigns(ReportFacts facts) {
		return lines(VitalRange.summarise(facts.vitals()).stream().map(FamilyReportAssembler::range).toList());
	}

	private static String range(VitalRange range) {
		String span = range.isSingleValue()
				? amount(range.lowest())
				: amount(range.lowest()) + "–" + amount(range.highest());
		return withUnit(metricName(range.metric()) + " " + span, range.unit());
	}

	/** A point per day, the day's range: the chart says no more than the ranges beside it. */
	@Override
	protected List<ReportSeries> vitalSeries(ReportFacts facts) {
		return seriesOfDays(facts);
	}

	/** The caregiver's own words, with the day and the caregiver's name. */
	@Override
	protected String observations(ReportFacts facts) {
		List<String> lines = new ArrayList<>();
		for (ObservationFact observation : facts.observations()) {
			lines.add(facts.visit(observation.visitId())
					.map(visit -> dayOf(visit.scheduledStart()) + SEPARATOR + caregiverOf(visit) + ": ")
					.orElse("")
					+ observation.note());
		}
		return lines(lines);
	}

	/** What happened and whether it is over; not who handled it, and not the timeline. */
	@Override
	protected String incidents(ReportFacts facts) {
		return lines(facts.incidents().stream().map(FamilyReportAssembler::incident).toList());
	}

	private static String incident(IncidentFact incident) {
		List<String> parts = new ArrayList<>();
		parts.add(timeOf(incident.reportedAt()));
		parts.add(categoryName(incident.category()));
		if (incident.description() != null && !incident.description().isBlank()) {
			parts.add(incident.description());
		}
		parts.add(incident.isResolved() && incident.resolvedAt() != null
				? "resolved " + timeOf(incident.resolvedAt())
				: "still being followed up");
		return String.join(SEPARATOR, parts);
	}

	/** The words the manager console uses for the same categories, so a family and a manager describe an event alike. */
	private static String categoryName(String category) {
		return switch (category) {
			case "SOS" -> "Emergency call";
			case "MEDICAL" -> "Medical concern";
			case "FALL" -> "Fall reported";
			case "SERVICE" -> "Service problem";
			default -> "Care exception";
		};
	}

	/**
	 * How the elder rated the visits, the reviews the family wrote and how each spot check it
	 * agreed to ended - the conclusion only, not the inspector's notes on the caregiver.
	 */
	@Override
	protected String ratings(ReportFacts facts, ReportMetrics metrics) {
		List<String> lines = new ArrayList<>();
		List<ConfirmationFact> answers = facts.quality().confirmations();
		if (!answers.isEmpty()) {
			long disputed = answers.stream().filter(ConfirmationFact::disputed).count();
			lines.add("The elder confirmed %s%s.".formatted(
					counted(answers.size() - disputed, "visit", "visits"),
					disputed == 0 ? "" : " and disputed " + disputed));
		}
		if (metrics.averageElderRating() != null) {
			lines.add("Average visit rating: %s out of 5 from %s.".formatted(
					amount(metrics.averageElderRating()), counted(metrics.ratingCount(), "rating", "ratings")));
		}
		for (ReviewFact review : facts.quality().reviews()) {
			lines.add(String.join(SEPARATOR,
					"Review of " + describeCaregiver(review.caregiverId(), review.caregiverName()),
					span(review.periodStart(), review.periodEnd()),
					review.overallRating() + " out of 5",
					renewal(review.renewalDecision())));
		}
		for (SpotCheckFact check : facts.quality().spotChecks()) {
			lines.add(String.join(SEPARATOR, "Spot check", timeOf(check.proposedTime()), spotCheckState(check)));
		}
		return lines(lines);
	}

	@Override
	protected String disclaimer() {
		return DISCLAIMER;
	}
}
