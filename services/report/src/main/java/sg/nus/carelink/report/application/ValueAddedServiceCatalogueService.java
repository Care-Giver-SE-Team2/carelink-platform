package sg.nus.carelink.report.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRepository;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRequestRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * The manager keeps the catalogue of extra services (docs/section5/decisions.md, D21): adds a service, changes
 * what it says and how long it lasts, withdraws it or offers it again, and removes one nobody ever asked for.
 * A service is never renamed: providers offer services by name. One that was asked for is never removed either,
 * since its requests point at it; withdrawing it is how it goes.
 */
@Service
@Transactional
public class ValueAddedServiceCatalogueService {

    /** {@link BusinessRuleViolation#code()} when the name is already in the catalogue. */
    public static final String NAME_TAKEN = "VALUE_ADDED_SERVICE_NAME_TAKEN";
    /** {@link BusinessRuleViolation#code()} when removing a service that was asked for. */
    public static final String IN_USE = "VALUE_ADDED_SERVICE_IN_USE";

    private final ValueAddedServiceRepository services;
    private final ValueAddedServiceRequestRepository requests;

    public ValueAddedServiceCatalogueService(ValueAddedServiceRepository services,
            ValueAddedServiceRequestRepository requests) {
        this.services = services;
        this.requests = requests;
    }

    /** A service in the catalogue, and how many requests ever named it: none means it may be removed. */
    public record Entry(ValueAddedService service, long requests) {
        public boolean removable() {
            return requests == 0;
        }
    }

    /** Every service, withdrawn ones too, by name, each with its requests counted. */
    @Transactional(readOnly = true)
    public List<Entry> all() {
        Map<Long, Long> counts = requests.countByService();
        return services.findAll().stream()
                .sorted(Comparator.comparing(ValueAddedService::name, String.CASE_INSENSITIVE_ORDER))
                .map(service -> new Entry(service, counts.getOrDefault(service.id(), 0L)))
                .toList();
    }

    /** The names a provider may offer: the services elders and families can ask for now. */
    @Transactional(readOnly = true)
    public List<String> offerableNames() {
        return services.findAvailable().stream().map(ValueAddedService::name).toList();
    }

    /** Adds a service, available at once to elders, families and providers. */
    public ValueAddedService add(String name, String description, int durationMinutes) {
        ValueAddedService added = ValueAddedService.added(name, description, durationMinutes);
        if (services.findByName(added.name()).isPresent()) {
            throw nameTaken(added.name());
        }
        try {
            return services.save(added);
        } catch (DataIntegrityViolationException sameNameAtOnce) {
            // Another manager added the same name between the check and the insert: the unique key held.
            throw nameTaken(added.name());
        }
    }

    /** Changes a service's description, length or status; its name stays. */
    public ValueAddedService revise(Long id, String description, int durationMinutes,
            ValueAddedService.Status status) {
        ValueAddedService current = services.findById(id)
                .orElseThrow(() -> new ResourceNotFound("Value-added service", id));
        return services.save(current.revised(description, durationMinutes, status));
    }

    /**
     * Removes a service nobody ever asked for, such as one added by mistake: it leaves elders', families' and
     * providers' lists at once. A provider that offered it keeps the name until it changes its services. One that
     * was asked for is refused, since its requests point at it: withdraw it instead.
     */
    public void remove(Long id) {
        ValueAddedService service = services.findById(id)
                .orElseThrow(() -> new ResourceNotFound("Value-added service", id));
        if (requests.existsForService(id)) {
            throw inUse(service.name());
        }
        try {
            services.delete(id);
        } catch (DataIntegrityViolationException askedForMeanwhile) {
            // A request named it between the check and the delete: the foreign key held.
            throw inUse(service.name());
        }
    }

    private static BusinessRuleViolation inUse(String name) {
        return new BusinessRuleViolation(IN_USE, "\"" + name + "\" has been requested, so it stays in the catalogue "
                + "for those requests. Make it unavailable instead.");
    }

    private static BusinessRuleViolation nameTaken(String name) {
        return new BusinessRuleViolation(NAME_TAKEN, "There is already a service called \"" + name
                + "\". Choose another name, or make that one available again.");
    }
}
