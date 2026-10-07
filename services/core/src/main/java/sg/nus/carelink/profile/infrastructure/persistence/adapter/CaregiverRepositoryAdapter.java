package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.repository.CaregiverRepository;
import sg.nus.carelink.profile.infrastructure.persistence.repository.CaregiverJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class CaregiverRepositoryAdapter implements CaregiverRepository {

	private final CaregiverJpaRepository jpa;

	CaregiverRepositoryAdapter(CaregiverJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<Caregiver> findByUserId(Long userId) {
		return jpa.findByUserId(userId).map(CaregiverMapper::toDomain);
	}

	@Override
	public Optional<Caregiver> findById(Long id) {
		return jpa.findById(id).map(CaregiverMapper::toDomain);
	}

	@Override
	public List<Caregiver> findAll() {
		return jpa.findAllByOrderByFullNameAsc().stream().map(CaregiverMapper::toDomain).toList();
	}

	@Override
	public List<Caregiver> findByIds(Set<Long> ids) {
		return jpa.findByIdIn(ids).stream().map(CaregiverMapper::toDomain).toList();
	}

	@Override
	public Caregiver save(Caregiver caregiver) {
		return CaregiverMapper.toDomain(jpa.save(CaregiverMapper.toEntity(caregiver)));
	}
}
