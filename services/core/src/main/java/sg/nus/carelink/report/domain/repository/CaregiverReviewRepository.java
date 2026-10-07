package sg.nus.carelink.report.domain.repository;

import java.util.Optional;

import sg.nus.carelink.report.domain.model.CaregiverReview;

/**
 * Port for caregiver_review: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.CaregiverReviewRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface CaregiverReviewRepository {

	Optional<CaregiverReview> findById(Long id);

	CaregiverReview save(CaregiverReview caregiverReview);
}
