package sg.nus.carelink.report.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRepository;
import sg.nus.carelink.report.infrastructure.persistence.repository.ValueAddedServiceJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class ValueAddedServiceRepositoryAdapter implements ValueAddedServiceRepository {

	private final ValueAddedServiceJpaRepository jpa;

	ValueAddedServiceRepositoryAdapter(ValueAddedServiceJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<ValueAddedService> findById(Long id) {
		return jpa.findById(id).map(ValueAddedServiceMapper::toDomain);
	}

	@Override
	public ValueAddedService save(ValueAddedService valueAddedService) {
		return ValueAddedServiceMapper.toDomain(jpa.save(ValueAddedServiceMapper.toEntity(valueAddedService)));
	}
}
