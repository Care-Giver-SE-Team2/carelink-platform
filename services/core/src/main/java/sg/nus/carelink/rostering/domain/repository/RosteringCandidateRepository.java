package sg.nus.carelink.rostering.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.rostering.domain.model.RosteringCandidate;

/**
 * Port for rostering_candidate: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.RosteringCandidateRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface RosteringCandidateRepository {

	Optional<RosteringCandidate> findById(Long id);

	RosteringCandidate save(RosteringCandidate rosteringCandidate);

	/** Everybody one run considered for one visit: suggestions by rank, then the excluded. */
	List<RosteringCandidate> findByRunAndVisit(Long rosteringRunId, Long visitId);

	/**
	 * Files a run's candidates for one visit under another: a move's search is made for the visit before
	 * it moves, and belongs to the visit that replaces it once visit has made that one.
	 */
	void moveToVisit(Long rosteringRunId, Long fromVisitId, Long toVisitId);
}
