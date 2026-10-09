package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.rostering.domain.model.CaregiverPreference;
import sg.nus.carelink.rostering.domain.repository.CaregiverPreferenceRepository;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.CaregiverPreferenceJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class CaregiverPreferenceRepositoryAdapter implements CaregiverPreferenceRepository {

	private final CaregiverPreferenceJpaRepository jpa;

	CaregiverPreferenceRepositoryAdapter(CaregiverPreferenceJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<CaregiverPreference> findById(Long id) {
		return jpa.findById(id).map(CaregiverPreferenceMapper::toDomain);
	}

	@Override
	public CaregiverPreference save(CaregiverPreference caregiverPreference) {
		return CaregiverPreferenceMapper.toDomain(jpa.save(CaregiverPreferenceMapper.toEntity(caregiverPreference)));
	}
}
