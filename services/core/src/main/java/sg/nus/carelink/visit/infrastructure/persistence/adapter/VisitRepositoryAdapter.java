package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.repository.VisitRepository;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitJpaEntity;
import sg.nus.carelink.visit.infrastructure.persistence.repository.VisitJpaRepository;

/**
 * Implements the visit domain repository using Spring Data.
 */
@Repository
class VisitRepositoryAdapter
        implements VisitRepository {

    private final VisitJpaRepository jpa;

    VisitRepositoryAdapter(
            VisitJpaRepository jpa) {

        this.jpa = jpa;
    }

    @Override
    public List<Visit> findAssigned(Long caregiverId, java.time.LocalDateTime from, java.time.LocalDateTime until) {
        return jpa.findByCaregiverIdAndScheduledStartGreaterThanEqualAndScheduledStartLessThanOrderByScheduledStartAscIdAsc(
                caregiverId, from, until).stream().map(VisitMapper::toDomain).toList();
    }

    @Override
    public Optional<Visit> findById(Long id) {
        return jpa.findById(id)
                .map(VisitMapper::toDomain);
    }

    @Override
    public List<Visit> findScheduledBetween(java.time.LocalDateTime from, java.time.LocalDateTime until) {
        return jpa.findByScheduledStartGreaterThanEqualAndScheduledStartLessThanOrderByScheduledStartAscIdAsc(
                from, until).stream().map(VisitMapper::toDomain).toList();
    }

    @Override
    public List<Visit> findCompletedByElderId(
            Long elderId) {

        return jpa
                .findByElderIdAndStatusOrderByScheduledStartDesc(
                        elderId,
                        VisitJpaEntity.Status.COMPLETED
                )
                .stream()
                .map(VisitMapper::toDomain)
                .toList();
    }

    @Override
    public List<Visit> findByCarePlanIdStartingFrom(Long carePlanId, java.time.LocalDateTime from) {
        return jpa.findByCarePlanIdAndScheduledStartGreaterThanEqual(carePlanId, from)
                .stream().map(VisitMapper::toDomain).toList();
    }

    @Override
    public List<Visit> findUnassignedScheduledStartingBetween(java.time.LocalDateTime from, java.time.LocalDateTime until) {
        return jpa.findByCaregiverIdIsNullAndStatusAndScheduledStartGreaterThanEqualAndScheduledStartLessThan(
                VisitJpaEntity.Status.SCHEDULED, from, until).stream().map(VisitMapper::toDomain).toList();
    }

    @Override
    public Visit save(Visit visit) {
        return VisitMapper.toDomain(
                jpa.save(
                        VisitMapper.toEntity(visit)
                )
        );
    }
}
