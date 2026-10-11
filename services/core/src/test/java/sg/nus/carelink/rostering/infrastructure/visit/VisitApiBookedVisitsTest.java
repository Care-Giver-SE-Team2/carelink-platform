package sg.nus.carelink.rostering.infrastructure.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import sg.nus.carelink.rostering.domain.model.VisitsAtRisk.Booking;
import sg.nus.carelink.visitapi.VisitApi;

/** The bookings visit's internal API answers become rostering's own. */
class VisitApiBookedVisitsTest {

	private static final LocalDateTime FROM = LocalDateTime.of(2026, 10, 12, 8, 0);

	private static final LocalDateTime UNTIL = LocalDateTime.of(2026, 10, 26, 0, 0);

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 13, 9, 0);

	private final VisitApi visit = mock(VisitApi.class);

	@Test
	void visitsAssignmentsBecomeRosteringsBookings() {
		given(visit.unstartedBetween(FROM, UNTIL)).willReturn(List.of(new VisitApi.Assignment(100L, 7L, 21L, NINE)));

		assertThat(new VisitApiBookedVisits(visit).unstartedBetween(FROM, UNTIL)).containsExactly(new Booking(7L, 21L, NINE));
	}

}
