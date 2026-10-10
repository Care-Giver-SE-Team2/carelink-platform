package sg.nus.carelink.report.controller.dto;

import sg.nus.carelink.profile.application.CaregiverPublicProfile;

public record CaregiverReviewCaregiverResponse(Long id, String fullName) {
    public static CaregiverReviewCaregiverResponse from(CaregiverPublicProfile caregiver) {
        return new CaregiverReviewCaregiverResponse(caregiver.id(), caregiver.fullName());
    }
}
