package sg.nus.carelink.careplan.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.domain.model.CarePlanNode;
import sg.nus.carelink.careplan.domain.model.ScheduleDays;
import sg.nus.carelink.careplan.domain.repository.CarePlanNodeRepository;
import sg.nus.carelink.careplan.domain.repository.CarePlanRepository;

@Service
@Transactional(readOnly = true)
class CarePlanLookupService implements CarePlanLookup {

	private final CarePlanRepository carePlans;
	private final CarePlanNodeRepository carePlanNodes;

	CarePlanLookupService(CarePlanRepository carePlans, CarePlanNodeRepository carePlanNodes) {
		this.carePlans = carePlans;
		this.carePlanNodes = carePlanNodes;
	}

	@Override
	public Optional<CarePlan> findLatestByElderId(Long elderId) {
		return carePlans.findLatestByElderId(elderId);
	}

	@Override
	public Optional<LocalDate> findNextVisitDate(Long elderId, LocalDate from) {
		CarePlan plan = carePlans.findLatestByElderId(elderId).orElse(null);
		if (plan == null || plan.status() != CarePlan.Status.PUBLISHED || plan.startDate() == null) {
			return Optional.empty();
		}
		Set<DayOfWeek> scheduledDays = carePlanNodes.findByCarePlanId(plan.id()).stream()
				.map(CarePlanNode::scheduleDays)
				.flatMap(days -> ScheduleDays.decode(days).stream())
				.collect(java.util.stream.Collectors.toSet());
		if (scheduledDays.isEmpty()) {
			return Optional.empty();
		}
		LocalDate candidate = plan.startDate().isAfter(from) ? plan.startDate() : from;
		for (int i = 0; i < 7; i++) {
			if (scheduledDays.contains(candidate.getDayOfWeek())) {
				return Optional.of(candidate);
			}
			candidate = candidate.plusDays(1);
		}
		return Optional.empty();
	}

}
