package sg.nus.carelink.rostering.infrastructure.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import sg.nus.carelink.rostering.domain.model.VisitsAtRisk.Booking;
import sg.nus.carelink.rostering.domain.repository.BookedVisits;
import sg.nus.carelink.visit.application.UpcomingAssignments;
import sg.nus.carelink.visitapi.VisitApi;

/**
 * Exactly one of rostering's two {@link BookedVisits} adapters is active: visit's service in core
 * until {@code carelink.visit-api.base-url} is set, visit's internal API from then on. Both hand
 * rostering the same bookings.
 */
class BookedVisitsAdaptersTest {

	private static final LocalDateTime FROM = LocalDateTime.of(2026, 10, 12, 8, 0);

	private static final LocalDateTime UNTIL = LocalDateTime.of(2026, 10, 26, 0, 0);

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 13, 9, 0);

	private final UpcomingAssignments inCore = mock(UpcomingAssignments.class);

	private final VisitApi overHttp = mock(VisitApi.class);

	private final ApplicationContextRunner core = new ApplicationContextRunner()
			.withBean(UpcomingAssignments.class, () -> inCore)
			.withBean(VisitApi.class, () -> overHttp)
			.withUserConfiguration(InProcessBookedVisits.class, VisitApiBookedVisits.class);

	@Test
	void whileVisitRunsInCoreRosteringAsksVisitsService() {
		core.run(context -> assertThat(context).getBean(BookedVisits.class).isInstanceOf(InProcessBookedVisits.class));
	}

	@Test
	void onceVisitsAddressIsSetRosteringAsksVisitsApi() {
		core.withPropertyValues("carelink.visit-api.base-url=http://visit:8080")
				.run(context -> assertThat(context).getBean(BookedVisits.class).isInstanceOf(VisitApiBookedVisits.class));
	}

	@Test
	void bothAdaptersHandRosteringTheSameBookings() {
		given(inCore.unstartedBetween(FROM, UNTIL))
				.willReturn(List.of(new UpcomingAssignments.Assignment(100L, 7L, 21L, NINE)));
		given(overHttp.unstartedBetween(FROM, UNTIL)).willReturn(List.of(new VisitApi.Assignment(100L, 7L, 21L, NINE)));
		Booking booking = new Booking(7L, 21L, NINE);

		assertThat(new InProcessBookedVisits(inCore).unstartedBetween(FROM, UNTIL)).containsExactly(booking);
		assertThat(new VisitApiBookedVisits(overHttp).unstartedBetween(FROM, UNTIL)).containsExactly(booking);
	}

}
