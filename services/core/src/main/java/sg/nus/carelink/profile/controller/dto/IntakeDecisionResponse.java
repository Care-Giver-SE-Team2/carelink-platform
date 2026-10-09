package sg.nus.carelink.profile.controller.dto;

import sg.nus.carelink.profile.application.IntakeApproval;
import sg.nus.carelink.profile.domain.model.IntakeApplication;

/**
 * The answered application: its new status and, once approved, the elder record created from it
 * and the elder's login. {@code elderLogin} is null for a decline; its password is shown once.
 */
public record IntakeDecisionResponse(Long id, IntakeApplication.Status status, Long elderId, ElderLogin elderLogin) {

	public record ElderLogin(String username, String temporaryPassword) {
	}

	public static IntakeDecisionResponse declined(IntakeApplication application) {
		return new IntakeDecisionResponse(application.id(), application.status(), application.elderId(), null);
	}

	public static IntakeDecisionResponse approved(IntakeApproval approval) {
		IntakeApplication application = approval.application();
		return new IntakeDecisionResponse(application.id(), application.status(), application.elderId(),
				new ElderLogin(approval.username(), approval.temporaryPassword()));
	}
}
