package sg.nus.carelink.rostering.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import sg.nus.carelink.rostering.domain.model.RosteringCandidateCheck;

/**
 * Port for rostering_candidate_check: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.RosteringCandidateCheckRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface RosteringCandidateCheckRepository {

	Optional<RosteringCandidateCheck> findById(Long id);

	RosteringCandidateCheck save(RosteringCandidateCheck rosteringCandidateCheck);

	/** The rule results behind these candidates, in id order. */
	List<RosteringCandidateCheck> findByCandidateIds(Collection<Long> rosteringCandidateIds);
}
