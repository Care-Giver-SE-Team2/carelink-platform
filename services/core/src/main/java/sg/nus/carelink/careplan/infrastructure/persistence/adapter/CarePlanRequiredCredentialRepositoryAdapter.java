package sg.nus.carelink.careplan.infrastructure.persistence.adapter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.careplan.domain.model.CarePlanRequiredCredential;
import sg.nus.carelink.careplan.domain.repository.CarePlanRequiredCredentialRepository;
import sg.nus.carelink.careplan.infrastructure.persistence.entity.CarePlanRequiredCredentialJpaEntity;
import sg.nus.carelink.careplan.infrastructure.persistence.repository.CarePlanRequiredCredentialJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class CarePlanRequiredCredentialRepositoryAdapter implements CarePlanRequiredCredentialRepository {

	private final CarePlanRequiredCredentialJpaRepository jpa;

	CarePlanRequiredCredentialRepositoryAdapter(CarePlanRequiredCredentialJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<CarePlanRequiredCredential> findById(CarePlanRequiredCredential.Id id) {
		return jpa.findById(new CarePlanRequiredCredentialJpaEntity.Id(id.carePlanId(), id.credentialTypeId())).map(CarePlanRequiredCredentialMapper::toDomain);
	}

	@Override
	public List<CarePlanRequiredCredential> findByCarePlanIds(Collection<Long> carePlanIds) {
		if (carePlanIds.isEmpty()) {
			return List.of();
		}
		return jpa.findByIdCarePlanIdIn(carePlanIds).stream().map(CarePlanRequiredCredentialMapper::toDomain).toList();
	}

	@Override
	public CarePlanRequiredCredential save(CarePlanRequiredCredential carePlanRequiredCredential) {
		return CarePlanRequiredCredentialMapper.toDomain(jpa.save(CarePlanRequiredCredentialMapper.toEntity(carePlanRequiredCredential)));
	}
}
