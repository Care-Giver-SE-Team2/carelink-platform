package sg.nus.carelink.incident.domain.service;

import java.util.Objects;
import java.util.Optional;

import sg.nus.carelink.incident.domain.model.EscalationTier;
import sg.nus.carelink.incident.domain.model.Responder;
import sg.nus.carelink.incident.domain.repository.IncidentRepository;
import sg.nus.carelink.incident.domain.repository.ManagerDirectory;

/**
 * Second link: the manager who handled this elder's last incident.
 *
 * <p>Continuity, and the reason the chain cannot be a fixed list of roles. The right second
 * responder is not a rank, it is whoever already knows that this elder lives alone, refuses
 * to go to hospital, and fell twice last month. That answer is different for every elder and
 * changes every time an incident is closed, so it can only be worked out when the incident
 * is being routed.
 *
 * <p>The elder's first ever incident has no such manager, and this link simply steps aside -
 * recorded, like every other step-aside, so the timeline explains the route.
 */
public final class FamiliarManagerHandler extends ChainedResponderHandler {

	private final IncidentRepository incidents;
	private final ManagerDirectory directory;

	public FamiliarManagerHandler(
			IncidentRepository incidents, ManagerDirectory directory, ResponderHandler next) {

		super(next);
		this.incidents = Objects.requireNonNull(incidents, "incidents");
		this.directory = Objects.requireNonNull(directory, "directory");
	}

	@Override
	public EscalationTier tier() {
		return EscalationTier.FAMILIAR_MANAGER;
	}

	@Override
	protected Optional<Responder> candidate(EscalationRequest request) {
		return incidents
				.lastResponderForElder(request.incident().elderId(), request.incident().id())
				.flatMap(directory::responderById);
	}

	@Override
	protected String unavailableReason() {
		return "no manager has handled this elder before";
	}
}
