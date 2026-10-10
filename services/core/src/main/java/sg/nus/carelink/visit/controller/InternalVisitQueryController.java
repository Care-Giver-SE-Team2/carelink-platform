package sg.nus.carelink.visit.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.application.StandaloneVisits;
import sg.nus.carelink.visit.application.VisitLookups;
import sg.nus.carelink.visit.domain.repository.VisitScheduleQuery;
import sg.nus.carelink.visitapi.VisitApi;

/**
 * The report and incident part of visit's internal API ({@link VisitApi}): the work order of an
 * approved extra service and where it stands, which caregivers an elder has had, whether a
 * caregiver is busy at a time, and the reads of the visit table incident makes today. In front of {@link StandaloneVisits},
 * {@link VisitScheduleQuery} and {@link VisitLookups}.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalVisitQueryController {

	private final StandaloneVisits standalone;

	private final VisitScheduleQuery schedule;

	private final VisitLookups lookups;

	InternalVisitQueryController(StandaloneVisits standalone, VisitScheduleQuery schedule, VisitLookups lookups) {
		this.standalone = standalone;
		this.schedule = schedule;
		this.lookups = lookups;
	}

	@PostMapping("/visits/standalone")
	public VisitApi.VisitRef scheduleStandalone(@RequestBody VisitApi.NewVisit visit) {
		return new VisitApi.VisitRef(standalone.schedule(new StandaloneVisits.NewVisit(visit.elderId(),
				visit.caregiverId(), visit.serviceType(), visit.start(), visit.end(), visit.instructions())));
	}

	@GetMapping("/visits/{visitId}/state")
	public VisitApi.State visitState(@PathVariable Long visitId) {
		return standalone.find(visitId)
				.map(state -> new VisitApi.State(state.visitId(), state.caregiverId(), state.status(), state.checkedIn()))
				.orElseThrow(() -> new ResourceNotFound("Visit", visitId));
	}

	@GetMapping("/visits/caregivers-of-elder")
	public List<Long> caregiverIdsForElder(@RequestParam Long elderId) {
		return schedule.caregiverIdsForElder(elderId);
	}

	/** No elders is a question too: the client sends no elderIds at all, and the answer is no. */
	@GetMapping("/visits/assigned")
	public VisitApi.Assigned hasAssignedVisit(@RequestParam(required = false) Set<Long> elderIds,
			@RequestParam Long caregiverId) {
		return new VisitApi.Assigned(schedule.hasAssignedVisit(elderIds == null ? Set.of() : elderIds, caregiverId));
	}

	@GetMapping("/visits/upcoming")
	public List<VisitApi.ElderVisit> upcomingVisits(@RequestParam Long elderId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until) {
		return lookups.upcomingVisits(elderId, from, until).stream()
				.map(visit -> new VisitApi.ElderVisit(visit.visitId(), visit.elderId(), visit.caregiverId(),
						visit.start(), visit.status(), visit.serviceType()))
				.toList();
	}

	@GetMapping("/visits/caregiver-busy")
	public VisitApi.Busy caregiverBusy(@RequestParam Long caregiverId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until) {
		return new VisitApi.Busy(lookups.caregiverBusy(caregiverId, from, until));
	}

	@GetMapping("/visits/latest-caregiver")
	public VisitApi.CaregiverRef latestCaregiver(@RequestParam Long elderId) {
		return lookups.latestCaregiverId(elderId).map(VisitApi.CaregiverRef::new)
				.orElseThrow(() -> new ResourceNotFound("A visit with a caregiver for elder", elderId));
	}

}
