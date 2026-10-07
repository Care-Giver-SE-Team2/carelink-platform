package sg.nus.carelink.incident.domain.service;

import java.util.Objects;
import java.util.Optional;

import sg.nus.carelink.incident.domain.model.EscalationTier;
import sg.nus.carelink.incident.domain.model.Responder;
import sg.nus.carelink.incident.domain.repository.ManagerDirectory;

/**
 * First link: whoever is already named on the incident keeps it.
 *
 * <p>Without this link every re-assembly of the chain would move a live incident to
 * whoever happens to be on shift now, which would break the rule that responsibility
 * accumulates rather than bounces. It matters in one case in particular: raising the
 * severity re-assembles the chain, and the manager who is already handling the incident
 * must not lose it because of that.
 *
 * <p>After a timeout the previous responder is in {@code alreadyHeld}, so this link steps
 * aside and the incident genuinely moves on.
 */
public final class AssignedResponderHandler extends ChainedResponderHandler {

	private final ManagerDirectory directory;

	public AssignedResponderHandler(ManagerDirectory directory, ResponderHandler next) {
		super(next);
		this.directory = Objects.requireNonNull(directory, "directory");
	}

	@Override
	public EscalationTier tier() {
		return EscalationTier.ASSIGNED_RESPONDER;
	}

	@Override
	protected Optional<Responder> candidate(EscalationRequest request) {
		Long current = request.incident().responderUserId();
		return current == null ? Optional.empty() : directory.responderById(current);
	}

	@Override
	protected String unavailableReason() {
		return "no responder named on the incident yet";
	}
}
