package sg.nus.carelink.careplan.application;

import java.util.List;

import sg.nus.carelink.careplan.domain.model.CarePlanNode;

/**
 * One task being published, unpacked from the controller's request DTO into plain
 * application-layer terms (CreateCarePlanRequest -> primitive args is the same pattern).
 * groupName is a display-only label; it plays no part in validation or computation.
 */
public record PlanNodeInput(
		String groupName,
		String name,
		List<VisitInput> visits,
		CarePlanNode.EvidenceType evidenceType) {
}
