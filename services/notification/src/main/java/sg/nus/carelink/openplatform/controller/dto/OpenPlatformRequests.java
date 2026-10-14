package sg.nus.carelink.openplatform.controller.dto;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What the Open Platform's callers send (docs/api/open-platform.yaml), checked before the service sees it. */
public final class OpenPlatformRequests {

	private OpenPlatformRequests() {
	}

	/** {@code ApplicationCreate}. The webhook URL's scheme and host are checked by the service. */
	public record Apply(
			@NotBlank @Size(max = 200) String organisationName,
			@NotBlank @Size(max = 100) String contactName,
			@NotBlank @Email @Size(max = 254) String contactEmail,
			@NotBlank @Size(max = 30) String contactPhone,
			@NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 100) String> services,
			@NotBlank @Size(max = 2048) String webhookUrl,
			@NotNull @Size(min = 8, max = 256) String password,
			@NotNull @AssertTrue(message = "Must accept the provider terms") Boolean agreedToTerms) {

		/** The password is not printed with the request. */
		@Override
		public String toString() {
			return "Apply[" + organisationName + ", " + contactEmail + "]";
		}
	}

	/** {@code WebhookSettingsUpdate}: the job events only; {@code ping} is not subscribed to. */
	public record WebhookSettingsUpdate(
			@NotBlank @Size(max = 2048) String url,
			@NotNull Set<@Pattern(regexp = "job\\.dispatched|job\\.cancelled",
					message = "Must be job.dispatched or job.cancelled") String> events) {
	}

	/** A manager's rejection; the provider is shown the reason. */
	public record Reject(@NotBlank @Size(max = 500) String reason) {
	}
}
