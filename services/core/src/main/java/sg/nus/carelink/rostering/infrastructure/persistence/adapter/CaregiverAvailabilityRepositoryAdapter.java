package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.rostering.domain.model.CaregiverAvailability;
import sg.nus.carelink.rostering.domain.repository.CaregiverAvailabilityRepository;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.CaregiverAvailabilityJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class CaregiverAvailabilityRepositoryAdapter implements CaregiverAvailabilityRepository {

	private final CaregiverAvailabilityJpaRepository jpa;

	CaregiverAvailabilityRepositoryAdapter(CaregiverAvailabilityJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<CaregiverAvailability> findById(Long id) {
		return jpa.findById(id).map(CaregiverAvailabilityMapper::toDomain);
	}

	@Override
	public CaregiverAvailability save(CaregiverAvailability caregiverAvailability) {
		return CaregiverAvailabilityMapper.toDomain(jpa.save(CaregiverAvailabilityMapper.toEntity(caregiverAvailability)));
	}
}
