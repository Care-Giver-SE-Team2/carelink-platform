package sg.nus.carelink.report.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * When the weekly reports are generated, as the institution sets it.
 *
 * <p>Bound from {@code carelink.report} so the schedule is a setting rather than a constant.
 * {@code ReportScheduler} reads the same two keys through placeholders, because a
 * {@code @Scheduled} expression cannot call a bean; the defaults here and there are the same,
 * so a deployment with no configuration runs late on Sunday, Singapore time.
 */
@ConfigurationProperties(prefix = "carelink.report")
public class ReportProperties {

	/** Spring cron: second, minute, hour, day of month, month, day of week. "-" switches the run off. */
	private String scheduleCron = "0 0 23 * * SUN";

	/** The zone the cron is read in: the institution's, not the server's. */
	private String scheduleZone = "Asia/Singapore";

	public String getScheduleCron() {
		return scheduleCron;
	}

	public void setScheduleCron(String scheduleCron) {
		this.scheduleCron = scheduleCron;
	}

	public String getScheduleZone() {
		return scheduleZone;
	}

	public void setScheduleZone(String scheduleZone) {
		this.scheduleZone = scheduleZone;
	}
}
