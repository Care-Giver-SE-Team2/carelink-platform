package sg.nus.carelink.careplan.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.careplan.domain.model.CarePlanNode;
import sg.nus.carelink.careplan.domain.repository.CarePlanNodeRepository;
import sg.nus.carelink.careplan.infrastructure.persistence.repository.CarePlanNodeJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class CarePlanNodeRepositoryAdapter implements CarePlanNodeRepository {

	private final CarePlanNodeJpaRepository jpa;

	CarePlanNodeRepositoryAdapter(CarePlanNodeJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<CarePlanNode> findById(Long id) {
		return jpa.findById(id).map(CarePlanNodeMapper::toDomain);
	}

	@Override
	public List<CarePlanNode> findByCarePlanId(Long carePlanId) {
		return jpa.findByCarePlanId(carePlanId).stream().map(CarePlanNodeMapper::toDomain).toList();
	}

	@Override
	public CarePlanNode save(CarePlanNode carePlanNode) {
		return CarePlanNodeMapper.toDomain(jpa.save(CarePlanNodeMapper.toEntity(carePlanNode)));
	}

	@Override
	public void deleteByCarePlanId(Long carePlanId) {
		jpa.deleteByCarePlanId(carePlanId);
	}
}
