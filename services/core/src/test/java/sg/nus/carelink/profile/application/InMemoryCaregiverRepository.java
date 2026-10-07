package sg.nus.carelink.profile.application;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.repository.CaregiverRepository;

/** Test double for the port; save() assigns ids from 1 when the caregiver has none. */
class InMemoryCaregiverRepository implements CaregiverRepository {

	private final Map<Long, Caregiver> rows = new HashMap<>();
	private long nextId = 1;

	@Override
	public Optional<Caregiver> findByUserId(Long userId) {
		return rows.values().stream().filter(row -> userId.equals(row.userId())).findFirst();
	}

	@Override
	public Optional<Caregiver> findById(Long id) {
		return Optional.ofNullable(rows.get(id));
	}

	@Override
	public List<Caregiver> findAll() {
		return rows.values().stream().sorted(Comparator.comparing(Caregiver::fullName)).toList();
	}

	@Override
	public List<Caregiver> findByIds(Set<Long> ids) {
		return rows.values().stream().filter(row -> ids.contains(row.id())).toList();
	}

	@Override
	public Caregiver save(Caregiver caregiver) {
		Caregiver stored = caregiver.id() != null ? caregiver : new Caregiver(nextId++, caregiver.userId(),
				caregiver.fullName(), caregiver.phone(), caregiver.sector(), caregiver.dialects(),
				caregiver.status(), caregiver.createdAt(), caregiver.updatedAt());
		rows.put(stored.id(), stored);
		return stored;
	}

	Caregiver save(String fullName, Caregiver.Status status) {
		return save(new Caregiver(null, 100L + nextId, fullName, null, "S31", null, status, null, null));
	}
}
