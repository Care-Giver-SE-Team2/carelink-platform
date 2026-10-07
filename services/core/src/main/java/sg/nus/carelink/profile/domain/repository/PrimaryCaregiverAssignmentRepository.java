package sg.nus.carelink.profile.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import sg.nus.carelink.profile.domain.model.PrimaryCaregiverAssignment;

/**
 * Port for elder_primary_caregiver. Implemented by
 * infrastructure.persistence.adapter.PrimaryCaregiverAssignmentRepositoryAdapter.
 */
public interface PrimaryCaregiverAssignmentRepository {

	Optional<PrimaryCaregiverAssignment> findByElderId(Long elderId);

	List<PrimaryCaregiverAssignment> findByElderIds(Set<Long> elderIds);

	/** Inserts, or replaces the elder's existing assignment. */
	PrimaryCaregiverAssignment save(PrimaryCaregiverAssignment assignment);

	void deleteByElderId(Long elderId);
}
