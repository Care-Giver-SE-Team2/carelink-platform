package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.visit.domain.model.ElderConfirmation;
import sg.nus.carelink.visit.domain.repository.ElderConfirmationRepository;
import sg.nus.carelink.visit.infrastructure.persistence.repository.ElderConfirmationJpaRepository;

/**
 * Implements the elder-confirmation domain repository using Spring Data.
 */
@Repository
class ElderConfirmationRepositoryAdapter
        implements ElderConfirmationRepository {

    private final ElderConfirmationJpaRepository jpa;

    ElderConfirmationRepositoryAdapter(
            ElderConfirmationJpaRepository jpa) {

        this.jpa = jpa;
    }

    @Override
    public Optional<ElderConfirmation> findById(
            Long id) {

        return jpa.findById(id)
                .map(ElderConfirmationMapper::toDomain);
    }

    @Override
    public Optional<ElderConfirmation> findByVisitId(
            Long visitId) {

        return jpa.findByVisitId(visitId)
                .map(ElderConfirmationMapper::toDomain);
    }

    @Override
    public boolean existsByVisitId(
            Long visitId) {

        return jpa.existsByVisitId(
                visitId
        );
    }

    @Override
    public ElderConfirmation save(
            ElderConfirmation confirmation) {

        return ElderConfirmationMapper.toDomain(
                jpa.save(
                        ElderConfirmationMapper.toEntity(
                                confirmation
                        )
                )
        );
    }
}