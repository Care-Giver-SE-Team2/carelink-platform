package sg.nus.carelink.profile.controller.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import sg.nus.carelink.profile.application.ElderSummary;

/**
 * Row shape for the Elders index (UC-MG01 step one): the elder plus its plan status and primary
 * caregiver. The three primaryCaregiver fields are all null while none is assigned.
 */
public record ElderListItemResponse(
		Long id,
		String fullName,
		LocalDate dateOfBirth,
		String address,
		String sector,
		String planStatus,
		Integer planVersion,
		LocalDate nextVisitDate,
		Long primaryCaregiverId,
		String primaryCaregiverName,
		LocalDateTime primaryCaregiverAssignedAt) {

	public static ElderListItemResponse from(ElderSummary summary) {
		var primary = summary.primaryCaregiver();
		return new ElderListItemResponse(
				summary.elder().id(),
				summary.elder().fullName(),
				summary.elder().dateOfBirth(),
				summary.elder().address(),
				summary.elder().sector(),
				summary.planStatus(),
				summary.planVersion(),
				summary.nextVisitDate(),
				primary == null ? null : primary.caregiverId(),
				primary == null ? null : primary.fullName(),
				primary == null ? null : primary.assignedAt());
	}
}
