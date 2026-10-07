package sg.nus.carelink.rostering.controller.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import sg.nus.carelink.rostering.application.FamilyChoice;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.RosteringRun;

/** Request bodies of UC-MG04 and the caregiver's side of UC-CG02. Shape only; the rules are in the domain. */
public final class AbsenceRequests {

	private AbsenceRequests() {
	}

	/** Body of {@code POST /api/absences}: a manager records a caregiver's absence. */
	public record Record(
			@NotNull(message = "caregiverId is required")
			Long caregiverId,

			AbsenceReport.Type type,

			@NotNull(message = "startDate is required")
			LocalDate startDate,

			@NotNull(message = "endDate is required")
			LocalDate endDate,

			@Size(max = 255, message = "reason must be at most 255 characters")
			String reason) {
	}

	/** Body of {@code POST /api/caregivers/me/absences}: a caregiver asks for leave. */
	public record Request(
			AbsenceReport.Type type,

			@NotNull(message = "startDate is required")
			LocalDate startDate,

			@NotNull(message = "endDate is required")
			LocalDate endDate,

			@Size(max = 255, message = "reason must be at most 255 characters")
			String reason) {
	}

	/** Body of {@code POST /api/absences/{id}/rerostering-runs}; no body ranks by continuity. */
	public record Reroster(RosteringRun.Objective objective) {
	}

	/** Body of {@code POST /api/roster-changes/{id}/decision}. */
	public record Decision(
			@NotNull(message = "choice is required")
			FamilyChoice.Kind choice,

			Long caregiverId,

			LocalDateTime newStart) {

		public FamilyChoice toChoice() {
			return new FamilyChoice(choice, caregiverId, newStart);
		}
	}
}
