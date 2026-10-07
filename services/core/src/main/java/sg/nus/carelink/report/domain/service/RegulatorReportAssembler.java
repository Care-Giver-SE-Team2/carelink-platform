package sg.nus.carelink.report.domain.service;

import java.util.ArrayList;
import java.util.List;

import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.ObservationFact;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.VisitFact;

/**
 * The regulator's version: the complete record, with the people in it reduced to numbers.
 *
 * <p>The use case calls this the audit version and asks it to keep the full operation trail
 * ("审计版保留完整操作留痕，用于应对举证要求"). So it has every reading and every step of
 * every incident's timeline. What it leaves out is identity and prose: caregivers appear as
 * {@code caregiver #id}, staff on an incident timeline as their role, and the caregivers'
 * notes are counted rather than quoted, because free text is where names and opinions end
 * up. The contract left this reader's rules "pending their owners"; these are the manager
 * side's decision for this release (manager B, UC-MG07) and are recorded as such.
 */
final class RegulatorReportAssembler extends ReportAssembler {

	/** How the incident module labels the scheduled scan on a timeline. */
	private static final String SYSTEM_ACTOR = "system";

	/** How the incident module labels a caregiver who reported an incident: "caregiver:" and a user id. */
	private static final String CAREGIVER_ACTOR = "caregiver:";

	@Override
	protected String describeCaregiver(VisitFact visit) {
		return "caregiver #" + visit.caregiverId();
	}

	@Override
	protected String vitalSigns(ReportFacts facts) {
		return eachReading(facts);
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

	@Override
	protected String disclaimer() {
		return null;
	}
}
