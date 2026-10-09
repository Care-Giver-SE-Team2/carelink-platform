package sg.nus.carelink.incident.domain.model;

import java.util.Objects;

/**
 * A person the escalation chain can hand an incident to.
 *
 * <p>Deliberately not the identity module's {@code AppUser}: the incident module needs an
 * id and something to show in the timeline, and nothing else. Keeping it to that means the
 * escalation rules can be unit tested without dragging in accounts, roles or passwords.
 */
public record Responder(Long userId, String displayName) {

	public Responder {
		Objects.requireNonNull(userId, "userId");
		if (displayName == null || displayName.isBlank()) {
			displayName = "user " + userId;
		}
	}
}
