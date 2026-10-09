package sg.nus.carelink.incident.domain.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import sg.nus.carelink.incident.domain.model.EscalationTier;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.Responder;

/**
 * What travels down the escalation chain: the incident that needs a responder, the moment
 * it is being routed, and what the levels above already declined.
 *
 * <p>It is immutable, and each handler that passes the request on produces a new one with
 * its own tier recorded as skipped. That is what lets the final outcome explain why nobody
 * took the incident, instead of only reporting that nobody did.
 *
 * <p>{@code alreadyHeld} is what stops an escalation handing the incident straight back to
 * the person who just let the countdown run out.
 */
public record EscalationRequest(
		Incident incident,
		LocalDateTime at,
		EscalationPolicy policy,
		Set<Long> alreadyHeld,
		List<EscalationRequest.SkippedTier> skipped) {

	public EscalationRequest {
		Objects.requireNonNull(incident, "incident");
		Objects.requireNonNull(at, "at");
		Objects.requireNonNull(policy, "policy");
		alreadyHeld = Set.copyOf(Objects.requireNonNull(alreadyHeld, "alreadyHeld"));
		skipped = List.copyOf(Objects.requireNonNull(skipped, "skipped"));
	}

	/** A first routing: nobody has held this incident yet. */
	public static EscalationRequest routing(Incident incident, LocalDateTime at, EscalationPolicy policy) {
		return new EscalationRequest(incident, at, policy, Set.of(), List.of());
	}

	/**
	 * A re-routing after a timeout. The responder who just timed out is excluded, so the
	 * chain cannot offer the incident back to them.
	 */
	public static EscalationRequest afterTimeout(
			Incident incident, LocalDateTime at, EscalationPolicy policy, Set<Long> previousResponders) {

		Set<Long> held = new LinkedHashSet<>(previousResponders);
		if (incident.responderUserId() != null) {
			held.add(incident.responderUserId());
		}
		return new EscalationRequest(incident, at, policy, held, List.of());
	}

	/** Records that this tier could not take the incident, and passes the request on. */
	public EscalationRequest skipping(EscalationTier tier, String reason) {
		List<SkippedTier> extended = new ArrayList<>(skipped);
		extended.add(new SkippedTier(tier, reason));
		return new EscalationRequest(incident, at, policy, alreadyHeld, extended);
	}

	/**
	 * Records that this tier is filled by {@code responder} and moves on.
	 *
	 * <p>Used only while surveying the chain for the API. The responder joins
	 * {@code alreadyHeld} so that one person who qualifies for two tiers is listed once,
	 * at the first tier they qualify for, instead of appearing twice in the plan.
	 */
	public EscalationRequest listing(EscalationTier tier, Responder responder) {
		Set<Long> held = new LinkedHashSet<>(alreadyHeld);
		held.add(responder.userId());
		List<SkippedTier> extended = new ArrayList<>(skipped);
		extended.add(new SkippedTier(tier, "listed at position " + position()));
		return new EscalationRequest(incident, at, policy, held, extended);
	}

	/** Position of the level now being considered. The first tier tried is position 1. */
	public int position() {
		return skipped.size() + 1;
	}

	/**
	 * How many responders have already turned this incident down, plus one.
	 *
	 * <p>What the countdown is scaled by, and deliberately not {@link #position()}: a tier
	 * that nobody fills has not cost the elder any time, so stepping over it must not extend
	 * the next responder's deadline. Only a person who actually held the incident and let it
	 * expire does that.
	 */
	public int depth() {
		return alreadyHeld.size() + 1;
	}

	public boolean hasAlreadyHeld(Responder responder) {
		return responder != null && alreadyHeld.contains(responder.userId());
	}

	public Incident.Severity severity() {
		return incident.severity();
	}

	/** Why a tier did not take the incident. Carried through to the timeline and the API. */
	public record SkippedTier(EscalationTier tier, String reason) {

		public SkippedTier {
			Objects.requireNonNull(tier, "tier");
			Objects.requireNonNull(reason, "reason");
		}
	}
}
