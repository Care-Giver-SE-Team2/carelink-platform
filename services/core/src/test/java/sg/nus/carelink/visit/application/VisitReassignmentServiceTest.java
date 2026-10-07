package sg.nus.carelink.visit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.model.VisitAssignment;
import sg.nus.carelink.visit.domain.repository.VisitAssignmentRepository;

/**
 * UC-MG04's way into the visits: what an absence vacates, who is booked when, and the four
 * changes - each of which keeps visit_assignment in step, starting the history with the
 * caregiver the care plan rostered when no row exists yet.
 */
class VisitReassignmentServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 9, 0);
	private static final LocalDateTime EIGHTH = LocalDateTime.of(2026, 10, 8, 9, 0);

	private final InMemoryVisitRepository visits = new InMemoryVisitRepository();
	private final Assignments assignments = new Assignments();
	private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Singapore")).toInstant(),
			ZoneId.of("Asia/Singapore"));
	private final VisitReassignmentService service = new VisitReassignmentService(visits, assignments, clock);

	private Long morning;
	private Long afternoon;

	@BeforeEach
	void setUp() {
		morning = visits.save(Visit.scheduled(7L, 5L, 4L, 3L, "Personal care", EIGHTH, EIGHTH.plusMinutes(45))).id();
		afternoon = visits.save(Visit.scheduled(8L, 5L, 4L, 3L, "Meals", EIGHTH.withHour(14), null)).id();
		visits.save(withStatus(Visit.scheduled(7L, 9L, 4L, 3L, "Personal care", EIGHTH.minusDays(10), null),
				Visit.Status.COMPLETED));
		visits.save(withStatus(Visit.scheduled(7L, 9L, 4L, 3L, "Personal care", EIGHTH.minusDays(3), null),
				Visit.Status.VERIFIED));
		visits.save(withStatus(Visit.scheduled(7L, 10L, 4L, 3L, "Personal care", EIGHTH.minusDays(2), null),
				Visit.Status.CANCELLED));
	}

	@Test
	void theAbsenceVacatesTheCaregiversUnstartedVisits() {
		assertThat(service.unstartedFor(5L, EIGHTH.toLocalDate().atStartOfDay(), EIGHTH.plusDays(1)))
				.extracting(VisitReassignment.VisitSlot::visitId).containsExactly(morning, afternoon);
		VisitReassignment.VisitSlot slot = service.find(morning).orElseThrow();
		assertThat(slot.status()).isEqualTo("SCHEDULED");
		assertThat(slot.carePlanId()).isEqualTo(4L);
		assertThat(slot.end()).isEqualTo(EIGHTH.plusMinutes(45));
	}

	@Test
	void bookingsAndHistoryCountOnlyVisitsThatHappenOrWill() {
		assertThat(service.bookingsBetween(EIGHTH.minusDays(30), EIGHTH.plusDays(1)))
				.extracting(VisitReassignment.Booking::caregiverId).containsExactlyInAnyOrder(9L, 9L, 5L, 5L);
		assertThat(service.finishedVisitsWith(7L, EIGHTH.minusDays(30), NOW)).containsExactlyEntriesOf(java.util.Map.of(9L, 2));
		assertThat(service.finishedVisitsWith(8L, EIGHTH.minusDays(30), NOW)).isEmpty();
	}

	@Test
	void handingOverStartsTheHistoryWithThePlansCaregiver() {
		service.reassign(morning, 9L, new VisitReassignment.Change(12L, 41L, 77L, "chosen by the family"));

		Visit visit = visits.findById(morning).orElseThrow();
		assertThat(visit.caregiverId()).isEqualTo(9L);
		assertThat(visit.absenceId()).isEqualTo(12L);
		assertThat(assignments.rows).extracting(VisitAssignment::caregiverId, VisitAssignment::status)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(5L, VisitAssignment.Status.REPLACED),
						org.assertj.core.groups.Tuple.tuple(9L, VisitAssignment.Status.ACTIVE));
		assertThat(assignments.rows.get(1).rosteringCandidateId()).isEqualTo(77L);
		assertThat(assignments.rows.get(1).assignedByUserId()).isEqualTo(41L);

		service.reassign(morning, 10L, new VisitReassignment.Change(12L, null, null, "default plan"));
		assertThat(assignments.rows).extracting(VisitAssignment::status).containsExactly(
				VisitAssignment.Status.REPLACED, VisitAssignment.Status.REPLACED, VisitAssignment.Status.ACTIVE);
	}

	@Test
	void anUncoveredVisitCanStillBeTakenUpOrCalledOff() {
		service.markUncovered(morning, new VisitReassignment.Change(12L, null, null, "nobody free"));
		Visit uncovered = visits.findById(morning).orElseThrow();
		assertThat(uncovered.status()).isEqualTo(Visit.Status.EXCEPTION);
		assertThat(uncovered.caregiverId()).isNull();
		assertThat(assignments.rows).singleElement().extracting(VisitAssignment::status)
				.isEqualTo(VisitAssignment.Status.CANCELLED);

		service.reassign(morning, 9L, new VisitReassignment.Change(12L, 11L, null, "a manager found somebody"));
		assertThat(visits.findById(morning).orElseThrow().status()).isEqualTo(Visit.Status.SCHEDULED);
		assertThat(assignments.findActiveByVisitId(morning)).get().extracting(VisitAssignment::caregiverId).isEqualTo(9L);
	}

	@Test
	void skippingAndMovingCallTheVisitOff() {
		service.callOff(afternoon, new VisitReassignment.Change(12L, 41L, null, "skipped"));
		assertThat(visits.findById(afternoon).orElseThrow().status()).isEqualTo(Visit.Status.CANCELLED);

		Long moved = service.moveTo(morning, EIGHTH.plusDays(2), 5L, new VisitReassignment.Change(12L, 41L, 78L, "moved"));
		Visit original = visits.findById(morning).orElseThrow();
		Visit copy = visits.findById(moved).orElseThrow();
		assertThat(original.status()).isEqualTo(Visit.Status.CANCELLED);
		assertThat(copy.scheduledStart()).isEqualTo(EIGHTH.plusDays(2));
		assertThat(copy.scheduledEnd()).isEqualTo(EIGHTH.plusDays(2).plusMinutes(45));
		assertThat(copy.caregiverId()).isEqualTo(5L);
		assertThat(assignments.rows).filteredOn(a -> a.visitId().equals(moved)).singleElement()
				.extracting(VisitAssignment::rosteringCandidateId).isEqualTo(78L);
	}

	@Test
	void aVisitAlreadyUnderWayOrMissingIsRefused() {
		Long started = visits.save(withStatus(Visit.scheduled(7L, 5L, 4L, 3L, "Care", EIGHTH, null),
				Visit.Status.IN_PROGRESS)).id();
		VisitReassignment.Change why = new VisitReassignment.Change(12L, null, null, "x");

		assertThatThrownBy(() -> service.reassign(started, 9L, why))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageContaining("is IN_PROGRESS and can no longer change hands")
				.extracting("code").isEqualTo("VISIT_NOT_OPEN");
		assertThatThrownBy(() -> service.markUncovered(started, why)).isInstanceOf(BusinessRuleViolation.class);
		assertThatThrownBy(() -> service.callOff(started, why)).isInstanceOf(BusinessRuleViolation.class);
		LocalDateTime tomorrow = EIGHTH.plusDays(1);
		assertThatThrownBy(() -> service.moveTo(started, tomorrow, 9L, why))
				.isInstanceOf(BusinessRuleViolation.class);
		assertThatThrownBy(() -> service.reassign(404L, 9L, why)).isInstanceOf(ResourceNotFound.class);
		assertThat(service.find(404L)).isEmpty();
	}

	private static Visit withStatus(Visit visit, Visit.Status status) {
		return new Visit(visit.id(), visit.elderId(), visit.caregiverId(), visit.carePlanNodeId(), visit.absenceId(),
				visit.serviceType(), visit.scheduledStart(), visit.scheduledEnd(), null, null, status, null,
				visit.carePlanId(), null, null, null);
	}

	/** visit_assignment in a list, in the order rows were written. */
	private static final class Assignments implements VisitAssignmentRepository {

		final List<VisitAssignment> rows = new ArrayList<>();

		@Override
		public Optional<VisitAssignment> findById(Long id) {
			return rows.stream().filter(a -> a.id().equals(id)).findFirst();
		}

		@Override
		public VisitAssignment save(VisitAssignment a) {
			if (a.id() == null) {
				VisitAssignment stored = new VisitAssignment((long) rows.size() + 1, a.visitId(), a.caregiverId(),
						a.assignedByUserId(), a.status(), a.reason(), a.assignedAt(), a.endedAt(), a.rosteringCandidateId());
				rows.add(stored);
				return stored;
			}
			rows.replaceAll(existing -> existing.id().equals(a.id()) ? a : existing);
			return a;
		}

		@Override
		public Optional<VisitAssignment> findActiveByVisitId(Long visitId) {
			return rows.stream().filter(a -> a.visitId().equals(visitId) && a.status() == VisitAssignment.Status.ACTIVE)
					.max(Comparator.comparing(VisitAssignment::id));
		}

		@Override
		public List<VisitAssignment> findByVisitId(Long visitId) {
			return rows.stream().filter(a -> a.visitId().equals(visitId)).toList();
		}
	}
}
