package sg.nus.carelink.visit.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.visit.infrastructure.persistence.entity.ElderConfirmationJpaEntity;

/**
 * Spring Data repository for elder_confirmation.
 */
public interface ElderConfirmationJpaRepository
        extends JpaRepository<ElderConfirmationJpaEntity, Long> {

    Optional<ElderConfirmationJpaEntity>
            findByVisitId(Long visitId);

    boolean existsByVisitId(Long visitId);
}