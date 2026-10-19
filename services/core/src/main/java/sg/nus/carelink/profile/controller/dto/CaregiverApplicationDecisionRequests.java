package sg.nus.carelink.profile.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Bodies of the manager's decision on a caregiver application. {@code expectedVersion} is the
 * application's version as the manager saw it; {@code clientRequestId} is a fresh UUID per attempt,
 * so a retried request replays its first result instead of deciding twice.
 */
public final class CaregiverApplicationDecisionRequests {

	private CaregiverApplicationDecisionRequests() {
	}

	/** {@code message} is optional; when given, the applicant sees it with the decision. */
	public record Approve(@Size(max = 500) String message, @NotNull @PositiveOrZero Long expectedVersion,
			@NotBlank String clientRequestId) {
	}

	public record Reject(@NotBlank @Size(max = 500) String reason, @NotNull @PositiveOrZero Long expectedVersion,
			@NotBlank String clientRequestId) {
	}
}
