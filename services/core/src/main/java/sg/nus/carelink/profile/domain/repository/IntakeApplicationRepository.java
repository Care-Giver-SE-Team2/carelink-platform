package sg.nus.carelink.profile.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.model.IntakeApplicationPage;

/**
 * Port for intake_application: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.IntakeApplicationRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface IntakeApplicationRepository {

	Optional<IntakeApplication> findById(Long id);

	/** The approved application(s) that created this elder's record — what the family first asked for. */
	List<IntakeApplication> findApprovedByElderId(Long elderId);

	/**
	 * Find applications after filtering by owner and optional status, ordered by creation time then id descending.
	 *
	 * @param familyMemberId Owner of the applications
	 * @param status Optional status filter; null includes all statuses
	 * @param page Zero-based page number
	 * @param size Page size
	 * @return Matching applications and the total count before pagination
	 *
	 * @author Wang Zhili
	 */
	IntakeApplicationPage findForApplicant(Long familyMemberId, IntakeApplication.Status status, int page, int size);
}
