package sg.nus.carelink.report.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRequestRepository;
import sg.nus.carelink.report.infrastructure.persistence.repository.ValueAddedServiceRequestJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class ValueAddedServiceRequestRepositoryAdapter implements ValueAddedServiceRequestRepository {

	private final ValueAddedServiceRequestJpaRepository jpa;

	ValueAddedServiceRequestRepositoryAdapter(ValueAddedServiceRequestJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<ValueAddedServiceRequest> findById(Long id) {
		return jpa.findById(id).map(ValueAddedServiceRequestMapper::toDomain);
	}

	@Override
	public ValueAddedServiceRequest save(ValueAddedServiceRequest valueAddedServiceRequest) {
		return ValueAddedServiceRequestMapper.toDomain(jpa.save(ValueAddedServiceRequestMapper.toEntity(valueAddedServiceRequest)));
	}
}
