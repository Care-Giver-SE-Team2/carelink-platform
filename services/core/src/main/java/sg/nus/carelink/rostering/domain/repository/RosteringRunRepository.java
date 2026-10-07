package sg.nus.carelink.rostering.domain.repository;

import java.util.Optional;

import sg.nus.carelink.rostering.domain.model.RosteringRun;

/**
 * Port for rostering_run: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.RosteringRunRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface RosteringRunRepository {

	Optional<RosteringRun> findById(Long id);

	RosteringRun save(RosteringRun rosteringRun);
}
