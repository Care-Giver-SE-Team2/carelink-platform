package sg.nus.carelink.report.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.report.domain.model.ValueAddedService;

/** Persistence port for the value-added service catalogue. */
public interface ValueAddedServiceRepository {
    Optional<ValueAddedService> findById(Long id);
    List<ValueAddedService> findAvailable();
    /** The whole catalogue, unavailable services included: requests made before a service was withdrawn still name it. */
    List<ValueAddedService> findAll();
    /** The service of that name, whatever its case; unavailable ones too, since a name is never used twice. */
    Optional<ValueAddedService> findByName(String name);
    ValueAddedService save(ValueAddedService valueAddedService);
    /** Removes a service no request names; a request's foreign key refuses it otherwise. */
    void delete(Long id);
}
