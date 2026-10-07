package sg.nus.carelink.incident.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One rung of an escalation chain: a tier, the person who fills it right now, and how long
 * that person has before responsibility moves on.
 *
 * <p>The countdown is per level, not per incident. The use case states it plainly: "每一级
 * 的倒计时时长可以不同，由严重度与层级共同决定" — a high-severity incident gives the duty
 * manager minutes and the tier above it rather longer, because the point of the second
 * level is to catch a missed alert, not to race the first one.
 */
public record EscalationLevel(
		int position,
		EscalationTier tier,
		Responder responder,
		Duration countdown,
		EscalationLevel.State state,
		LocalDateTime respondBy) {

	public EscalationLevel {
		Objects.requireNonNull(tier, "tier");
		Objects.requireNonNull(countdown, "countdown");
		Objects.requireNonNull(state, "state");
		if (position < 1) {
			throw new IllegalArgumentException("position starts at 1, got " + position);
		}
	}

	/** A level that has somebody in it and has not been reached yet. */
	public static EscalationLevel pending(int position, EscalationTier tier, Responder responder, Duration countdown) {
		return new EscalationLevel(position, tier, responder, countdown, State.PENDING, null);
	}

	/** A tier nobody was available for. It stays in the chain so the reason is visible. */
	public static EscalationLevel skipped(int position, EscalationTier tier, Duration countdown) {
		return new EscalationLevel(position, tier, null, countdown, State.SKIPPED_UNAVAILABLE, null);
	}

	/** The terminal level: no responder, no countdown, reached only when everything above timed out. */
	public static EscalationLevel terminal(int position) {
		return new EscalationLevel(
				position, EscalationTier.FAMILY_ESCALATION, null, Duration.ZERO, State.PENDING, null);
	}

	/** Marks this level as the one currently holding the incident, and fixes its deadline. */
	public EscalationLevel takeCurrentFrom(LocalDateTime now) {
		return new EscalationLevel(position, tier, responder, countdown, State.CURRENT, now.plus(countdown));
	}

	public EscalationLevel timedOut() {
		return new EscalationLevel(position, tier, responder, countdown, State.TIMED_OUT, respondBy);
	}

	public EscalationLevel claimed() {
		return new EscalationLevel(position, tier, responder, countdown, State.CLAIMED, respondBy);
	}

	/** A level can only take the incident if somebody actually fills it. */
	public boolean isFillable() {
		return responder != null;
	}

	public Long responderUserId() {
		return responder == null ? null : responder.userId();
	}

	public enum State {
		PENDING, CURRENT, TIMED_OUT, SKIPPED_UNAVAILABLE, CLAIMED
	}
}
