package sg.nus.carelink.incident.support;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.PageSlice;
import sg.nus.carelink.incident.domain.repository.IncidentRepository;

/** Test double for the port: the services are exercised without Spring or a database. */
public final class InMemoryIncidentRepository implements IncidentRepository {

	private final Map<Long, Incident> rows = new LinkedHashMap<>();
	private long nextId = 1;

	@Override
	public Optional<Incident> findById(Long id) {
		return Optional.ofNullable(rows.get(id));
	}

	@Override
	public Incident save(Incident incident) {
		Incident stored = incident.id() == null ? IncidentFixtures.withId(incident, nextId++) : incident;
		rows.put(stored.id(), stored);
		return stored;
	}

	@Override
	public List<Incident> findAwaitingTakeOverPastDeadline(LocalDateTime deadline) {
		List<Incident> overdue = new ArrayList<>();
		for (Incident incident : rows.values()) {
			if (incident.awaitingTakeOver()
					&& incident.respondBy() != null
					&& !incident.respondBy().isAfter(deadline)) {
				overdue.add(incident);
			}
		}
		overdue.sort(Comparator.comparing(Incident::respondBy));
		return List.copyOf(overdue);
	}

	@Override
	public Optional<Long> lastResponderForElder(Long elderId, Long excludingIncidentId) {
		return rows.values().stream()
				.filter(incident -> incident.elderId().equals(elderId))
				.filter(incident -> !incident.id().equals(excludingIncidentId))
				.filter(incident -> incident.responderUserId() != null)
				.max(Comparator.comparing(Incident::reportedAt))
				.map(Incident::responderUserId);
	}

	@Override
	public List<Incident> findByElder(Long elderId) {
		return rows.values().stream()
				.filter(incident -> incident.elderId().equals(elderId))
				.sorted(Comparator.comparing(Incident::reportedAt).reversed())
				.toList();
	}

	/**
	 * The same ordering the adapter asks the database for: nearest deadline first, the
	 * incidents without one after them, newest of those first. Written out rather than
	 * left to insertion order, because a test that passes on insertion order would say
	 * nothing about the queue the manager actually sees.
	 */
	@Override
	public PageSlice<Incident> findQueue(
			Set<Incident.Status> statuses, Incident.Severity severity, Long elderId, int page, int size) {

		List<Incident> matching = rows.values().stream()
				.filter(incident -> statuses.contains(incident.status()))
				.filter(incident -> severity == null || incident.severity() == severity)
				.filter(incident -> elderId == null || elderId.equals(incident.elderId()))
				.sorted(Comparator
						.comparingInt(InMemoryIncidentRepository::queueTier)
						.thenComparing(Incident::respondBy, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(Incident::reportedAt, Comparator.reverseOrder()))
				.toList();

		int from = Math.min(page * size, matching.size());
		int to = Math.min(from + size, matching.size());
		return new PageSlice<>(matching.subList(from, to), page, size, matching.size());
	}

	/** The same three tiers the real query orders by; see IncidentRepository#findQueue. */
	private static int queueTier(Incident incident) {
		if (incident.status() == Incident.Status.UNRESOLVED_ESCALATED) {
			return 0;
		}
		return incident.responderUserId() == null ? 1 : 2;
	}

	public int size() {
		return rows.size();
	}
}
