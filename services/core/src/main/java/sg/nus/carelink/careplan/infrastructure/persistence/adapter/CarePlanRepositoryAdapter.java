package sg.nus.carelink.careplan.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.domain.repository.CarePlanRepository;
import sg.nus.carelink.careplan.infrastructure.persistence.repository.CarePlanJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class CarePlanRepositoryAdapter implements CarePlanRepository {

	private final CarePlanJpaRepository jpa;

	CarePlanRepositoryAdapter(CarePlanJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<CarePlan> findById(Long id) {
		return jpa.findById(id).map(CarePlanMapper::toDomain);
	}

	@Override
	public Optional<CarePlan> findLatestByElderId(Long elderId) {
		return jpa.findFirstByElderIdOrderByVersionDesc(elderId).map(CarePlanMapper::toDomain);
	}

	@Override
	public List<CarePlan> findByElderId(Long elderId) {
		return jpa.findByElderIdOrderByVersionAsc(elderId).stream().map(CarePlanMapper::toDomain).toList();
	}

	@Override
	public List<Long> findElderIdsWithIssuedPlans() {
		return jpa.findElderIdsWithIssuedPlans();
	}

	@Override
	public CarePlan save(CarePlan carePlan) {
		return CarePlanMapper.toDomain(jpa.save(CarePlanMapper.toEntity(carePlan)));
	}
}
