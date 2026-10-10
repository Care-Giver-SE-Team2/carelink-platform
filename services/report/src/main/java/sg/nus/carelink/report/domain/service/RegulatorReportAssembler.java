package sg.nus.carelink.report.domain.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 * The regulator's version: the complete record, with the people in it reduced to numbers.
 *
 * <p>The use case calls this the audit version and asks it to keep the full operation trail
 * ("审计版保留完整操作留痕，用于应对举证要求"). So it has every reading and every step of
 * every incident's timeline. What it leaves out is identity and prose: the elder appears by
 * number with an age band instead of a name and age, caregivers as {@code caregiver #id},
 * staff on an incident timeline as their role, and anything written in someone's own words -
 * caregivers' notes, the elder's comments, the family's reviews, the inspector's findings - is
 * counted or reduced to its conclusion rather than quoted, because free text is where names
 * and opinions end up. The contract left this reader's rules "pending their owners"; these
 * are the manager side's decision for this release (manager B, UC-MG07) and are recorded as
 * such.
 */
final class RegulatorReportAssembler extends ReportAssembler {

	/** How the incident module labels the scheduled scan on a timeline. */
	private static final String SYSTEM_ACTOR = "system";

	/** How the incident module labels a caregiver who reported an incident: "caregiver:" and a user id. */
	private static final String CAREGIVER_ACTOR = "caregiver:";

	@Override
	protected String describeCaregiver(Long caregiverId, String caregiverName) {
		return "caregiver #" + caregiverId;
	}

	/** The elder by number and age band, how they live, the plan, the primary caregiver by number - no name, no notes. */
	@Override
	protected String aboutTheElder(ReportFacts facts) {
		ElderProfile elder = facts.elder();
		List<String> who = new ArrayList<>(List.of("Elder #" + facts.elderId()));
		elder.ageBandOn(facts.period().end()).ifPresent(band -> who.add("aged " + band));
		if (elder.mobilityLevel() != null) {
			who.add(mobility(elder.mobilityLevel()));
		}
		if (elder.livesAlone() != null) {
			who.add(elder.livesAlone() ? "lives alone" : "lives with others");
		}
		return lines(List.of(
				String.join(SEPARATOR, who),
				planLine(elder),
				elder.hasPrimaryCaregiver()
						? "Primary caregiver: " + describeCaregiver(elder.primaryCaregiverId(), null)
						: "No primary caregiver assigned"));
	}

	/** Each caregiver's workload by number; roster changes and value-added requests counted by how they ended. */
	@Override
	protected String serviceDetail(ReportFacts facts) {
		List<String> lines = new ArrayList<>(workload(facts));
		List<RosterChangeFact> changes = facts.changes().rosterChanges();
		if (!changes.isEmpty()) {
			lines.add("Absence cover: %s, %s.".formatted(
					counted(changes.size(), "visit", "visits"),
					tallied(changes, change -> change.outcome() == null ? "open" : change.outcome().toLowerCase(Locale.ROOT))));
		}
		List<ValueAddedFact> requests = facts.changes().valueAdded();
		if (!requests.isEmpty()) {
			lines.add("Value-added requests: %d, %s.".formatted(
					requests.size(), tallied(requests, request -> requestState(request.status()))));
		}
		return lines(lines);
	}

	@Override
	protected String vitalSigns(ReportFacts facts) {
		return eachReading(facts);
	}

	@Override
	protected List<ReportSeries> vitalSeries(ReportFacts facts) {
		return seriesOfReadings(facts);
	}

	/** How many notes, on how many visits - never what they say. */
	@Override
	protected String observations(ReportFacts facts) {
		long visits = facts.observations().stream().map(ObservationFact::visitId).distinct().count();
		return "%s recorded across %s. The notes themselves are not included in this version.".formatted(
				counted(facts.observations().size(), "observation", "observations"),
				counted(visits, "visit", "visits"));
	}

	/** Each incident's facts and outcome, then every step of its timeline with the actor's role. */
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
			if (incident.resolvedAt() != null) {
				parts.add("resolved " + timeOf(incident.resolvedAt()));
			}
			incident.outcome().ifPresent(outcome -> parts.add("outcome " + outcome));
			lines.add(String.join(SEPARATOR, parts));

			for (IncidentFact.Step step : incident.timeline()) {
				lines.add("  " + String.join(SEPARATOR, timeOf(step.occurredAt()), step.action(), role(step.actor())));
			}
		}
		return lines(lines);
	}

	/**
	 * Who acted, without saying who they are. A manager's label on the timeline is their name
	 * and username; here it becomes "staff". The scan stays "system", and a caregiver keeps the
	 * account number the incident module wrote, which identifies a record, not a person.
	 */
	private static String role(String actor) {
		if (actor == null || actor.isBlank()) {
			return "not recorded";
		}
		if (SYSTEM_ACTOR.equals(actor)) {
			return SYSTEM_ACTOR;
		}
		if (actor.startsWith(CAREGIVER_ACTOR)) {
			return "caregiver (user #" + actor.substring(CAREGIVER_ACTOR.length()) + ")";
		}
		return "staff";
	}

	/** The elder's answers and the reviews counted, each spot check by its conclusion - no comments, notes or findings. */
	@Override
	protected String ratings(ReportFacts facts, ReportMetrics metrics) {
		List<String> lines = new ArrayList<>();
		List<ConfirmationFact> answers = facts.quality().confirmations();
		if (!answers.isEmpty()) {
			long disputed = answers.stream().filter(ConfirmationFact::disputed).count();
			lines.add("Elder's answers: %d confirmed, %d disputed%s. Comments are not included in this version.".formatted(
					answers.size() - disputed, disputed,
					metrics.averageElderRating() == null ? "" : "; average rating %s out of 5 from %s".formatted(
							amount(metrics.averageElderRating()), counted(metrics.ratingCount(), "rating", "ratings"))));
		}
		List<ReviewFact> reviews = facts.quality().reviews();
		if (!reviews.isEmpty()) {
			lines.add("Periodic reviews: %d, overall %s; decisions: %s.".formatted(
					reviews.size(),
					reviews.stream().map(review -> String.valueOf(review.overallRating())).collect(Collectors.joining(", ")),
					tallied(reviews, review -> renewal(review.renewalDecision()))));
		}
		for (SpotCheckFact check : facts.quality().spotChecks()) {
			lines.add(String.join(SEPARATOR,
					"Spot check " + check.id(),
					timeOf(check.proposedTime()),
					caregiver(check.caregiverId(), check.caregiverName()),
					spotCheckState(check)));
		}
		return lines(lines);
	}

	/** "1 replaced, 2 skipped": how many of each, in the order each first appears. */
	private static <T> String tallied(List<T> items, Function<T, String> label) {
		Map<String, Long> counts = new LinkedHashMap<>();
		for (T item : items) {
			counts.merge(label.apply(item), 1L, Long::sum);
		}
		return counts.entrySet().stream()
				.map(entry -> entry.getValue() + " " + entry.getKey())
				.collect(Collectors.joining(", "));
	}

	@Override
	protected String disclaimer() {
		return null;
	}
}
