package sg.nus.carelink.incident.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.incident.domain.model.Responder;

/**
 * Who can be handed an incident.
 *
 * <p>Declared here rather than in the identity module because the escalation rules are what
 * need it, and the domain layer is allowed to say what it needs without knowing where the
 * answer comes from. The implementation lives in {@code infrastructure}; a unit test
 * supplies a two-line fake and the whole chain becomes testable without a database.
 *
 * <p>Deliberately says nothing about shifts. CareLink does not roster its managers - the
 * institution has a small team and no on-call rota - so "who is on duty right now" is not a
 * question the system can answer, and the chain does not ask it. What it asks instead is who
 * knows this elder and who is available at all.
 */
public interface ManagerDirectory {

	/** Every enabled manager, ordered by name so the chain is the same on two calls. */
	List<Responder> allManagers();

	/** Display name for a user already named on an incident, so the timeline reads properly. */
	Optional<Responder> responderById(Long userId);
}
