package sg.nus.carelink.report.infrastructure.schedule;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sg.nus.carelink.report.application.ReportService;

/**
 * The weekly trigger of UC-MG07 ("每周定时任务运行（周日）").
 *
 * <p>Nothing but a trigger, like the incident module's {@code EscalationScheduler}: which
 * week and which elders are decided in {@link ReportService#generateForLastWeek}, so the run
 * can be tested by calling it, and this file is the only one that knows a timer is involved.
 *
 * <p>Late Sunday in Singapore by default, not in the server's zone: the week a family expects
 * a report for ends at midnight where they live. Both the time and the zone are settings
 * ({@code carelink.report.schedule-cron}, {@code carelink.report.schedule-zone}); a cron of
 * {@code -} switches the run off. A run that fails is logged by the scheduler with its cause
 * and files nothing - the whole run is one transaction - and the next run, or a manager's
 * Generate, simply tries again.
 */
@Component
class ReportScheduler {

	private final ReportService reports;

	ReportScheduler(ReportService reports) {
		this.reports = reports;
	}

	@Scheduled(
			cron = "${carelink.report.schedule-cron:0 0 23 * * SUN}",
			zone = "${carelink.report.schedule-zone:Asia/Singapore}")
	void generateWeeklyReports() {
		reports.generateForLastWeek();
	}
}
