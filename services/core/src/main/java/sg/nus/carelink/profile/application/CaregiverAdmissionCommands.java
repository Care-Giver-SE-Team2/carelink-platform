package sg.nus.carelink.profile.application;

import sg.nus.carelink.profile.domain.model.CaregiverApplication.View;

/** Admission only: no certificate publication, roster changes or account recovery. */
public interface CaregiverAdmissionCommands {
    View decide(String managerUsername, Long applicationId, boolean approve,
            String reason, long expectedVersion, String clientRequestId);
}
