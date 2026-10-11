package sg.nus.carelink.report.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.visitapi.VisitApi;

/** The primary caregiver takes an extra service only when core has them at work and visit has them free. */
class ApiValueAddedVisitAssignmentTest {

	private static final LocalDateTime START = LocalDateTime.of(2026, 10, 24, 10, 0);

	private final CoreApi core = mock(CoreApi.class);
	private final VisitApi visit = mock(VisitApi.class);
	private final ApiValueAddedVisitAssignment assignment = new ApiValueAddedVisitAssignment(core, visit);

	@Test
	void anElderWithoutAPrimaryCaregiverHasNobodyToAsk() {
		when(core.findPrimaryCaregiverId(1L)).thenReturn(Optional.empty());

		assertThat(assignment.chooseCaregiver(1L, START, START.plusHours(3))).isEmpty();
		verify(core, never()).onLeave(any(), any());
		verifyNoInteractions(visit);
	}

	@Test
	void aCaregiverOnLeaveThatDayIsNotChosenAndVisitIsNotAsked() {
		when(core.findPrimaryCaregiverId(1L)).thenReturn(Optional.of(7L));
		when(core.onLeave(7L, START.toLocalDate())).thenReturn(new CoreApi.OnLeave(true));

		assertThat(assignment.chooseCaregiver(1L, START, START.plusHours(3))).isEmpty();
		verifyNoInteractions(visit);
	}

	@Test
	void aCaregiverBusyAnywhereInTheServicesTimeIsNotChosen() {
		when(core.findPrimaryCaregiverId(1L)).thenReturn(Optional.of(7L));
		when(core.onLeave(7L, START.toLocalDate())).thenReturn(new CoreApi.OnLeave(false));
		when(visit.caregiverBusy(7L, START, START.plusHours(3))).thenReturn(new VisitApi.Busy(true));

		assertThat(assignment.chooseCaregiver(1L, START, START.plusHours(3))).isEmpty();
	}

	@Test
	void aFreePrimaryCaregiverIsChosen() {
		when(core.findPrimaryCaregiverId(1L)).thenReturn(Optional.of(7L));
		when(core.onLeave(7L, START.toLocalDate())).thenReturn(new CoreApi.OnLeave(false));
		when(visit.caregiverBusy(7L, START, START.plusHours(3))).thenReturn(new VisitApi.Busy(false));

		assertThat(assignment.chooseCaregiver(1L, START, START.plusHours(3))).contains(7L);
	}

}
