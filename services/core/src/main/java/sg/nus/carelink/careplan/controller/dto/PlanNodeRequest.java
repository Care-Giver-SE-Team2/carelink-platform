package sg.nus.carelink.careplan.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

import sg.nus.carelink.careplan.domain.model.CarePlanNode;

/**
 * One task the manager is publishing, with its weekly effort ({@code visits}). {@code groupName}
 * is a display-only label the UI uses to cluster tasks under a heading; it carries no validation
 * or roll-up rule.
 */
public record PlanNodeRequest(
		String groupName,
		@NotBlank @Size(max = 150) String name,
		List<@Valid VisitRequest> visits,
		CarePlanNode.EvidenceType evidenceType) {
}
