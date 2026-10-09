package sg.nus.carelink.visit.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.visit.domain.model.VisitStateTransition;

/**
 * Port for visit_state_transition: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.VisitStateTransitionRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface VisitStateTransitionRepository {

	Optional<VisitStateTransition> findById(Long id);

	/** Applied history for one visit, ordered by occurredAt then id ascending. */
	List<VisitStateTransition> findAppliedByVisitId(Long visitId);

	VisitStateTransition save(VisitStateTransition visitStateTransition);
}
