package sg.nus.carelink.report.domain.service;

import java.util.ArrayList;
import java.util.List;

import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.ObservationFact;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.VisitFact;

/**
 * The institution's own version: everything, with the names left in ("主管版完整").
 *
 * <p>The only reader for whom nothing is withheld. Caregivers by name and number, every
 * reading with its out-of-range flag, the notes with the visit and task they belong to, and
 * each incident's timeline with who did what and the conclusion it was closed with - the
 * record a manager needs when a family or a regulator asks a question about the week.
 */
final class InternalReportAssembler extends ReportAssembler {

	@Override
	protected String describeCaregiver(VisitFact visit) {
		return "%s (caregiver #%d)".formatted(
				visit.caregiverName() == null ? "unnamed" : visit.caregiverName(), visit.caregiverId());
	}

	@Override
	protected String vitalSigns(ReportFacts facts) {
		return eachReading(facts);
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

	@Override
	protected String disclaimer() {
		return null;
	}
}
