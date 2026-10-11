package sg.nus.carelink.visit.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.visit.domain.model.Visit;

/**
 * Port for visit persistence.
 */
public interface VisitRepository {
	java.util.List<Visit> findAssigned(Long caregiverId, java.time.LocalDateTime from, java.time.LocalDateTime until);

    Optional<Visit> findById(Long id);

    /** Every visit starting in [from, until), whoever it is assigned to, earliest first. */
    List<Visit> findScheduledBetween(java.time.LocalDateTime from, java.time.LocalDateTime until);

    /**
     * Visits that have been completed by the caregiver and are therefore
     * candidates for EL01 elder confirmation.
     */
    List<Visit> findCompletedByElderId(Long elderId);

    /** Every visit generated from one care plan version that starts at or after {@code from}. */
    List<Visit> findByCarePlanIdStartingFrom(Long carePlanId, java.time.LocalDateTime from);

    /** SCHEDULED visits with no caregiver that start in [from, until). */
    List<Visit> findUnassignedScheduledStartingBetween(java.time.LocalDateTime from, java.time.LocalDateTime until);

    Visit save(Visit visit);
}