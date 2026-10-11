package sg.nus.carelink.visit.infrastructure.core;

import org.springframework.stereotype.Component;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreNotFound;
import sg.nus.carelink.coreapi.CoreRuleViolation;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.application.VisitPlanReader;

/**
 * {@link VisitPlanReader} from core's internal API. core's 404 and 409 become the errors core's
 * care plans threw in process, with the same code.
 */
@Component
class CoreVisitPlanReader implements VisitPlanReader {

	private final CoreApi core;

	CoreVisitPlanReader(CoreApi core) {
		this.core = core;
	}

	@Override
	public Snapshot read(Long planId, Long elderId) {
		try {
			CoreApi.CarePlanSnapshot plan = core.carePlanSnapshot(planId, elderId);
			return new Snapshot(plan.id(), plan.version(), plan.tasks().stream()
					.map(task -> new Task(task.id(), task.name(), task.evidenceType()))
					.toList());
		}
		catch (CoreNotFound missing) {
			throw new ResourceNotFound("Visit care plan", planId);
		}
		catch (CoreRuleViolation refused) {
			throw new BusinessRuleViolation(refused.code(), refused.getMessage());
		}
	}

}
