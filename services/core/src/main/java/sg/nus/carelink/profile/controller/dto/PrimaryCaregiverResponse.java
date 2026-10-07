package sg.nus.carelink.profile.controller.dto;

import java.time.LocalDateTime;

import sg.nus.carelink.profile.application.PrimaryCaregiverSummary;

/** The elder's primary caregiver after an assignment. */
public record PrimaryCaregiverResponse(Long caregiverId, String fullName, LocalDateTime assignedAt) {

	public static PrimaryCaregiverResponse from(PrimaryCaregiverSummary summary) {
		return new PrimaryCaregiverResponse(summary.caregiverId(), summary.fullName(), summary.assignedAt());
	}
}
