package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;

/** UC-MG04 alternative 4a: one change that cannot be settled does not stop the rest. */
class RosterChangeScanServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 11, 0);

	private final ReRosteringFakes.Changes changes = new ReRosteringFakes.Changes();
	private final AbsenceReRosteringService reRostering = mock(AbsenceReRosteringService.class);
	private final RosterChangeScanService scan = new RosterChangeScanService(changes, reRostering,
			new ReRosteringFakes.MutableClock(NOW, ZoneId.of("Asia/Singapore")));

	@Test
	void nothingDueMeansNothingRuns() {
		assertThat(scan.sweep()).isZero();
	}

	@Test
	void aFailingChangeIsSkippedAndTheOthersAreSettled() {
		Long failing = changes.save(due(30L)).id();
		Long fine = changes.save(due(31L)).id();
		Long answeredMeanwhile = changes.save(due(32L)).id();
		when(reRostering.applyDefaultIfStillDue(failing)).thenThrow(new IllegalStateException("visit locked"));
		when(reRostering.applyDefaultIfStillDue(fine)).thenReturn(true);
		when(reRostering.applyDefaultIfStillDue(answeredMeanwhile)).thenReturn(false);

		assertThat(scan.sweep()).isEqualTo(1);
		verify(reRostering).applyDefaultIfStillDue(fine);
		verify(reRostering).applyDefaultIfStillDue(answeredMeanwhile);
	}

	private static RosterChange due(Long visitId) {
		VacatedSlot slot = new VacatedSlot(visitId, 7L, 4L, null, NOW.plusDays(1), null, 5L);
		return RosterChange.offered(12L, slot, 1L, 9L, NOW.minusMinutes(1), NOW.minusHours(2));
	}
}
