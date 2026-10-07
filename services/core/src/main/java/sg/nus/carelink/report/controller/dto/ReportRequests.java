package sg.nus.carelink.report.controller.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The request bodies of UC-MG07, kept together because they are two small shapes read by one
 * controller.
 *
 * <p>Validation lives here rather than in the service, as in the incident module: a period
 * that ends before it starts or an empty correction is a malformed request (400), which the
 * client can fix by sending something else, not a broken rule (409). The domain still checks
 * both - nothing reaches it any other way today, and that should not be what keeps it safe.
 */
public final class ReportRequests {

	private ReportRequests() {
	}

	/** Body of {@code POST /api/reports/generate}. */
	public record Generate(
			Long elderId,

			@NotNull(message = "periodStart is required")
			LocalDate periodStart,

			@NotNull(message = "periodEnd is required")
			LocalDate periodEnd) {

		/** Reported against the field name {@code periodInOrder}. */
		@AssertTrue(message = "periodEnd must not be before periodStart")
		public boolean isPeriodInOrder() {
			return periodStart == null || periodEnd == null || !periodEnd.isBefore(periodStart);
		}
	}

	/** Body of {@code POST /api/reports/{id}/amendments}. */
	public record Amend(
			@NotBlank(message = "note is required")
			@Size(max = 1000, message = "note must be at most 1000 characters")
			String note) {
	}
}
