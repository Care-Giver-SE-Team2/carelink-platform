package sg.nus.carelink.rostering.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.rostering.domain.model.RosteringConstraint;

/**
 * Port for rostering_constraint: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.RosteringConstraintRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface RosteringConstraintRepository {

	Optional<RosteringConstraint> findById(Long id);

	RosteringConstraint save(RosteringConstraint rosteringConstraint);

	/** The whole rule set, switched-off rules included, in id order. */
	List<RosteringConstraint> findAll();
}
