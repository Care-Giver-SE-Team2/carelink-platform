package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.application.UpcomingAssignments;
import sg.nus.carelink.visit.application.VisitScheduling;
import sg.nus.carelink.visitapi.VisitApi;

class InternalVisitPlanningControllerTest {

	private final VisitScheduling scheduling = mock(VisitScheduling.class);
	private final InternalVisitPlanningController controller =
			new InternalVisitPlanningController(scheduling, mock(UpcomingAssignments.class));

	@Test
	void releasesAFormerPrimaryCaregiversUntouchedVisitsAndSaysHowMany() {
		LocalDateTime from = LocalDateTime.of(2026, 10, 8, 9, 0);
		when(scheduling.releaseUntouchedFrom(42L, 5L, from)).thenReturn(3);

		assertThat(controller.releaseUntouchedFrom(new VisitApi.ReleaseUntouched(42L, 5L, from)))
				.isEqualTo(new VisitApi.Released(3));
	}
}
