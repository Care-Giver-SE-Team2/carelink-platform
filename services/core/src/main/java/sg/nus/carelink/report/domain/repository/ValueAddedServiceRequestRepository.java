package sg.nus.carelink.report.domain.repository;

import java.util.Optional;

import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;

/**
 * Port for value_added_service_request: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.ValueAddedServiceRequestRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface ValueAddedServiceRequestRepository {

	Optional<ValueAddedServiceRequest> findById(Long id);

	ValueAddedServiceRequest save(ValueAddedServiceRequest valueAddedServiceRequest);
}
