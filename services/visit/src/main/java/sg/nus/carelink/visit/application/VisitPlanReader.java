package sg.nus.carelink.visit.application;

import java.util.List;

/** The published care plan a visit was made from, as core's care plans keep it. */
public interface VisitPlanReader {

	/**
	 * The plan's tasks for this elder.
	 *
	 * @throws sg.nus.carelink.shared.error.ResourceNotFound when there is no such plan
	 * @throws sg.nus.carelink.shared.error.BusinessRuleViolation VISIT_PLAN_UNAVAILABLE when it is not the
	 *         elder's published plan
	 */
	Snapshot read(Long planId, Long elderId);

	record Task(Long id, String name, String evidenceType) {
	}

	record Snapshot(Long id, Integer version, List<Task> tasks) {
	}

}
