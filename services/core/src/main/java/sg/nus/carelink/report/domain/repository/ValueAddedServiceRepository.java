package sg.nus.carelink.report.domain.repository;

import java.util.Optional;

import sg.nus.carelink.report.domain.model.ValueAddedService;

/**
 * Port for value_added_service: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.ValueAddedServiceRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface ValueAddedServiceRepository {

	Optional<ValueAddedService> findById(Long id);

	ValueAddedService save(ValueAddedService valueAddedService);
}
