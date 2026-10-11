package sg.nus.carelink.visit.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sg.nus.carelink.visit.application.UpcomingAssignments;
import sg.nus.carelink.visit.application.VisitScheduling;
import sg.nus.carelink.visitapi.VisitApi;

/**
 * The rostering part of visit's internal API ({@link VisitApi}): the visits a published care plan
 * calls for, calling off a superseded version's untouched visits, visits that started with nobody on
 * them, and the booked visits a lapsing certificate puts at risk. In front of {@link VisitScheduling}
 * and {@link UpcomingAssignments}.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalVisitPlanningController {

	private final VisitScheduling scheduling;

	private final UpcomingAssignments assignments;

	InternalVisitPlanningController(VisitScheduling scheduling, UpcomingAssignments assignments) {
		this.scheduling = scheduling;
		this.assignments = assignments;
	}

	@PostMapping("/visits/schedule")
	public VisitApi.Outcome schedule(@RequestBody List<VisitApi.PlannedVisit> visits) {
		VisitScheduling.Outcome outcome = scheduling.schedule(visits.stream()
				.map(visit -> new VisitScheduling.PlannedVisit(visit.elderId(), visit.caregiverId(), visit.carePlanId(),
						visit.carePlanNodeId(), visit.serviceType(), visit.start(), visit.end()))
				.toList());
		return new VisitApi.Outcome(outcome.created(), outcome.covered());
	}

	@PostMapping("/visits/cancel-untouched")
	public VisitApi.Cancelled cancelUntouchedFrom(@RequestBody VisitApi.CancelUntouched request) {
		return new VisitApi.Cancelled(scheduling.cancelUntouchedFrom(request.carePlanId(), request.from()));
	}

	@GetMapping("/visits/uncovered-started")
	public List<VisitApi.UncoveredVisit> findUncoveredStarted(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime now) {
		return scheduling.findUncoveredStarted(since, now).stream()
				.map(visit -> new VisitApi.UncoveredVisit(visit.visitId(), visit.elderId(), visit.start(),
						visit.serviceType()))
				.toList();
	}

	@PostMapping("/visits/{visitId}/uncovered-exception")
	public VisitApi.Marked markUncoveredAsException(@PathVariable Long visitId) {
		return new VisitApi.Marked(scheduling.markUncoveredAsException(visitId));
	}

	@GetMapping("/visits/upcoming-assignments")
	public List<VisitApi.Assignment> unstartedBetween(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until) {
		return assignments.unstartedBetween(from, until).stream()
				.map(assignment -> new VisitApi.Assignment(assignment.visitId(), assignment.caregiverId(),
						assignment.carePlanId(), assignment.start()))
				.toList();
	}

}
