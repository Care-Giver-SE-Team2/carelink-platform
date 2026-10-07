package sg.nus.carelink.rostering.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.VisitsAtRisk;
import sg.nus.carelink.rostering.domain.model.VisitsAtRisk.Booking;

/** UC-MG06: a visit is at risk when it is booked after the certificate lapses and its plan needs it. */
class VisitsAtRiskTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 9, 0);
	private static final LocalDateTime WINDOW_END = LocalDate.of(2026, 10, 16).atStartOfDay();
	private static final Long FIRST_AID = 11L;
	private static final Long DEVI = 201L;

	private final VisitsAtRisk atRisk = new VisitsAtRisk(NOW, WINDOW_END, List.of(
			new Booking(DEVI, 1L, LocalDateTime.of(2026, 10, 5, 8, 0)),
			new Booking(DEVI, 1L, LocalDateTime.of(2026, 10, 6, 8, 0)),
			new Booking(DEVI, 1L, LocalDateTime.of(2026, 10, 7, 8, 0)),
			new Booking(DEVI, 2L, LocalDateTime.of(2026, 10, 7, 14, 0)),
			new Booking(999L, 1L, LocalDateTime.of(2026, 10, 7, 8, 0))),
			Map.of(1L, Set.of(FIRST_AID), 2L, Set.of(12L)));

	@Test
	void countsTheCaregiversVisitsNeedingTheTypeFromTheDayAfterItLapses() {
		assertThat(atRisk.count(DEVI, FIRST_AID, LocalDate.of(2026, 10, 5))).contains(2);
	}

	@Test
	void aCertificateTheyDoNotHoldYetPutsEveryMatchingVisitAtRisk() {
		assertThat(atRisk.count(DEVI, FIRST_AID, NOW.toLocalDate().minusDays(1))).contains(3);
	}

	@Test
	void aCertificateCoveringPastTheWindowHasNoCount() {
		assertThat(atRisk.count(DEVI, FIRST_AID, LocalDate.of(2026, 10, 15))).isEmpty();
		assertThat(atRisk.count(DEVI, FIRST_AID, LocalDate.of(9999, 12, 31))).isEmpty();
	}

	@Test
	void aTypeNoPlanRequiresPutsNothingAtRisk() {
		assertThat(atRisk.count(DEVI, 99L, LocalDate.of(2026, 10, 1))).contains(0);
	}
}
