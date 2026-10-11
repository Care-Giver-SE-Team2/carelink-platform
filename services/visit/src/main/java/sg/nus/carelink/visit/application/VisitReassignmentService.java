package sg.nus.carelink.visit.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.model.VisitAssignment;
import sg.nus.carelink.visit.domain.repository.VisitAssignmentRepository;
import sg.nus.carelink.visit.domain.repository.VisitRepository;

/**
 * UC-MG04's way into the visits. Whether a visit may change is the Visit's own rule; this class
 * loads, asks, saves, and keeps visit_assignment in step.
 *
 * <p>A refused change leaves as {@link BusinessRuleViolation}, HTTP 409, not as the
 * {@code IllegalStateException} the model throws: by the time a manager or a family acts, the
 * visit may have been started or called off by somebody else, and that is a conflict to report,
 * not a server error.
 */
@Service
@Transactional
class VisitReassignmentService implements VisitReassignment {

	/** States in which a caregiver is, was or will be at the visit: what fills their day. */
	private static final Set<Visit.Status> BOOKED = EnumSet.of(Visit.Status.SCHEDULED, Visit.Status.ARRIVED,
			Visit.Status.IN_PROGRESS, Visit.Status.COMPLETED, Visit.Status.VERIFIED, Visit.Status.AUTO_CLOSED);

	/** States of a visit that was actually carried out. */
	private static final Set<Visit.Status> FINISHED = EnumSet.of(Visit.Status.COMPLETED, Visit.Status.VERIFIED,
			Visit.Status.AUTO_CLOSED);

	private final VisitRepository visits;
	private final VisitAssignmentRepository assignments;
	private final Clock clock;

	VisitReassignmentService(VisitRepository visits, VisitAssignmentRepository assignments, Clock clock) {
		this.visits = visits;
		this.assignments = assignments;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<VisitSlot> unstartedFor(Long caregiverId, LocalDateTime from, LocalDateTime until) {
		return visits.findAssigned(caregiverId, from, until).stream()
				.filter(Visit::hasNotStarted)
				.map(VisitReassignmentService::toSlot)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<VisitSlot> find(Long visitId) {
		return visits.findById(visitId).map(VisitReassignmentService::toSlot);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Booking> bookingsBetween(LocalDateTime from, LocalDateTime until) {
		return visits.findScheduledBetween(from, until).stream()
				.filter(visit -> visit.caregiverId() != null && BOOKED.contains(visit.status()))
				.map(visit -> new Booking(visit.id(), visit.elderId(), visit.caregiverId(), visit.scheduledStart(),
						visit.scheduledEnd()))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, Integer> finishedVisitsWith(Long elderId, LocalDateTime since, LocalDateTime until) {
		return visits.findScheduledBetween(since, until).stream()
				.filter(visit -> elderId.equals(visit.elderId()))
				.filter(visit -> visit.caregiverId() != null && FINISHED.contains(visit.status()))
				.collect(Collectors.groupingBy(Visit::caregiverId, Collectors.summingInt(visit -> 1)));
	}

	@Override
	public void reassign(Long visitId, Long toCaregiverId, Change why) {
		Visit visit = requireOpen(visitId, "change hands");
		LocalDateTime now = LocalDateTime.now(clock);
		endCurrent(visit, VisitAssignment.Status.REPLACED, "handed to caregiver #" + toCaregiverId, now);
		visits.save(visit.reassignedForAbsence(toCaregiverId, why.absenceId()));
		assignments.save(VisitAssignment.active(visitId, toCaregiverId, why.byUserId(), why.reason(),
				why.rosteringCandidateId(), now));
	}

	@Override
	public void markUncovered(Long visitId, Change why) {
		Visit visit = require(visitId);
		if (!visit.hasNotStarted()) {
			throw notOpen(visit, "be left uncovered");
		}
		endCurrent(visit, VisitAssignment.Status.CANCELLED, "nobody free to cover it", LocalDateTime.now(clock));
		visits.save(visit.uncoveredForAbsence(why.absenceId()));
	}

	@Override
	public void callOff(Long visitId, Change why) {
		Visit visit = requireOpen(visitId, "be called off");
		endCurrent(visit, VisitAssignment.Status.CANCELLED, why.reason(), LocalDateTime.now(clock));
		visits.save(visit.calledOffForAbsence(why.absenceId()));
	}

	@Override
	public Long moveTo(Long visitId, LocalDateTime newStart, Long toCaregiverId, Change why) {
		Visit visit = requireOpen(visitId, "be moved");
		LocalDateTime now = LocalDateTime.now(clock);
		endCurrent(visit, VisitAssignment.Status.CANCELLED, "visit moved to " + newStart, now);
		visits.save(visit.calledOffForAbsence(why.absenceId()));
		Visit moved = visits.save(visit.movedTo(newStart, toCaregiverId, why.absenceId()));
		assignments.save(VisitAssignment.active(moved.id(), toCaregiverId, why.byUserId(), why.reason(),
				why.rosteringCandidateId(), now));
		return moved.id();
	}

	/**
	 * Ends whatever assignment the visit has. A visit rostered from its care plan has no row
	 * yet, so its caregiver is written down first, already ended - otherwise the history would
	 * begin with the replacement.
	 */
	private void endCurrent(Visit visit, VisitAssignment.Status how, String why, LocalDateTime now) {
		Optional<VisitAssignment> active = assignments.findActiveByVisitId(visit.id());
		if (active.isPresent()) {
			assignments.save(how == VisitAssignment.Status.REPLACED
					? active.get().replaced(why, now)
					: active.get().cancelled(why, now));
		}
		else if (visit.caregiverId() != null) {
			assignments.save(VisitAssignment.earlier(visit.id(), visit.caregiverId(), how,
					"rostered from the care plan; ended: " + why, visit.createdAt(), now));
		}
	}

	private Visit requireOpen(Long visitId, String verb) {
		Visit visit = require(visitId);
		if (!visit.hasNotStarted() && !visit.leftUncoveredByAbsence()) {
			throw notOpen(visit, verb);
		}
		return visit;
	}

	private Visit require(Long visitId) {
		return visits.findById(visitId).orElseThrow(() -> new ResourceNotFound("Visit", visitId));
	}

	private static BusinessRuleViolation notOpen(Visit visit, String verb) {
		return new BusinessRuleViolation("VISIT_NOT_OPEN",
				"Visit %d is %s and can no longer %s".formatted(visit.id(), visit.status(), verb));
	}

	private static VisitSlot toSlot(Visit visit) {
		return new VisitSlot(visit.id(), visit.elderId(), visit.caregiverId(), visit.carePlanId(),
				visit.serviceType(), visit.scheduledStart(), visit.scheduledEnd(), visit.status().name(),
				visit.absenceId());
	}
}
