package sg.nus.carelink.incident.infrastructure.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import sg.nus.carelink.incident.domain.repository.SpotCheckLookups.VisitFacts;
import sg.nus.carelink.visitapi.VisitApi;

/**
 * Exactly one of the spot checks' visit adapters is active: the visit table in core until
 * {@code carelink.visit-api.base-url} is set, visit's internal API from then on. The SQL one is
 * covered by the spot check integration tests; this checks the switch and the API one.
 */
class SpotCheckVisitsAdaptersTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 14, 9, 0);

	private final VisitApi overHttp = mock(VisitApi.class);

	private final ApplicationContextRunner core = new ApplicationContextRunner()
			.withBean(JdbcClient.class, () -> mock(JdbcClient.class))
			.withBean(VisitApi.class, () -> overHttp)
			.withUserConfiguration(JdbcSpotCheckVisits.class, VisitApiSpotCheckVisits.class);

	@Test
	void exactlyOneAdapterIsActive() {
		core.run(context -> assertThat(context).getBean(SpotCheckVisits.class).isInstanceOf(JdbcSpotCheckVisits.class));
		core.withPropertyValues("carelink.visit-api.base-url=http://visit:8080")
				.run(context -> assertThat(context).getBean(SpotCheckVisits.class).isInstanceOf(VisitApiSpotCheckVisits.class));
	}

	@Test
	void visitsAnswersBecomeTheSpotChecksFacts() {
		when(overHttp.findVisit(812L)).thenReturn(Optional.of(new VisitApi.VisitSlot(812L, 101L, 7L, 21L, "BATHING", NINE,
				NINE.plusHours(1), "SCHEDULED", null)));
		when(overHttp.upcomingVisits(101L, NINE, NINE.plusDays(7)))
				.thenReturn(List.of(new VisitApi.ElderVisit(812L, 101L, 7L, NINE, "SCHEDULED", "BATHING")));
		VisitFacts facts = new VisitFacts(812L, 101L, 7L, NINE, "SCHEDULED", "BATHING");
		VisitApiSpotCheckVisits visits = new VisitApiSpotCheckVisits(overHttp);

		assertThat(visits.visit(812L)).contains(facts);
		assertThat(visits.visit(999L)).isEmpty();
		assertThat(visits.upcomingVisits(101L, NINE, NINE.plusDays(7))).containsExactly(facts);
	}

}
