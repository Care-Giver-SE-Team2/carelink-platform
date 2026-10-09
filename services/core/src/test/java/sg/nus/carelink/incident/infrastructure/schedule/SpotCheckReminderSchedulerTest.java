package sg.nus.carelink.incident.infrastructure.schedule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.application.SpotCheckService;

/** The trigger asks for what is due and reminds each in turn; one failure does not stop the rest. */
class SpotCheckReminderSchedulerTest {

	private final SpotCheckService checks = mock(SpotCheckService.class);
	private final SpotCheckReminderScheduler scheduler = new SpotCheckReminderScheduler(checks, Duration.ofDays(1));

	@Test
	void remindsEveryRequestThatIsDueAndCarriesOnPastAFailure() {
		when(checks.dueForReminder(Duration.ofDays(1))).thenReturn(List.of(1L, 2L, 3L));
		when(checks.remind(1L)).thenThrow(new IllegalStateException("database away"));
		when(checks.remind(2L)).thenReturn(true);
		when(checks.remind(3L)).thenReturn(false);

		scheduler.remindFamilies();

		verify(checks).remind(1L);
		verify(checks).remind(2L);
		verify(checks).remind(3L);
	}
}
