package sg.nus.carelink.visit.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.application.VisitReassignment;
import sg.nus.carelink.visitapi.VisitApi;

/**
 * The re-rostering part of visit's internal API ({@link VisitApi}): which visits an absence
 * vacates, who is booked when, who has been to an elder before, and the four changes re-rostering
 * makes to a visit. Each call is one call to {@link VisitReassignment}.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalVisitReassignmentController {

	private final VisitReassignment visits;

	InternalVisitReassignmentController(VisitReassignment visits) {
		this.visits = visits;
	}

	@GetMapping("/visits/{visitId}")
	public VisitApi.VisitSlot visit(@PathVariable Long visitId) {
		return visits.find(visitId).map(InternalVisitReassignmentController::slot)
				.orElseThrow(() -> new ResourceNotFound("Visit", visitId));
	}

	@GetMapping("/visits/unstarted")
	public List<VisitApi.VisitSlot> unstartedFor(@RequestParam Long caregiverId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until) {
		return visits.unstartedFor(caregiverId, from, until).stream().map(InternalVisitReassignmentController::slot)
				.toList();
	}

	@GetMapping("/visits/bookings")
	public List<VisitApi.Booking> bookingsBetween(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until) {
		return visits.bookingsBetween(from, until).stream()
				.map(booking -> new VisitApi.Booking(booking.visitId(), booking.elderId(), booking.caregiverId(),
						booking.start(), booking.end()))
				.toList();
	}

	@GetMapping("/visits/finished-with")
	public Map<Long, Integer> finishedVisitsWith(@RequestParam Long elderId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until) {
		return visits.finishedVisitsWith(elderId, since, until);
	}

	@PostMapping("/visits/{visitId}/reassignment")
	public ResponseEntity<Void> reassign(@PathVariable Long visitId, @RequestBody VisitApi.Reassignment reassignment) {
		visits.reassign(visitId, reassignment.toCaregiverId(), change(reassignment.why()));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/visits/{visitId}/uncovered")
	public ResponseEntity<Void> markUncovered(@PathVariable Long visitId, @RequestBody VisitApi.Change why) {
		visits.markUncovered(visitId, change(why));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/visits/{visitId}/call-off")
	public ResponseEntity<Void> callOff(@PathVariable Long visitId, @RequestBody VisitApi.Change why) {
		visits.callOff(visitId, change(why));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/visits/{visitId}/move")
	public VisitApi.VisitRef moveTo(@PathVariable Long visitId, @RequestBody VisitApi.Move move) {
		return new VisitApi.VisitRef(visits.moveTo(visitId, move.newStart(), move.toCaregiverId(), change(move.why())));
	}

	private static VisitReassignment.Change change(VisitApi.Change why) {
		return new VisitReassignment.Change(why.absenceId(), why.byUserId(), why.rosteringCandidateId(), why.reason());
	}

	private static VisitApi.VisitSlot slot(VisitReassignment.VisitSlot slot) {
		return new VisitApi.VisitSlot(slot.visitId(), slot.elderId(), slot.caregiverId(), slot.carePlanId(),
				slot.serviceType(), slot.start(), slot.end(), slot.status(), slot.absenceId());
	}

}
