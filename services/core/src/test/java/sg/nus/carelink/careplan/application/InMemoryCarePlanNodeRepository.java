package sg.nus.carelink.careplan.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import sg.nus.carelink.careplan.domain.model.CarePlanNode;
import sg.nus.carelink.careplan.domain.repository.CarePlanNodeRepository;

/** Test double for the port: the service is exercised without Spring or a database (as in identity). */
class InMemoryCarePlanNodeRepository implements CarePlanNodeRepository {

	private final Map<Long, CarePlanNode> rows = new HashMap<>();
	private long nextId = 1;

	@Override
	public Optional<CarePlanNode> findById(Long id) {
		return Optional.ofNullable(rows.get(id));
	}

	@Override
	public List<CarePlanNode> findByCarePlanId(Long carePlanId) {
		return rows.values().stream().filter(n -> n.carePlanId().equals(carePlanId)).toList();
	}

	@Override
	public CarePlanNode save(CarePlanNode carePlanNode) {
		CarePlanNode stored = carePlanNode.id() == null
				? new CarePlanNode(nextId, carePlanNode.carePlanId(), carePlanNode.groupName(),
						carePlanNode.activityCode(), carePlanNode.name(),
						carePlanNode.scheduleDays(), carePlanNode.durationPerVisit(), carePlanNode.weeklyHours(),
						carePlanNode.evidenceType(), carePlanNode.displayOrder(), carePlanNode.createdAt(),
						carePlanNode.updatedAt(), carePlanNode.visits())
				: carePlanNode;
		rows.put(stored.id(), stored);
		if (carePlanNode.id() == null) {
			nextId++;
		}
		return stored;
	}

	@Override
	public void deleteByCarePlanId(Long carePlanId) {
		new ArrayList<>(rows.values()).stream()
				.filter(n -> n.carePlanId().equals(carePlanId))
				.forEach(n -> rows.remove(n.id()));
	}
}
