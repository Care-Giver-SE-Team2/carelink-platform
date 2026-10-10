package sg.nus.carelink.incident.infrastructure.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import sg.nus.carelink.visitapi.VisitApi;

/**
 * Exactly one of the incident alert's latest-caregiver adapters is active: the visit table in core
 * until {@code carelink.visit-api.base-url} is set, visit's internal API from then on. The SQL one
 * is covered by the incident integration tests; this checks the switch and the API one.
 */
class LatestCaregiversAdaptersTest {

	private final VisitApi overHttp = mock(VisitApi.class);

	private final ApplicationContextRunner core = new ApplicationContextRunner()
			.withBean(JdbcClient.class, () -> mock(JdbcClient.class))
			.withBean(VisitApi.class, () -> overHttp)
			.withUserConfiguration(JdbcLatestCaregivers.class, VisitApiLatestCaregivers.class);

	@Test
	void exactlyOneAdapterIsActive() {
		core.run(context -> assertThat(context).getBean(LatestCaregivers.class).isInstanceOf(JdbcLatestCaregivers.class));
		core.withPropertyValues("carelink.visit-api.base-url=http://visit:8080")
				.run(context -> assertThat(context).getBean(LatestCaregivers.class).isInstanceOf(VisitApiLatestCaregivers.class));
	}

	@Test
	void visitNamesTheCaregiverOrNobody() {
		when(overHttp.findLatestCaregiverId(101L)).thenReturn(Optional.of(7L));
		when(overHttp.findLatestCaregiverId(102L)).thenReturn(Optional.empty());
		VisitApiLatestCaregivers caregivers = new VisitApiLatestCaregivers(overHttp);

		assertThat(caregivers.latestCaregiverId(101L)).contains(7L);
		assertThat(caregivers.latestCaregiverId(102L)).isEmpty();
	}

}
