package sg.nus.carelink.report.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

/**
 * The institution's own version: everything, with the names left in ("主管版完整").
 *
 * <p>The only reader for whom nothing is withheld. The elder's profile with the medical notes,
 * caregivers by name and number with the hours each worked, every reading with its
 * out-of-range flag, the notes with the visit and task they belong to, each incident's
 * timeline with who did what and the conclusion it was closed with, every change to the
 * roster, every answer, review and spot-check finding in the words it was given in - the record
 * a manager needs when a family or a regulator asks a question about the week.
 */
final class InternalReportAssembler extends ReportAssembler {

	@Override
	protected String describeCaregiver(Long caregiverId, String caregiverName) {
		return "%s (caregiver #%d)".formatted(caregiverName == null ? "unnamed" : caregiverName, caregiverId);
	}

	/** Who the elder is, as the profile records it, with the plan in force and the primary caregiver. */
	@Override
	protected String aboutTheElder(ReportFacts facts) {
		ElderProfile elder = facts.elder();
		List<String> who = new ArrayList<>();
		addIfPresent(who, elder.fullName());
		elder.ageOn(facts.period().end()).ifPresent(age -> who.add(age + " years old"));
		if (elder.gender() != null) {
			who.add(elder.gender().toLowerCase(Locale.ROOT));
		}
		if (elder.mobilityLevel() != null) {
			who.add(mobility(elder.mobilityLevel()));
		}
		if (elder.livesAlone() != null) {
			who.add(elder.livesAlone() ? "lives alone" : "lives with others");
		}

		List<String> lines = new ArrayList<>();
		if (!who.isEmpty()) {
			lines.add(String.join(SEPARATOR, who));
		}
		if (elder.medicalNotes() != null && !elder.medicalNotes().isBlank()) {
			lines.add("Medical notes: " + elder.medicalNotes().strip());
		}
		lines.add(planLine(elder));
		lines.add(elder.hasPrimaryCaregiver()
				? "Primary caregiver: " + describeCaregiver(elder.primaryCaregiverId(), elder.primaryCaregiverName())
				: "No primary caregiver assigned");
		return lines(lines);
	}

	/** Each caregiver's workload, then every roster change and value-added request in full. */
	@Override
	protected String serviceDetail(ReportFacts facts) {
		List<String> lines = new ArrayList<>(workload(facts));
		for (RosterChangeFact change : facts.changes().rosterChanges()) {
			List<String> parts = new ArrayList<>(List.of(
					"Absence cover",
					timeOf(change.visitStart()),
					"visit " + change.visitId(),
					caregiver(change.originalCaregiverId(), change.originalCaregiverName()) + " away",
					settled(change)));
			if (change.decidedBy() != null) {
				parts.add("decided by " + decider(change.decidedBy()));
			}
			lines.add(String.join(SEPARATOR, parts));
		}
		for (ValueAddedFact request : facts.changes().valueAdded()) {
			lines.add(String.join(SEPARATOR,
					"Value-added request " + request.id(),
					request.service(),
					timeOf(request.requestedFor()),
					requestState(request.status())
							+ (request.visitId() == null ? "" : " as visit " + request.visitId())));
		}
		return lines(lines);
	}

	private String settled(RosterChangeFact change) {
		if (change.outcome() == null) {
			return "UNCOVERED".equals(change.status()) ? "uncovered" : "awaiting the family";
		}
		return switch (change.outcome()) {
			case "REPLACED" -> "replaced by " + caregiver(change.assignedCaregiverId(), change.assignedCaregiverName());
			case "RESCHEDULED" -> change.hasReplacement()
					? "rescheduled with " + caregiver(change.assignedCaregiverId(), change.assignedCaregiverName())
					: "rescheduled";
			case "SKIPPED" -> "skipped";
			default -> "withdrawn";
		};
	}

	private static String decider(String decidedBy) {
		return switch (decidedBy) {
			case "FAMILY" -> "the family";
			case "DEFAULT_PLAN" -> "the default plan";
			default -> "a manager";
		};
	}

	@Override
	protected String vitalSigns(ReportFacts facts) {
		return eachReading(facts);
	}

