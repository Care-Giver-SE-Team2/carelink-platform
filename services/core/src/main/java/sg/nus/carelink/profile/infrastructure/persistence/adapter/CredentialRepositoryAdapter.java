package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import sg.nus.carelink.profile.domain.model.Credential;
import sg.nus.carelink.profile.domain.repository.CredentialRepository;
import sg.nus.carelink.profile.infrastructure.persistence.repository.CredentialJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class CredentialRepositoryAdapter implements CredentialRepository {

	private final CredentialJpaRepository jpa;

	CredentialRepositoryAdapter(CredentialJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<Credential> findById(Long id) {
		return jpa.findById(id).map(CredentialMapper::toDomain);
	}

	@Override
	public List<Credential> findByCaregiverId(Long caregiverId) {
		return jpa.findByCaregiverIdOrderByCredentialTypeIdAscIdAsc(caregiverId).stream()
				.map(CredentialMapper::toDomain).toList();
	}

	@Override
	public List<Credential> findAll() {
		return jpa.findAll(Sort.by("id")).stream().map(CredentialMapper::toDomain).toList();
	}

	@Override
	public Credential save(Credential credential) {
		return CredentialMapper.toDomain(jpa.save(CredentialMapper.toEntity(credential)));
	}
}
