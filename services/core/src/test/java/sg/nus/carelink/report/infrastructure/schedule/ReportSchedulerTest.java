package sg.nus.carelink.report.infrastructure.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import sg.nus.carelink.report.application.ReportService;
import sg.nus.carelink.report.infrastructure.config.ReportProperties;

/**
 * The weekly trigger: that it fires the run, and when it fires by default. What the run does is
 * {@code ReportServiceTest}'s business.
 */
class ReportSchedulerTest {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

	@Test
	void firesTheWeeklyRun() {
		ReportService service = mock(ReportService.class);

		new ReportScheduler(service).generateWeeklyReports();

		verify(service).generateForLastWeek();
	}

	/** A9: late on Sunday, Singapore time, unless the institution says otherwise. */
	@Test
	void runsAt2300OnSundaySingaporeTimeByDefault() throws NoSuchMethodException {
		Scheduled scheduled = ReportScheduler.class.getDeclaredMethod("generateWeeklyReports").getAnnotation(Scheduled.class);

		assertThat(scheduled.cron()).isEqualTo("${carelink.report.schedule-cron:0 0 23 * * SUN}");
		assertThat(scheduled.zone()).isEqualTo("${carelink.report.schedule-zone:Asia/Singapore}");

		ZonedDateTime thursdayMorning = ZonedDateTime.of(2026, 9, 24, 10, 0, 0, 0, SINGAPORE);
		assertThat(CronExpression.parse("0 0 23 * * SUN").next(thursdayMorning))
				.isEqualTo(ZonedDateTime.of(2026, 9, 27, 23, 0, 0, 0, SINGAPORE));
	}

	/** The placeholder defaults and the bound settings are the same numbers, so neither can drift alone. */
	@Test
	void thePropertiesDefaultToWhatTheTriggerFallsBackOn() throws NoSuchMethodException {
		Scheduled scheduled = ReportScheduler.class.getDeclaredMethod("generateWeeklyReports").getAnnotation(Scheduled.class);
		ReportProperties defaults = new ReportProperties();

		assertThat(scheduled.cron()).endsWith(":" + defaults.getScheduleCron() + "}");
		assertThat(scheduled.zone()).endsWith(":" + defaults.getScheduleZone() + "}");
	}
}
