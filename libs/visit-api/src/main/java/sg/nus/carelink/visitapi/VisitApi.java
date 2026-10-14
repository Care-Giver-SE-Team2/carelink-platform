package sg.nus.carelink.visitapi;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * What visit offers the other services: rostering and report reach visit's VisitReassignment,
 * VisitScheduling, UpcomingAssignments, StandaloneVisits and VisitScheduleQuery through it, and
 * incident asks it about visits. The records carry the same fields as those types did when the
 * callers called them in-process. visit serves it under {@code /internal/v1}.
 *
 * <p>Errors come back as they did in-process: 403 as {@link AccessDeniedException}, 404 as
 * {@link VisitNotFound}, and a broken business rule (409) as {@link VisitRuleViolation} with visit's
 * rule code. See {@link VisitApiClients}.
 *
 * <p>The four changes re-rostering makes take a request key ({@value #IDEMPOTENCY_KEY}). visit makes a
 * keyed change at most once and answers the same key again with the first answer, so a caller that
 * did not hear back - a read timeout after visit had already made the change - sends the change again
 * with the same key, never a new one. Without a key a change is made every time it is sent.
 */
@HttpExchange(url = "/internal/v1", accept = "application/json")
public interface VisitApi {

	/** The header that carries a change's request key. */
	String IDEMPOTENCY_KEY = "Idempotency-Key";

	// ---------- Reassignment (rostering, report) ----------

	@GetExchange("/visits/{visitId}")
	VisitSlot visit(@PathVariable Long visitId);

	/** {@link #visit}, with a visit that does not exist as empty. */
	default Optional<VisitSlot> findVisit(Long visitId) {
		try {
			return Optional.of(visit(visitId));
		}
		catch (VisitNotFound notFound) {
			return Optional.empty();
		}
	}

	@GetExchange("/visits/unstarted")
	List<VisitSlot> unstartedFor(@RequestParam Long caregiverId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until);

	@GetExchange("/visits/bookings")
	List<Booking> bookingsBetween(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until);

	/** For each caregiver, how many of the elder's visits they finished in the period. */
	@GetExchange("/visits/finished-with")
	Map<Long, Integer> finishedVisitsWith(@RequestParam Long elderId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until);

	/** @param requestKey makes the change once however often it is sent; null to make it every time */
	@PostExchange("/visits/{visitId}/reassignment")
	void reassign(@PathVariable Long visitId, @RequestBody Reassignment reassignment,
			@RequestHeader(name = IDEMPOTENCY_KEY, required = false) String requestKey);

	/** @param requestKey makes the change once however often it is sent; null to make it every time */
	@PostExchange("/visits/{visitId}/uncovered")
	void markUncovered(@PathVariable Long visitId, @RequestBody Change why,
			@RequestHeader(name = IDEMPOTENCY_KEY, required = false) String requestKey);

	/** @param requestKey makes the change once however often it is sent; null to make it every time */
	@PostExchange("/visits/{visitId}/call-off")
	void callOff(@PathVariable Long visitId, @RequestBody Change why,
			@RequestHeader(name = IDEMPOTENCY_KEY, required = false) String requestKey);

	/**
	 * Moves the visit to a new start and caregiver, and answers the id of the visit that replaces it. Sent
	 * again with the same key, it answers the same visit.
	 *
	 * @param requestKey makes the move once however often it is sent; null to make it every time
	 */
	@PostExchange("/visits/{visitId}/move")
	VisitRef moveTo(@PathVariable Long visitId, @RequestBody Move move,
			@RequestHeader(name = IDEMPOTENCY_KEY, required = false) String requestKey);

	// ---------- Scheduling (rostering) ----------

	@PostExchange("/visits/schedule")
	Outcome schedule(@RequestBody List<PlannedVisit> visits);

	@PostExchange("/visits/cancel-untouched")
	Cancelled cancelUntouchedFrom(@RequestBody CancelUntouched request);

	@PostExchange("/visits/release-untouched")
	Released releaseUntouchedFrom(@RequestBody ReleaseUntouched request);

	@GetExchange("/visits/uncovered-started")
	List<UncoveredVisit> findUncoveredStarted(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime now);

	@PostExchange("/visits/{visitId}/uncovered-exception")
	Marked markUncoveredAsException(@PathVariable Long visitId);

	@GetExchange("/visits/upcoming-assignments")
	List<Assignment> unstartedBetween(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until);

	// ---------- Standalone visits and schedule queries (report) ----------

	@PostExchange("/visits/standalone")
	VisitRef scheduleStandalone(@RequestBody NewVisit visit);

	@GetExchange("/visits/{visitId}/state")
	State visitState(@PathVariable Long visitId);

	/** {@link #visitState}, with a visit that does not exist as empty. */
	default Optional<State> findVisitState(Long visitId) {
		try {
			return Optional.of(visitState(visitId));
		}
		catch (VisitNotFound notFound) {
			return Optional.empty();
		}
	}

	@GetExchange("/visits/caregivers-of-elder")
	List<Long> caregiverIdsForElder(@RequestParam Long elderId);

	/** Whether the caregiver has, or had, a visit with any of the elders; with no elders, no. */
	@GetExchange("/visits/assigned")
	Assigned hasAssignedVisit(@RequestParam(required = false) Set<Long> elderIds, @RequestParam Long caregiverId);

	// ---------- Visit reads (incident) ----------

	/** The elder's scheduled, assigned visits starting in the period, earliest first. */
	@GetExchange("/visits/upcoming")
	List<ElderVisit> upcomingVisits(@RequestParam Long elderId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until);

	/** The caregiver on the elder's most recent visit; 404 when the elder has had none with a caregiver. */
	@GetExchange("/visits/latest-caregiver")
	CaregiverRef latestCaregiver(@RequestParam Long elderId);

	/**
	 * Whether the caregiver has a visit booked or under way (SCHEDULED, ARRIVED or IN_PROGRESS) that
	 * overlaps [from, until). A visit with no end counts as an hour long.
	 */
	@GetExchange("/visits/caregiver-busy")
	Busy caregiverBusy(@RequestParam Long caregiverId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until);

	/** {@link #latestCaregiver}, with no such visit as empty. */
	default Optional<Long> findLatestCaregiverId(Long elderId) {
		try {
			return Optional.ofNullable(latestCaregiver(elderId).caregiverId());
		}
		catch (VisitNotFound notFound) {
			return Optional.empty();
		}
	}

	// ---------- Records ----------

	record VisitSlot(Long visitId, Long elderId, Long caregiverId, Long carePlanId, String serviceType,
			LocalDateTime start, LocalDateTime end, String status, Long absenceId) {
	}

	record Booking(Long visitId, Long elderId, Long caregiverId, LocalDateTime start, LocalDateTime end) {
	}

	/** Why a visit changes hands, for its assignment history. */
	record Change(Long absenceId, Long byUserId, Long rosteringCandidateId, String reason) {
	}

	record Reassignment(Long toCaregiverId, Change why) {
	}

	record Move(LocalDateTime newStart, Long toCaregiverId, Change why) {
	}

	record VisitRef(Long visitId) {
	}

	record PlannedVisit(Long elderId, Long caregiverId, Long carePlanId, Long carePlanNodeId, String serviceType,
			LocalDateTime start, LocalDateTime end) {
	}

	record Outcome(int created, int covered) {
	}

	record CancelUntouched(Long carePlanId, LocalDateTime from) {
	}

	record Cancelled(int count) {
	}

	record ReleaseUntouched(Long elderId, Long caregiverId, LocalDateTime from) {
	}

	record Released(int count) {
	}

	record UncoveredVisit(Long visitId, Long elderId, LocalDateTime start, String serviceType) {
	}

	record Marked(boolean marked) {
	}

	record Assignment(Long visitId, Long caregiverId, Long carePlanId, LocalDateTime start) {
	}

	record NewVisit(Long elderId, Long caregiverId, String serviceType, LocalDateTime start, LocalDateTime end,
			String instructions) {
	}

	record State(Long visitId, Long caregiverId, String status, boolean checkedIn) {
	}

	record Assigned(boolean assigned) {
	}

	record ElderVisit(Long visitId, Long elderId, Long caregiverId, LocalDateTime start, String status,
			String serviceType) {
	}

	record CaregiverRef(Long caregiverId) {
	}

	record Busy(boolean busy) {
	}

}
