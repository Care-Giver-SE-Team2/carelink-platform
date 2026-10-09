package sg.nus.carelink.rostering.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.rostering.domain.repository.RosteringRunRepository;

/** Test double for the port: the service is exercised without Spring or a database (as in identity). */
class InMemoryRosteringRunRepository implements RosteringRunRepository {

	private final Map<Long, RosteringRun> rows = new HashMap<>();
	private long nextId = 1;

	@Override
	public Optional<RosteringRun> findById(Long id) {
		return Optional.ofNullable(rows.get(id));
	}

	@Override
	public RosteringRun save(RosteringRun rosteringRun) {
		RosteringRun stored = rosteringRun.id() == null
				? new RosteringRun(nextId, rosteringRun.triggerType(), rosteringRun.absenceId(), rosteringRun.objective(), rosteringRun.requestedByUserId(), rosteringRun.status(), rosteringRun.visitsTotal(), rosteringRun.visitsCovered(), rosteringRun.continuityKept(), rosteringRun.addedTravelKm(), rosteringRun.ranAt(), rosteringRun.committedAt())
				: rosteringRun;
		rows.put(stored.id(), stored);
		if (rosteringRun.id() == null) {
			nextId++;
		}
		return stored;
	}
}
