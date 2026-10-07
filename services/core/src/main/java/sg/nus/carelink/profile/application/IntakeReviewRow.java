package sg.nus.carelink.profile.application;

import java.util.List;

import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.service.IntakeScreening;

/**
 * One pending application as the manager reviews it: what the family submitted, who they are,
 * the sector the elder would fall in (null when unknown) and the screening checks.
 */
public record IntakeReviewRow(IntakeApplication application, Applicant applicant, String sector,
		List<IntakeScreening.Check> checks) {

	/** The family member who applied. {@code username} and {@code phone} may be null. */
	public record Applicant(String fullName, String username, String phone) {
	}
}
