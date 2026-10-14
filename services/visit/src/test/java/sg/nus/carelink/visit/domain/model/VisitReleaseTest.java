package sg.nus.carelink.visit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/** A primary caregiver who is removed or replaced is taken off the visits nobody has started. */
class VisitReleaseTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 8, 9, 0);

	@Test
	void anUnstartedVisitLosesItsCaregiverAndStaysScheduled() {
		Visit released = visit(5L, Visit.Status.SCHEDULED).released();

		assertThat(released.caregiverId()).isNull();
		assertThat(released.status()).isEqualTo(Visit.Status.SCHEDULED);
		assertThat(released.id()).isEqualTo(30L);
	}

	@Test
	void aVisitUnderWayOrWithNobodyOnItCannotBeReleased() {
		assertThatThrownBy(() -> visit(5L, Visit.Status.IN_PROGRESS).released())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("IN_PROGRESS");
		assertThatThrownBy(() -> visit(null, Visit.Status.SCHEDULED).released())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("unassigned");
	}

	private static Visit visit(Long caregiverId, Visit.Status status) {
		return new Visit(30L, 42L, caregiverId, 300L, null, "Bathing assistance", NINE, NINE.plusMinutes(30), null,
				null, status, null, 9L, 1, null, null);
	}
}
