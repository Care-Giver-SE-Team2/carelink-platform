package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The saga's retries: every due change is tried again, and one that fails does not stop the rest. */
class VisitChangeStepScanServiceTest {

	private final VisitChangeSaga saga = mock(VisitChangeSaga.class);
	private final AbsenceReRosteringService reRostering = mock(AbsenceReRosteringService.class);
	private final VisitChangeStepScanService scan = new VisitChangeStepScanService(saga, reRostering);

	@Test
	void nothingDueMeansNothingIsTried() {
		when(saga.due()).thenReturn(List.of());

		assertThat(scan.sweep()).isZero();
		verify(saga, never()).tryAgain(any(), any());
	}

	@Test
	void aChangeThatFailsIsLoggedAndTheOthersAreTried() {
		when(saga.handOverGivenUp()).thenReturn(1);
		when(saga.due()).thenReturn(List.of(1L, 2L, 3L));
		doThrow(new IllegalStateException("database went away")).when(saga).tryAgain(eq(2L), any());

		assertThat(scan.sweep()).isEqualTo(3);
		verify(saga).tryAgain(eq(1L), any());
		verify(saga).tryAgain(eq(3L), any());
		verify(saga).handOverGivenUp();
	}
}
