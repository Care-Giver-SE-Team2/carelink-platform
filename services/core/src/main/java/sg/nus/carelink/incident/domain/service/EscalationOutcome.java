package sg.nus.carelink.incident.domain.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import sg.nus.carelink.incident.domain.model.EscalationTier;
import sg.nus.carelink.incident.domain.model.Responder;

/**
 * What came back from walking the escalation chain: either a level took the incident, or
 * every level declined and the terminal tier was reached.
 *
 * <p>Both answers carry {@code skipped}, so the caller can write the reasons to the
 * timeline. An escalation that silently lands on the third responder is not auditable; one
 * that records "duty manager off shift, first alternate already timed out" is.
 */
public record EscalationOutcome(
		boolean assigned,
		EscalationTier tier,
		Responder responder,
		Duration countdown,
		List<EscalationRequest.SkippedTier> skipped) {

	public EscalationOutcome {
		Objects.requireNonNull(tier, "tier");
		skipped = List.copyOf(Objects.requireNonNull(skipped, "skipped"));
		if (assigned) {
			Objects.requireNonNull(responder, "responder");
			Objects.requireNonNull(countdown, "countdown");
		}
	}

	public static EscalationOutcome assignedTo(
			EscalationTier tier,
			Responder responder,
			Duration countdown,
			List<EscalationRequest.SkippedTier> skipped) {

		return new EscalationOutcome(true, tier, responder, countdown, skipped);
	}

	/** Nobody left to try. The incident becomes UNRESOLVED_ESCALATED and the family is told. */
	public static EscalationOutcome exhausted(List<EscalationRequest.SkippedTier> skipped) {
		return new EscalationOutcome(false, EscalationTier.FAMILY_ESCALATION, null, Duration.ZERO, skipped);
	}

	/** The deadline the newly assigned responder has to meet. */
	public LocalDateTime respondBy(LocalDateTime from) {
		return from.plus(countdown);
	}

	/** One line summarising the route taken, for the incident timeline. */
	public String describeRoute() {
		String route = skipped.stream()
				.map(entry -> "%s (%s)".formatted(entry.tier().label(), entry.reason()))
				.reduce((left, right) -> left + "; " + right)
				.orElse("no level skipped");
		return assigned
				? "assigned to %s at %s after %s".formatted(responder.displayName(), tier.label(), route)
				: "chain exhausted after %s".formatted(route);
	}
}
