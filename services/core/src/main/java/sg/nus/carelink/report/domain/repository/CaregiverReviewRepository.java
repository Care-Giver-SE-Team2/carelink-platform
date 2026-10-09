package sg.nus.carelink.report.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import sg.nus.carelink.report.domain.model.CaregiverReview;

/** Persistence port for FM09 caregiver reviews. */
public interface CaregiverReviewRepository {

    Optional<CaregiverReview> findById(Long id);

    List<CaregiverReview> findByElderId(Long elderId);

    boolean existsForPeriod(
            Long familyMemberId,
            Long elderId,
            Long caregiverId,
            LocalDate periodStart,
            LocalDate periodEnd);

    CaregiverReview save(CaregiverReview caregiverReview);
}