	@Override
	protected List<ReportSeries> vitalSeries(ReportFacts facts) {
		return seriesOfReadings(facts);
	}

	@Override
	protected String observations(ReportFacts facts) {
		List<String> lines = new ArrayList<>();
		for (ObservationFact observation : facts.observations()) {
			String task = observation.task() == null || observation.task().isBlank() ? "General" : observation.task();
			lines.add(facts.visit(observation.visitId())
					.map(visit -> String.join(SEPARATOR,
							timeOf(visit.scheduledStart()), "visit " + visit.id(), caregiverOf(visit), task))
					.orElse("visit " + observation.visitId() + SEPARATOR + task)
					+ ": " + observation.note());
		}
		return lines(lines);
	}

	/** Each incident in full: the timeline with its actors, and the conclusion it was closed with. */
	@Override
	protected String incidents(ReportFacts facts) {
		List<String> lines = new ArrayList<>();
		for (IncidentFact incident : facts.incidents()) {
			List<String> parts = new ArrayList<>(List.of(
					timeOf(incident.reportedAt()),
					"incident " + incident.id(),
					incident.category(),
					"severity " + incident.severity(),
					incident.status()));
			if (incident.description() != null && !incident.description().isBlank()) {
				parts.add(incident.description());
			}
			lines.add(String.join(SEPARATOR, parts));

			for (IncidentFact.Step step : incident.timeline()) {
				List<String> entry = new ArrayList<>(List.of(
						timeOf(step.occurredAt()),
						step.action(),
						step.actor() == null ? "not recorded" : step.actor()));
				if (step.detail() != null && !step.detail().isBlank()) {
					entry.add(step.detail());
				}
				lines.add("  " + String.join(SEPARATOR, entry));
			}
			incident.resolution().ifPresent(resolution -> lines.add("  Resolution: " + resolution));
		}
		return lines(lines);
	}

	/** Every answer with its comment, every review with its scores and notes, every finding and the reply to it. */
	@Override
	protected String ratings(ReportFacts facts, ReportMetrics metrics) {
		List<String> lines = new ArrayList<>();
		for (ConfirmationFact answer : facts.quality().confirmations()) {
			lines.add(facts.visit(answer.visitId())
					.map(visit -> String.join(SEPARATOR, dayOf(visit.scheduledStart()), "visit " + visit.id(), caregiverOf(visit)))
					.orElse("visit " + answer.visitId())
					+ SEPARATOR + answered(answer));
		}
		for (ReviewFact review : facts.quality().reviews()) {
			List<String> scores = new ArrayList<>(List.of("overall " + review.overallRating()));
			if (review.punctualityScore() != null) {
				scores.add("punctuality " + review.punctualityScore());
			}
			if (review.careQualityScore() != null) {
				scores.add("care quality " + review.careQualityScore());
			}
			lines.add(String.join(SEPARATOR,
					"Review of " + caregiver(review.caregiverId(), review.caregiverName()),
					span(review.periodStart(), review.periodEnd()),
					String.join(", ", scores),
					renewal(review.renewalDecision()))
					+ (review.notes() == null || review.notes().isBlank() ? "" : ": " + review.notes().strip()));
		}
		for (SpotCheckFact check : facts.quality().spotChecks()) {
			List<String> parts = new ArrayList<>(List.of(
					"Spot check " + check.id(),
					timeOf(check.proposedTime()),
					caregiver(check.caregiverId(), check.caregiverName()),
					spotCheckState(check)));
			if (check.finding() != null && !check.finding().isBlank()) {
				parts.add("finding: " + check.finding().strip());
			}
			if (check.caregiverResponse() != null && !check.caregiverResponse().isBlank()) {
				parts.add("caregiver's response: " + check.caregiverResponse().strip());
			}
			lines.add(String.join(SEPARATOR, parts));
		}
		return lines(lines);
	}

	private static String answered(ConfirmationFact answer) {
		String verdict = answer.disputed() ? "disputed" : "confirmed";
		String rating = answer.isRated() ? ", rated " + answer.rating() : "";
		String comment = answer.comment() == null || answer.comment().isBlank() ? "" : ": " + answer.comment().strip();
		return verdict + rating + comment;
	}

	@Override
	protected String disclaimer() {
		return null;
	}
}
