package sg.nus.carelink.incident.domain.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The standard response a manager falls back on when the family cannot be reached.
 *
 * <p>The use case does not let the manager wait: "家属联络不上：启用标准处置预案先行处置，
 * 不等待家属". So each incident category has one agreed set of steps, and applying it is
 * recorded on the timeline like any other action.
 *
 * <p>An enum rather than a table, on purpose. There are four playbooks, they change at the
 * pace the institution rewrites its procedures, and nobody edits them through the
 * application. A table would buy nothing and cost a migration, a JPA entity, a repository
 * and a seed script. If the institution ever wants to edit them in the product, that is
 * the moment to promote this to a table — not before.
 */
public enum Playbook {

	SOS_IMMEDIATE(
			"PB-SOS",
			Incident.Category.SOS,
			"Elder emergency call",
			List.of(
					"Call the elder directly; if there is no answer, treat as unresponsive",
					"Dispatch the nearest caregiver on shift to the address on the incident",
					"Call emergency services if the elder is unresponsive or reports chest pain, breathing "
							+ "difficulty or a head injury",
					"Stay on the incident until a caregiver or paramedic confirms arrival")),

	FALL_CHECK(
			"PB-FALL",
			Incident.Category.FALL,
			"Fall reported",
			List.of(
					"Do not move the elder; confirm consciousness and pain location by phone",
					"Dispatch the nearest caregiver on shift",
					"Escalate to emergency services if there is head impact, loss of consciousness or "
							+ "suspected fracture",
					"Record the outcome and raise the severity if the condition worsens")),

	MEDICAL_REVIEW(
			"PB-MED",
			Incident.Category.MEDICAL,
			"Medical concern",
			List.of(
					"Read the elder's known conditions and current care plan before calling",
					"Confirm medication taken in the last 24 hours with the caregiver on record",
					"Arrange a same-day visit, or refer to the elder's clinic if symptoms are stable",
					"Record what was decided and who it was agreed with")),

	SERVICE_FOLLOW_UP(
			"PB-SVC",
			Incident.Category.SERVICE,
			"Service problem",
			List.of(
					"Confirm what was missed or unsatisfactory with the caregiver who reported it",
					"Arrange a replacement visit within the institution's service window",
					"Record the agreed remedy so the periodic report shows it"));

	private final String code;
	private final Incident.Category category;
	private final String title;
	private final List<String> steps;

	Playbook(String code, Incident.Category category, String title, List<String> steps) {
		this.code = code;
		this.category = category;
		this.title = title;
		this.steps = List.copyOf(steps);
	}

	public String code() {
		return code;
	}

	public Incident.Category category() {
		return category;
	}

	public String title() {
		return title;
	}

	public List<String> steps() {
		return steps;
	}

	/** The playbook the system offers for an incident of this category, if there is one. */
	public static Optional<Playbook> forCategory(Incident.Category category) {
		return Arrays.stream(values())
				.filter(playbook -> playbook.category == category)
				.findFirst();
	}

	public static Optional<Playbook> byCode(String code) {
		return Arrays.stream(values())
				.filter(playbook -> playbook.code.equalsIgnoreCase(code))
				.findFirst();
	}
}
