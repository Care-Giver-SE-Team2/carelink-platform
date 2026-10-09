package sg.nus.carelink.report.infrastructure.persistence.adapter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.report.domain.model.CaregiverReview;
import sg.nus.carelink.report.domain.repository.CaregiverReviewRepository;
import sg.nus.carelink.report.infrastructure.persistence.repository.CaregiverReviewJpaRepository;

/** Implements the caregiver-review domain port with Spring Data. */
@Repository
class CaregiverReviewRepositoryAdapter implements CaregiverReviewRepository {

    private final CaregiverReviewJpaRepository jpa;

    CaregiverReviewRepositoryAdapter(CaregiverReviewJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<CaregiverReview> findById(Long id) {
        return jpa.findById(id).map(CaregiverReviewMapper::toDomain);
    }

    @Override
    public List<CaregiverReview> findByElderId(Long elderId) {
        return jpa.findByElderIdOrderByCreatedAtDescIdDesc(elderId).stream()
                .map(CaregiverReviewMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsForPeriod(
            Long familyMemberId,
            Long elderId,
            Long caregiverId,
            LocalDate periodStart,
            LocalDate periodEnd) {
        return jpa.existsByFamilyMemberIdAndElderIdAndCaregiverIdAndPeriodStartAndPeriodEnd(
                familyMemberId, elderId, caregiverId, periodStart, periodEnd);
    }

    @Override
    public CaregiverReview save(CaregiverReview caregiverReview) {
        return CaregiverReviewMapper.toDomain(
                jpa.save(CaregiverReviewMapper.toEntity(caregiverReview)));
    }
}
