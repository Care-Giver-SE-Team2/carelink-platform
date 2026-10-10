package sg.nus.carelink.visit.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.visit.domain.model.VisitAssignment;

/**
 * Port for visit_assignment: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.VisitAssignmentRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface VisitAssignmentRepository {

	Optional<VisitAssignment> findById(Long id);

	VisitAssignment save(VisitAssignment visitAssignment);

	/** The visit's current assignment, if any row records one. */
	Optional<VisitAssignment> findActiveByVisitId(Long visitId);

	/** Every assignment a visit has had, oldest first. */
	List<VisitAssignment> findByVisitId(Long visitId);
}
