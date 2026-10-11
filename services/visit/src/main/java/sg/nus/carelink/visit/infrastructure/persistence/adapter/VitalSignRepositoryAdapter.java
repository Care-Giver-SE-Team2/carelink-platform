package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.visit.domain.model.VitalSign;
import sg.nus.carelink.visit.domain.repository.VitalSignRepository;
import sg.nus.carelink.visit.infrastructure.persistence.repository.VitalSignJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class VitalSignRepositoryAdapter implements VitalSignRepository {

	private final VitalSignJpaRepository jpa;

	VitalSignRepositoryAdapter(VitalSignJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<VitalSign> findById(Long id) {
		return jpa.findById(id).map(VitalSignMapper::toDomain);
	}

	@Override
	public VitalSign save(VitalSign vitalSign) {
		return VitalSignMapper.toDomain(jpa.save(VitalSignMapper.toEntity(vitalSign)));
	}
}
