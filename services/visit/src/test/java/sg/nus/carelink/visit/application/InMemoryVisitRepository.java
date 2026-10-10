package sg.nus.carelink.visit.application;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.repository.VisitRepository;

/**
 * Test double for the visit repository.
 *
 * <p>The application service is exercised without Spring or a database.
 */
class InMemoryVisitRepository
        implements VisitRepository, sg.nus.carelink.visit.domain.repository.VisitCommandRepository {

    @Override public Optional<Visit> lock(Long id) { return findById(id); }

    private final Map<Long, Visit> rows =
            new HashMap<>();

    private long nextId = 1;

    @Override
    public List<Visit> findAssigned(Long caregiverId, java.time.LocalDateTime from, java.time.LocalDateTime until) {
        return rows.values().stream().filter(v -> caregiverId.equals(v.caregiverId()))
                .filter(v -> !v.scheduledStart().isBefore(from) && v.scheduledStart().isBefore(until))
                .sorted(Comparator.comparing(Visit::scheduledStart).thenComparing(Visit::id)).toList();
    }

    @Override
    public List<Visit> findScheduledBetween(java.time.LocalDateTime from, java.time.LocalDateTime until) {
        return rows.values().stream()
                .filter(v -> !v.scheduledStart().isBefore(from) && v.scheduledStart().isBefore(until))
                .sorted(Comparator.comparing(Visit::scheduledStart).thenComparing(Visit::id)).toList();
    }

    @Override
    public List<Visit> findUnassignedScheduledStartingBetween(java.time.LocalDateTime from, java.time.LocalDateTime until) {
        return rows.values().stream()
                .filter(v -> v.caregiverId() == null && v.status() == Visit.Status.SCHEDULED)
                .filter(v -> !v.scheduledStart().isBefore(from) && v.scheduledStart().isBefore(until))
                .sorted(Comparator.comparing(Visit::scheduledStart).thenComparing(Visit::id)).toList();
    }

    @Override
    public List<Visit> findByCarePlanIdStartingFrom(Long carePlanId, java.time.LocalDateTime from) {
        return rows.values().stream()
                .filter(v -> carePlanId.equals(v.carePlanId()) && !v.scheduledStart().isBefore(from))
                .toList();
    }

    @Override
    public Optional<Visit> findById(
            Long id) {

        return Optional.ofNullable(
                rows.get(id)
        );
    }

    @Override
    public List<Visit> findCompletedByElderId(
            Long elderId) {

        return rows.values()
                .stream()
                .filter(visit ->
                        elderId.equals(
                                visit.elderId()
                        )
                )
                .filter(visit ->
                        visit.status()
                                == Visit.Status.COMPLETED
                )
                .sorted(
                        Comparator.comparing(
                                Visit::scheduledStart,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
                )
                .toList();
    }

    @Override
    public Visit save(
            Visit visit) {

        Visit stored =
                visit.id() == null
                        ? new Visit(
                                nextId,
                                visit.elderId(),
                                visit.caregiverId(),
                                visit.carePlanNodeId(),
                                visit.absenceId(),
                                visit.serviceType(),
                                visit.scheduledStart(),
                                visit.scheduledEnd(),
                                visit.checkedInAt(),
                                visit.checkedOutAt(),
                                visit.status(),
                                visit.stateDeadline(),
                                visit.carePlanId(),
                                visit.version(),
                                visit.createdAt(),
                                visit.updatedAt()
                        )
                        : visit;

        rows.put(
                stored.id(),
                stored
        );

        if (visit.id() == null) {
            nextId++;
        }

        return stored;
    }
}
