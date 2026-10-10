package sg.nus.carelink.report.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;

/** Persistence port for the request shared by UC-EL02 and UC-FM08. */
public interface ValueAddedServiceRequestRepository {
    Optional<ValueAddedServiceRequest> findById(Long id);
    List<ValueAddedServiceRequest> findByElderId(Long elderId);
    /** Every request, newest first: the manager's overview. */
    List<ValueAddedServiceRequest> findAll();
    List<ValueAddedServiceRequest> findByStatus(ValueAddedServiceRequest.Status status);
    ValueAddedServiceRequest save(ValueAddedServiceRequest request);
}
