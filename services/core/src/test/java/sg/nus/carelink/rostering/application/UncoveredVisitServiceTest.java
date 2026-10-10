package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.rostering.domain.repository.VisitScheduling;
import sg.nus.carelink.rostering.domain.repository.VisitScheduling.UncoveredVisit;

class UncoveredVisitServiceTest {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 11, 5);
	private static final UncoveredVisit COMPANIONSHIP =
			new UncoveredVisit(140L, 10L, LocalDateTime.of(2026, 10, 2, 11, 0), "Companionship");

	private final FakeVisits visits = new FakeVisits();
	private final IncidentService incidents = mock(IncidentService.class);
	private final UncoveredVisitService service = new UncoveredVisitService(visits, incidents,
			Clock.fixed(NOW.atZone(SINGAPORE).toInstant(), SINGAPORE));

	@Test
	void looksBackADayFromNow() {
		service.startedUncovered();

		assertThat(visits.asked).containsExactly(NOW.minusHours(24), NOW);
	}

	@Test
	void marksTheVisitAndRaisesItsIncident() {
		visits.stillUncovered = true;

		assertThat(service.escalate(COMPANIONSHIP)).isTrue();

		assertThat(visits.marked).containsExactly(140L);
		verify(incidents).raiseForUncoveredVisit(10L, 140L, "Companionship visit at 11:00 started with no caregiver assigned");
	}

	@Test
	void raisesNothingForAVisitCoveredInTheMeantime() {
		visits.stillUncovered = false;

		assertThat(service.escalate(COMPANIONSHIP)).isFalse();

		verify(incidents, never()).raiseForUncoveredVisit(any(), any(), any());
	}

	private static final class FakeVisits implements VisitScheduling {
		final List<LocalDateTime> asked = new ArrayList<>();
		final List<Long> marked = new ArrayList<>();
		boolean stillUncovered;

		@Override
		public Outcome schedule(List<PlannedVisit> visits) {
			return new Outcome(0, 0);
		}

		@Override
		public int cancelUntouchedFrom(Long carePlanId, LocalDateTime from) {
			return 0;
		}

		@Override
		public List<UncoveredVisit> findUncoveredStarted(LocalDateTime since, LocalDateTime now) {
			asked.add(since);
			asked.add(now);
			return List.of();
		}

		@Override
		public boolean markUncoveredAsException(Long visitId) {
			if (stillUncovered) {
				marked.add(visitId);
			}
			return stillUncovered;
		}
	}
}
