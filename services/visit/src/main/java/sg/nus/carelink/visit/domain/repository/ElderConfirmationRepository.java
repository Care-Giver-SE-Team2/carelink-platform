package sg.nus.carelink.visit.domain.repository;

import java.util.Optional;

import sg.nus.carelink.visit.domain.model.ElderConfirmation;

/**
 * Port for elder_confirmation persistence.
 */
public interface ElderConfirmationRepository {

    Optional<ElderConfirmation> findById(Long id);

    Optional<ElderConfirmation> findByVisitId(Long visitId);

    boolean existsByVisitId(Long visitId);

    ElderConfirmation save(
            ElderConfirmation elderConfirmation
    );
}