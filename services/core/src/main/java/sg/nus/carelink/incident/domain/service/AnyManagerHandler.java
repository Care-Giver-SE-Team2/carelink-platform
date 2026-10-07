package sg.nus.carelink.incident.domain.service;

import java.util.Objects;
import java.util.Optional;

import sg.nus.carelink.incident.domain.model.EscalationTier;
import sg.nus.carelink.incident.domain.model.Responder;
import sg.nus.carelink.incident.domain.repository.ManagerDirectory;

/**
 * Third link: any enabled manager.
 *
 * <p>The guaranteed fallback, and the reason an incident can never be left with nobody: it
 * offers the first manager who has not already held this incident, so every escalation moves
 * to somebody new and a first-ever incident for an elder still gets a responder.
 *
 * <p>Only when every manager has already had it does this link step aside too, and the
 * terminal tier takes over — the "升级链已用尽" branch, which is a real outcome rather than
 * an oversight.
 */
public final class AnyManagerHandler extends ChainedResponderHandler {

	private final ManagerDirectory directory;

	public AnyManagerHandler(ManagerDirectory directory, ResponderHandler next) {
		super(next);
		this.directory = Objects.requireNonNull(directory, "directory");
	}

	@Override
	public EscalationTier tier() {
		return EscalationTier.ANY_MANAGER;
	}

	@Override
	protected Optional<Responder> candidate(EscalationRequest request) {
		return directory.allManagers().stream()
				.filter(manager -> !request.hasAlreadyHeld(manager))
				.findFirst();
	}

	@Override
	protected String unavailableReason() {
		return "every manager has already held this incident";
	}
}
