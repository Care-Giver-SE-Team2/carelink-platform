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

	IntakeApplication save(IntakeApplication intakeApplication);

	/** Applications still waiting for an answer (submitted or under review), newest first. */
	List<IntakeApplication> findPending();

	/** Applications at a postcode still waiting for an answer, for the one-elder-one-record check. */
	List<IntakeApplication> findPendingByPostalCode(String postalCode);

	/**
	 * Reads an application and holds it until the transaction ends, so two managers answering it
	 * at once are taken one after the other and the second sees the first's answer.
	 */
	Optional<IntakeApplication> findByIdForUpdate(Long id);

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
