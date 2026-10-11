package sg.nus.carelink.incident.infrastructure.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import sg.nus.carelink.incident.domain.repository.SpotCheckLookups.VisitFacts;
import sg.nus.carelink.visitapi.VisitApi;

/** What visit's internal API answers becomes the spot checks' visit facts. */
class VisitApiSpotCheckVisitsTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 14, 9, 0);

	private final VisitApi visit = mock(VisitApi.class);

	@Test
	void visitsAnswersBecomeTheSpotChecksFacts() {
		when(visit.findVisit(812L)).thenReturn(Optional.of(new VisitApi.VisitSlot(812L, 101L, 7L, 21L, "BATHING", NINE,
				NINE.plusHours(1), "SCHEDULED", null)));
		when(visit.upcomingVisits(101L, NINE, NINE.plusDays(7)))
				.thenReturn(List.of(new VisitApi.ElderVisit(812L, 101L, 7L, NINE, "SCHEDULED", "BATHING")));
		VisitFacts facts = new VisitFacts(812L, 101L, 7L, NINE, "SCHEDULED", "BATHING");
		VisitApiSpotCheckVisits visits = new VisitApiSpotCheckVisits(visit);

		assertThat(visits.visit(812L)).contains(facts);
		assertThat(visits.visit(999L)).isEmpty();
		assertThat(visits.upcomingVisits(101L, NINE, NINE.plusDays(7))).containsExactly(facts);
	}

}
