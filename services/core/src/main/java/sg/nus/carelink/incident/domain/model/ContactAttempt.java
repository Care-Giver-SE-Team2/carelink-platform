package sg.nus.carelink.incident.domain.model;

import java.util.Objects;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * One attempt to reach the family about an incident.
 *
 * <p>Both outcomes are recorded. The use case is specific about this — "被拒绝或超时的接管
 * 尝试同样留痕，不只记录成功的那次" — because the value of the timeline at a later enquiry
 * is that it shows what was tried, not only what worked. A failed call with a reason is
 * evidence; a missing entry is not.
 *
 * <p>There is no {@code contact_attempt} table. An attempt is an event, and events for an
 * incident live in {@code incident_log}, which is append-only.
 */
public record ContactAttempt(Channel channel, Outcome outcome, String note) {

	public ContactAttempt {
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(outcome, "outcome");
		if (outcome == Outcome.NOT_REACHED && (note == null || note.isBlank())) {
			throw new BusinessRuleViolation(
					"CONTACT_REASON_REQUIRED",
					"An attempt that did not reach the family must say why");
		}
	}

	public boolean reachedTheFamily() {
		return outcome == Outcome.REACHED;
	}

	/** The line written to the timeline. */
	public String describe() {
		String base = "%s via %s".formatted(outcome == Outcome.REACHED ? "Reached" : "Did not reach", channel);
		return note == null || note.isBlank() ? base : base + " - " + note;
	}

	public enum Channel {
		PHONE, PUSH, EMAIL
	}

	public enum Outcome {
		REACHED, NOT_REACHED
	}
}
