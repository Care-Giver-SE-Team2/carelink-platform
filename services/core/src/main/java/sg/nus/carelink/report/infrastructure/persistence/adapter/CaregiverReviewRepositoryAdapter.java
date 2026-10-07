package sg.nus.carelink.report.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.report.domain.model.CaregiverReview;
import sg.nus.carelink.report.domain.repository.CaregiverReviewRepository;
import sg.nus.carelink.report.infrastructure.persistence.repository.CaregiverReviewJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
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
	public CaregiverReview save(CaregiverReview caregiverReview) {
		return CaregiverReviewMapper.toDomain(jpa.save(CaregiverReviewMapper.toEntity(caregiverReview)));
	}
}
