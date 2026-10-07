package sg.nus.carelink.incident.domain.service;

import java.util.List;

import sg.nus.carelink.incident.domain.model.EscalationLevel;
import sg.nus.carelink.incident.domain.model.EscalationTier;

/**
 * The last link. It never assigns anyone: reaching it <em>is</em> the outcome.
 *
 * <p>Having a terminal handler rather than a null check is what keeps
 * {@link ChainedResponderHandler} free of "if there is no successor" branching, and it
 * gives "nobody took the incident" a place to live. The caller turns this outcome into the
 * UNRESOLVED_ESCALATED state, pins the incident to the top-level view and tells the family,
 * exactly as the failure post-condition of UC-MG05 requires.
 *
 * <p>The incident is never closed here. The use case is explicit: "不允许异常在无人处置的
 * 情况下自动关闭".
 */
public final class FamilyEscalationHandler implements ResponderHandler {

	@Override
	public EscalationTier tier() {
		return EscalationTier.FAMILY_ESCALATION;
	}

	@Override
	public EscalationOutcome handle(EscalationRequest request) {
		return EscalationOutcome.exhausted(request.skipped());
	}

	@Override
	public List<EscalationLevel> survey(EscalationRequest request) {
		return List.of(EscalationLevel.terminal(request.position()));
	}
}
