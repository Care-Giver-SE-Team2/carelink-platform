package sg.nus.carelink.incident.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import sg.nus.carelink.incident.domain.model.ContactAttempt;
import sg.nus.carelink.incident.domain.model.Incident;

/**
 * The request bodies of UC-MG05, kept together because they are four small shapes that are
 * only ever read by one controller.
 *
 * <p>Validation lives on these records rather than in the service: a missing resolution
 * note is a malformed request (400), while resolving an incident nobody has taken over is a
 * broken rule (409). Keeping the two apart is what lets the client tell "I sent the wrong
 * thing" from "you cannot do that yet".
 */
public final class IncidentRequests {

	private IncidentRequests() {
	}

	/** Body of {@code POST /api/incidents/{id}/escalate}. */
	public record Escalate(
			@Size(max = 255, message = "reason must be at most 255 characters")
			String reason) {
	}

	/** Body of {@code POST /api/incidents/{id}/contact-attempts}. */
	public record ContactAttemptBody(
			@NotNull(message = "channel is required")
			ContactAttempt.Channel channel,

			@NotNull(message = "outcome is required")
			ContactAttempt.Outcome outcome,

			@Size(max = 255, message = "note must be at most 255 characters")
			String note) {

		public ContactAttempt toDomain() {
			return new ContactAttempt(channel, outcome, note);
		}
	}

	/** Body of {@code POST /api/incidents/{id}/playbook}. */
	public record ApplyPlaybook(
			@NotBlank(message = "playbookCode is required")
			String playbookCode) {
	}

	/** Body of {@code POST /api/incidents/{id}/severity}. */
	public record ChangeSeverity(
			@NotNull(message = "severity is required")
			Incident.Severity severity,

			@Size(max = 255, message = "reason must be at most 255 characters")
			String reason) {
	}

	/** Body of {@code POST /api/incidents/{id}/resolve}. */
	public record Resolve(
			@NotBlank(message = "resolutionNote is required")
			@Size(max = 500, message = "resolutionNote must be at most 500 characters")
			String resolutionNote,

			Outcome outcome) {

		/** How the incident ended. Feeds the periodic report, so it is a closed set. */
		public enum Outcome {
			HANDLED_ON_SITE, REFERRED_TO_MEDICAL_CARE, FALSE_ALARM
		}
	}
}
