package sg.nus.carelink.incident.infrastructure.schedule;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sg.nus.carelink.incident.application.SpotCheckService;

/**
 * The clock behind the family's reminder (UC-MG08 with the family's side, exception 1a): once
 * an hour by default, every request the family has left unanswered for a day is asked about
 * again. The rule is in {@link SpotCheckService}; this file is the only one that knows a timer
 * is involved. Each reminder is its own transaction, so one failure is logged and skipped.
 */
@Component
class SpotCheckReminderScheduler {

	private static final Logger log = LoggerFactory.getLogger(SpotCheckReminderScheduler.class);

	private final SpotCheckService checks;
	private final Duration after;

	SpotCheckReminderScheduler(SpotCheckService checks,
			@Value("${carelink.spot-checks.consent-reminder-after:P1D}") Duration after) {
		this.checks = checks;
		this.after = after;
	}

	@SchedulerLock(name = "incident.spot-check-reminders", lockAtMostFor = "PT1H", lockAtLeastFor = "PT55M")
	@Scheduled(
			fixedDelayString = "${carelink.spot-checks.reminder-scan-interval:PT1H}",
			initialDelayString = "${carelink.spot-checks.reminder-scan-initial-delay:PT3M}")
	void remindFamilies() {
		int reminded = 0;
		for (Long checkId : checks.dueForReminder(after)) {
			try {
				if (checks.remind(checkId)) {
					reminded++;
				}
			} catch (RuntimeException failure) {
				log.error("Could not remind the family about spot check {}", checkId, failure);
			}
		}
		if (reminded > 0) {
			log.info("Families reminded about spot checks: {}", reminded);
		}
	}
}
