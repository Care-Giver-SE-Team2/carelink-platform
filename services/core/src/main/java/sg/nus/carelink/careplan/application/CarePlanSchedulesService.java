package sg.nus.carelink.careplan.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.domain.model.CarePlanNode;
import sg.nus.carelink.careplan.domain.repository.CarePlanNodeRepository;
import sg.nus.carelink.careplan.domain.repository.CarePlanRepository;

@Service
@Transactional(readOnly = true)
class CarePlanSchedulesService implements CarePlanSchedules {

	private final CarePlanRepository carePlans;
	private final CarePlanNodeRepository carePlanNodes;

	CarePlanSchedulesService(CarePlanRepository carePlans, CarePlanNodeRepository carePlanNodes) {
		this.carePlans = carePlans;
		this.carePlanNodes = carePlanNodes;
	}

	@Override
	public List<PlanSchedule> forElder(Long elderId) {
		List<CarePlan> versions = carePlans.findByElderId(elderId);
		Map<Long, CarePlan> successorOf = versions.stream()
				.filter(plan -> plan.supersedesPlanId() != null && plan.status() != CarePlan.Status.DRAFT)
				.collect(Collectors.toMap(CarePlan::supersedesPlanId, Function.identity(), (a, b) -> a));
		return versions.stream()
				.map(plan -> plan.effectivePeriod(successorOf.get(plan.id()))
						.map(period -> new PlanSchedule(plan.id(), plan.elderId(), plan.version(),
								period.from(), period.until(), tasksOf(plan.id())))
						.orElse(null))
				.filter(Objects::nonNull)
				.toList();
	}

	@Override
	public List<Long> eldersWithSchedules() {
		return carePlans.findElderIdsWithIssuedPlans();
	}

	private List<Task> tasksOf(Long carePlanId) {
		return carePlanNodes.findByCarePlanId(carePlanId).stream()
				.map(CarePlanSchedulesService::taskOf)
				.toList();
	}

	private static Task taskOf(CarePlanNode node) {
		return new Task(node.id(), node.groupName(), node.activityCode(), node.name(), node.visits().stream()
				.map(visit -> new Slot(visit.day(), visit.startTime(), visit.minutes()))
				.toList());
	}
}
