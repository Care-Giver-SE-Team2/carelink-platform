package sg.nus.carelink.careplan.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sg.nus.carelink.careplan.application.VisitPlanReader;
import sg.nus.carelink.coreapi.CoreApi;

/** careplan's part of core's internal API ({@link CoreApi}): the published plan a visit follows. */
@RestController
@RequestMapping("/internal/v1")
public class InternalCarePlanController {

	private final VisitPlanReader plans;

	InternalCarePlanController(VisitPlanReader plans) {
		this.plans = plans;
	}

	@GetMapping("/care-plans/{planId}/snapshot")
	public CoreApi.CarePlanSnapshot carePlanSnapshot(@PathVariable Long planId, @RequestParam Long elderId) {
		VisitPlanReader.Snapshot snapshot = plans.read(planId, elderId);
		return new CoreApi.CarePlanSnapshot(snapshot.id(), snapshot.version(), snapshot.tasks().stream()
				.map(task -> new CoreApi.CarePlanTask(task.id(), task.name(), task.evidenceType()))
				.toList());
	}

}
