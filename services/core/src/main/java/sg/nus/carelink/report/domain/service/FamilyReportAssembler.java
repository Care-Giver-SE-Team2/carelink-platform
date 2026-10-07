package sg.nus.carelink.report.domain.service;

import java.util.ArrayList;
import java.util.List;

import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.ObservationFact;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.VisitFact;
import sg.nus.carelink.report.domain.model.VitalRange;

/**
 * The family's version: what happened to their relative, in words, without the working.
 *
 * <p>Follows the family rules the contract spells out for this report: caregivers by name
 * only, no staff-performance data, vital signs as ranges rather than raw measurements, and a
 * medical disclaimer that is always there. Incidents are described - what, when, whether it
 * is over - but not who on the staff handled them or how quickly.
 */
final class FamilyReportAssembler extends ReportAssembler {

	/** UC-MG07: "家属版必须包含免责声明，明确其不构成医疗建议". Fixed text, never assembled. */
	private static final String DISCLAIMER = "This summary is prepared from care records for information only "
			+ "and does not constitute medical advice.";

	@Override
	protected String describeCaregiver(VisitFact visit) {
		return visit.caregiverName() == null ? "a caregiver" : visit.caregiverName();
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

	@Override
	protected String disclaimer() {
		return DISCLAIMER;
	}
}
