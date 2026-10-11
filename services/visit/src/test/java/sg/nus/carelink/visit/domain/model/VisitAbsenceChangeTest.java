package sg.nus.carelink.visit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * UC-MG04's changes to a visit and to its assignment history: a visit nobody has started may
 * change hands, be left uncovered, be called off or be moved; one an absence left uncovered may
 * still be taken up; anything already under way may not.
 */
class VisitAbsenceChangeTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 8, 9, 0);

	@Test
	void anUnstartedVisitChangesHandsAndRemembersTheAbsence() {
		Visit reassigned = scheduled().reassignedForAbsence(9L, 12L);

		assertThat(reassigned.caregiverId()).isEqualTo(9L);
		assertThat(reassigned.absenceId()).isEqualTo(12L);
		assertThat(reassigned.status()).isEqualTo(Visit.Status.SCHEDULED);
		assertThat(reassigned.id()).isEqualTo(30L);
	}

	@Test
	void nobodyFreeMakesTheVisitAnExceptionAReplacementCanStillTake() {
		Visit uncovered = scheduled().uncoveredForAbsence(12L);

		assertThat(uncovered.status()).isEqualTo(Visit.Status.EXCEPTION);
		assertThat(uncovered.caregiverId()).isNull();
		assertThat(uncovered.leftUncoveredByAbsence()).isTrue();

		Visit takenUp = uncovered.reassignedForAbsence(10L, 12L);
		assertThat(takenUp.status()).isEqualTo(Visit.Status.SCHEDULED);
		assertThat(takenUp.caregiverId()).isEqualTo(10L);
		assertThat(uncovered.calledOffForAbsence(12L).status()).isEqualTo(Visit.Status.CANCELLED);
	}

	@Test
	void anExceptionThatIsNotAnAbsenceGapStaysTheManagersBusiness() {
		Visit missedCheckIn = withStatus(Visit.Status.EXCEPTION, null, null);

		assertThat(missedCheckIn.leftUncoveredByAbsence()).isFalse();
		assertThatThrownBy(() -> missedCheckIn.reassignedForAbsence(9L, 12L)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aVisitUnderWayCanNoLongerChange() {
		Visit arrived = withStatus(Visit.Status.ARRIVED, 5L, null);

		assertThatThrownBy(() -> arrived.reassignedForAbsence(9L, 12L))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("can no longer be reassigned");
		assertThatThrownBy(() -> arrived.uncoveredForAbsence(12L)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> arrived.calledOffForAbsence(12L)).isInstanceOf(IllegalStateException.class);
		Visit uncovered = scheduled().uncoveredForAbsence(12L);
		assertThatThrownBy(() -> uncovered.uncoveredForAbsence(12L))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aMovedVisitIsANewVisitOfTheSameLengthAndTheOldOneIsCalledOff() {
		Visit original = scheduled();

		Visit moved = original.movedTo(NINE.plusDays(3), 5L, 12L);
		assertThat(moved.id()).isNull();
		assertThat(moved.scheduledStart()).isEqualTo(NINE.plusDays(3));
		assertThat(moved.scheduledEnd()).isEqualTo(NINE.plusDays(3).plusMinutes(45));
		assertThat(moved.caregiverId()).isEqualTo(5L);
		assertThat(moved.carePlanNodeId()).isEqualTo(3L);
		assertThat(moved.absenceId()).isEqualTo(12L);
		assertThat(moved.status()).isEqualTo(Visit.Status.SCHEDULED);
		assertThat(withStatus(Visit.Status.SCHEDULED, 5L, null).movedTo(NINE.plusDays(1), 5L, 12L).scheduledEnd())
				.isNull();

		Visit calledOff = original.calledOffForAbsence(12L);
		assertThat(calledOff.status()).isEqualTo(Visit.Status.CANCELLED);
		assertThat(calledOff.caregiverId()).isEqualTo(5L);
	}

	@Test
	void anAssignmentEndsOnceAndKeepsWhyItEnded() {
		VisitAssignment active = VisitAssignment.active(30L, 9L, 11L, "covering an absence", 77L, NINE);
		assertThat(active.status()).isEqualTo(VisitAssignment.Status.ACTIVE);
		assertThat(active.rosteringCandidateId()).isEqualTo(77L);

		VisitAssignment stored = new VisitAssignment(1L, 30L, 9L, 11L, VisitAssignment.Status.ACTIVE, null, NINE, null, null);
		VisitAssignment replaced = stored.replaced("handed on", NINE.plusHours(1));
		assertThat(replaced.status()).isEqualTo(VisitAssignment.Status.REPLACED);
		assertThat(replaced.reason()).isEqualTo("handed on");
		assertThat(replaced.endedAt()).isEqualTo(NINE.plusHours(1));

		VisitAssignment cancelled = active.cancelled("skipped", NINE.plusHours(1));
		assertThat(cancelled.reason()).isEqualTo("covering an absence; ended: skipped");
		assertThatThrownBy(() -> cancelled.replaced("again", NINE)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aVisitRosteredBeforeAnyAssignmentRowGetsItsFirstCaregiverOnRecord() {
		VisitAssignment earlier = VisitAssignment.earlier(30L, 5L, VisitAssignment.Status.REPLACED, "plan", null, NINE);
		assertThat(earlier.assignedAt()).isEqualTo(NINE);
		assertThat(earlier.endedAt()).isEqualTo(NINE);
		assertThat(VisitAssignment.earlier(30L, 5L, VisitAssignment.Status.CANCELLED, "plan", NINE.minusDays(9), NINE)
				.assignedAt()).isEqualTo(NINE.minusDays(9));
		assertThat(VisitAssignment.active(30L, 9L, null, "y".repeat(300), null, NINE).reason())
				.hasSize(VisitAssignment.REASON_LENGTH);
	}

	private static Visit scheduled() {
		return new Visit(30L, 7L, 5L, 3L, null, "Personal care", NINE, NINE.plusMinutes(45), null, null,
				Visit.Status.SCHEDULED, null, 4L, 0, null, null);
	}

	private static Visit withStatus(Visit.Status status, Long caregiverId, LocalDateTime end) {
		return new Visit(30L, 7L, caregiverId, 3L, null, "Personal care", NINE, end, null, null, status, null, 4L, 0,
				null, null);
	}
}
