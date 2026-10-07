package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.model.IntakeApplicationPage;
import sg.nus.carelink.profile.domain.repository.IntakeApplicationRepository;
import sg.nus.carelink.profile.infrastructure.persistence.entity.IntakeApplicationJpaEntity;
import sg.nus.carelink.profile.infrastructure.persistence.repository.IntakeApplicationJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class IntakeApplicationRepositoryAdapter implements IntakeApplicationRepository {

	private static final List<IntakeApplicationJpaEntity.Status> PENDING =
			List.of(IntakeApplicationJpaEntity.Status.SUBMITTED, IntakeApplicationJpaEntity.Status.UNDER_REVIEW);

	private final IntakeApplicationJpaRepository jpa;

	IntakeApplicationRepositoryAdapter(IntakeApplicationJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<IntakeApplication> findById(Long id) {
		return jpa.findById(id).map(IntakeApplicationMapper::toDomain);
	}

	@Override
	public IntakeApplication save(IntakeApplication intakeApplication) {
		return IntakeApplicationMapper.toDomain(jpa.save(IntakeApplicationMapper.toEntity(intakeApplication)));
	}

	@Override
	public List<IntakeApplication> findPending() {
		return jpa.findByStatusNewestFirst(PENDING).stream().map(IntakeApplicationMapper::toDomain).toList();
	}

	@Override
	public List<IntakeApplication> findPendingByPostalCode(String postalCode) {
		return jpa.findByPostalCodeAndStatusIn(postalCode, PENDING).stream().map(IntakeApplicationMapper::toDomain)
				.toList();
	}

	@Override
	public Optional<IntakeApplication> findByIdForUpdate(Long id) {
		return jpa.findByIdForUpdate(id).map(IntakeApplicationMapper::toDomain);
	}

	@Override
	public IntakeApplicationPage findForApplicant(Long familyMemberId, IntakeApplication.Status status,
			int page, int size) {
		var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
		var entityStatus = status == null ? null : IntakeApplicationJpaEntity.Status.valueOf(status.name());
		long total = jpa.countForApplicant(familyMemberId, entityStatus);
		// Return out-of-range pages before converting their offset to JPA's integer limit.
		if (pageable.getOffset() >= total) {
			return new IntakeApplicationPage(List.of(), page, size, total);
		}
		var result = jpa.findForApplicant(familyMemberId, entityStatus, pageable);
		return new IntakeApplicationPage(result.stream().map(IntakeApplicationMapper::toDomain).toList(),
				page, size, total);
	}
}
