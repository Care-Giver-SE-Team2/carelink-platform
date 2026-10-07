package sg.nus.carelink.profile.infrastructure.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import sg.nus.carelink.profile.application.CredentialExpiryService;

/**
 * The daily trigger: that it fires the scan, and when it fires by default. What the scan does is
 * {@code CredentialExpiryServiceTest}'s business.
 */
class CredentialExpirySchedulerTest {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

	@Test
	void firesTheScan() {
		CredentialExpiryService service = mock(CredentialExpiryService.class);
		when(service.scanToday()).thenReturn(new CredentialExpiryService.Result(0, 0, 0));

		new CredentialExpiryScheduler(service).scanDaily();

		verify(service).scanToday();
	}

	@Test
	void runsAtMidnightSingaporeTimeEveryDayByDefault() throws NoSuchMethodException {
		Scheduled scheduled = CredentialExpiryScheduler.class.getDeclaredMethod("scanDaily").getAnnotation(Scheduled.class);

		assertThat(scheduled.cron()).isEqualTo("${carelink.caregiver.expiry-scan-cron:0 0 0 * * *}");
		assertThat(scheduled.zone()).isEqualTo("${carelink.caregiver.expiry-scan-zone:Asia/Singapore}");

		ZonedDateTime lateEvening = ZonedDateTime.of(2026, 10, 4, 23, 0, 0, 0, SINGAPORE);
		assertThat(CronExpression.parse("0 0 0 * * *").next(lateEvening))
				.isEqualTo(ZonedDateTime.of(2026, 10, 5, 0, 0, 0, 0, SINGAPORE));
	}
}
