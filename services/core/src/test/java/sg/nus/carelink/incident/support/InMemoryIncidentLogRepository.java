package sg.nus.carelink.incident.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import sg.nus.carelink.incident.domain.model.IncidentLog;

import sg.nus.carelink.incident.domain.repository.IncidentLogRepository;

/**
 * Append-only test double for the timeline.
 *
 * <p>It deliberately offers no way to delete or rewrite an entry, so a test cannot
 * accidentally assert against a timeline the real system could never produce.
 */
public final class InMemoryIncidentLogRepository implements IncidentLogRepository {

	private final List<IncidentLog> entries = new ArrayList<>();
	private long nextId = 1;

	@Override
	public Optional<IncidentLog> findById(Long id) {
		return entries.stream().filter(entry -> id.equals(entry.id())).findFirst();
	}

	@Override
	public IncidentLog save(IncidentLog incidentLog) {
		IncidentLog stored = new IncidentLog(
				nextId++,
				incidentLog.incidentId(),
				incidentLog.actor(),
				incidentLog.action(),
				incidentLog.detail(),
				incidentLog.occurredAt());
		entries.add(stored);
		return stored;
	}

	@Override
	public List<IncidentLog> findTimeline(Long incidentId) {
		return entries.stream()
				.filter(entry -> incidentId.equals(entry.incidentId()))
				.toList();
	}

	/** Every action recorded for an incident, in order, as plain strings. */
	public List<String> actionsFor(Long incidentId) {
		return findTimeline(incidentId).stream().map(IncidentLog::action).toList();
	}

	public List<IncidentLog> all() {
		return List.copyOf(entries);
	}
}
