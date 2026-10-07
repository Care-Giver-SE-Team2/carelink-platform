package sg.nus.carelink.careplan.application;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.domain.repository.CarePlanRepository;

/** Test double for the port: the service is exercised without Spring or a database (as in identity). */
class InMemoryCarePlanRepository implements CarePlanRepository {

	private final Map<Long, CarePlan> rows = new HashMap<>();
	private long nextId = 1;

	@Override
	public Optional<CarePlan> findById(Long id) {
		return Optional.ofNullable(rows.get(id));
	}

	@Override
	public Optional<CarePlan> findLatestByElderId(Long elderId) {
		return rows.values().stream()
				.filter(p -> p.elderId().equals(elderId))
				.max(Comparator.comparing(CarePlan::version));
	}

	@Override
	public List<CarePlan> findByElderId(Long elderId) {
		return rows.values().stream()
				.filter(p -> p.elderId().equals(elderId))
				.sorted(Comparator.comparing(CarePlan::version))
				.toList();
	}

	@Override
	public List<Long> findElderIdsWithIssuedPlans() {
		return rows.values().stream()
				.filter(p -> p.status() != CarePlan.Status.DRAFT)
				.map(CarePlan::elderId)
				.distinct()
				.toList();
	}

	@Override
	public CarePlan save(CarePlan carePlan) {
		CarePlan stored = carePlan.id() == null
				? new CarePlan(nextId, carePlan.elderId(), carePlan.createdByUserId(), carePlan.supersedesPlanId(), carePlan.version(), carePlan.status(), carePlan.totalHours(), carePlan.publishedAt(), carePlan.createdAt(), carePlan.updatedAt(),
						carePlan.startDate(), carePlan.stopEffectiveDate(), carePlan.stopReason(), carePlan.stoppedByUserId(), carePlan.stoppedAt())
				: carePlan;
		rows.put(stored.id(), stored);
		if (carePlan.id() == null) {
			nextId++;
		}
		return stored;
	}
}
