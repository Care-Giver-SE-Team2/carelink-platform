package sg.nus.carelink.incident.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import sg.nus.carelink.incident.domain.model.SpotCheck;

/** Request bodies of UC-MG08. Shape only; whether a step is allowed is the SpotCheck's business. */
public final class SpotCheckRequests {

	private SpotCheckRequests() {
	}

	/** Body of {@code POST /api/spot-checks}: which visit, and why. */
	public record Request(
			@NotNull(message = "visitId is required")
			Long visitId,

			@NotBlank(message = "purpose is required")
			@Size(max = 255, message = "purpose must be at most 255 characters")
			String purpose) {
	}

	/** Body of {@code POST /api/spot-checks/{id}/decision}: the family agrees, or declines and says why. */
	public record Decision(
			@NotNull(message = "approve is required")
			Boolean approve,

			@Size(max = 255, message = "reason must be at most 255 characters")
			String reason) {
	}

	/** Body of {@code POST /api/spot-checks/{id}/conclusion}. */
	public record Conclusion(
			@NotNull(message = "result is required")
			SpotCheck.Result result,

			@Size(max = 500, message = "notes must be at most 500 characters")
			String notes) {
	}

	/** Body of {@code POST /api/spot-checks/{id}/no-show}. */
	public record NoShow(
			@Size(max = 500, message = "notes must be at most 500 characters")
			String notes) {
	}

	/** Body of {@code POST /api/spot-checks/{id}/move}: another visit of the same elder. */
	public record Move(
			@NotNull(message = "visitId is required")
			Long visitId) {
	}

	/** Body of {@code POST /api/spot-checks/{id}/withdrawal}. */
	public record Withdrawal(
			@NotBlank(message = "reason is required")
			@Size(max = 255, message = "reason must be at most 255 characters")
			String reason) {
	}

	/** Body of {@code POST /api/spot-checks/{id}/response}: the caregiver answers the conclusion. */
	public record Response(
			@NotBlank(message = "response is required")
			@Size(max = 500, message = "response must be at most 500 characters")
			String response) {
	}
}
