package sg.nus.carelink.profile.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import sg.nus.carelink.profile.domain.model.PrimaryCaregiverAssignment;
import sg.nus.carelink.profile.domain.repository.PrimaryCaregiverAssignmentRepository;

/** Test double for the port, keyed by elder id like the table. */
class InMemoryPrimaryCaregiverAssignmentRepository implements PrimaryCaregiverAssignmentRepository {

	private final Map<Long, PrimaryCaregiverAssignment> rows = new HashMap<>();

	@Override
	public Optional<PrimaryCaregiverAssignment> findByElderId(Long elderId) {
		return Optional.ofNullable(rows.get(elderId));
	}

	@Override
	public List<PrimaryCaregiverAssignment> findByElderIds(Set<Long> elderIds) {
		return rows.values().stream().filter(row -> elderIds.contains(row.elderId())).toList();
	}

	@Override
	public PrimaryCaregiverAssignment save(PrimaryCaregiverAssignment assignment) {
		rows.put(assignment.elderId(), assignment);
		return assignment;
	}

	@Override
	public void deleteByElderId(Long elderId) {
		rows.remove(elderId);
	}
}
