package sg.nus.carelink.incident.domain.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import sg.nus.carelink.incident.domain.model.EscalationLevel;
import sg.nus.carelink.incident.domain.model.Responder;

/**
 * The half of a chain link that is the same for every tier.
 *
 * <p>Every handler does the same three things in the same order — look for somebody to fill
 * its tier, refuse anyone who has already had the incident, pass the request on with a
 * reason if it cannot help. Only the lookup differs, so that is the single abstract method
 * subclasses fill in. Keeping the walk here means a new tier cannot accidentally forget to
 * record why it stepped aside.
 */
abstract class ChainedResponderHandler implements ResponderHandler {

	/** Why a tier steps aside when the person who fills it has already had this incident. */
	private static final String ALREADY_HELD = "already held this incident";

	private final ResponderHandler next;

	protected ChainedResponderHandler(ResponderHandler next) {
		this.next = Objects.requireNonNull(next, "next: every link has a successor, the last one is the terminal tier");
	}

	/** Who fills this tier at the moment of the request, if anyone. */
	protected abstract Optional<Responder> candidate(EscalationRequest request);

	/** Why this tier could not take the incident, when {@link #candidate} came back empty. */
	protected abstract String unavailableReason();

	@Override
	public final EscalationOutcome handle(EscalationRequest request) {
		Optional<Responder> found = candidate(request);

		if (found.isEmpty()) {
			return next.handle(request.skipping(tier(), unavailableReason()));
		}
		if (request.hasAlreadyHeld(found.get())) {
			return next.handle(request.skipping(tier(), ALREADY_HELD));
		}

		Duration countdown = request.policy().countdownFor(request.severity(), tier(), request.depth());
		return EscalationOutcome.assignedTo(tier(), found.get(), countdown, request.skipped());
	}

	@Override
	public final List<EscalationLevel> survey(EscalationRequest request) {
		int position = request.position();
		Optional<Responder> found = candidate(request);
		boolean usable = found.isPresent() && !request.hasAlreadyHeld(found.get());
		Duration countdown = request.policy().countdownFor(request.severity(), tier(), request.depth());

		List<EscalationLevel> levels = new ArrayList<>();
		levels.add(usable
				? EscalationLevel.pending(position, tier(), found.get(), countdown)
				: EscalationLevel.skipped(position, tier(), countdown));

		EscalationRequest onwards;
		if (usable) {
			onwards = request.listing(tier(), found.get());
		}
		else {
			String reason = found.isEmpty() ? unavailableReason() : ALREADY_HELD;
			onwards = request.skipping(tier(), reason);
		}
		levels.addAll(next.survey(onwards));
		return List.copyOf(levels);
	}
}
