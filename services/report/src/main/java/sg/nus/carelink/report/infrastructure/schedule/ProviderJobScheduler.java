package sg.nus.carelink.report.infrastructure.schedule;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sg.nus.carelink.report.application.ProviderJobService;

/**
 * Nothing but a trigger: calls off the provider jobs whose time to answer ran out, each in a
 * transaction of its own, so a manager takes them over ({@link ProviderJobService#expire}), and forgets
 * old Idempotency-Keys. Every minute by default ({@code carelink.provider-jobs.expire-interval}), on one
 * replica at a time.
 */
@Component
@Profile("!worker")
class ProviderJobScheduler {

	private static final Logger log = LoggerFactory.getLogger(ProviderJobScheduler.class);

	private final ProviderJobService jobs;

	ProviderJobScheduler(ProviderJobService jobs) {
		this.jobs = jobs;
	}

	@SchedulerLock(name = "report.provider-job-expiry", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
	@Scheduled(fixedDelayString = "${carelink.provider-jobs.expire-interval:PT1M}",
			initialDelayString = "${carelink.provider-jobs.expire-initial-delay:PT1M}")
	void expire() {
		int expired = 0;
		for (Long jobId : jobs.unanswered()) {
			try {
				if (jobs.expire(jobId)) {
					expired++;
				}
			}
			catch (RuntimeException failure) {
				log.warn("Provider job {} could not be expired: {}", jobId, failure.getClass().getSimpleName());
			}
		}
		if (expired > 0) {
			log.info("Provider jobs expired unanswered and handed to a manager: {}", expired);
		}
		try {
			jobs.forgetOldKeys();
		}
		catch (RuntimeException failure) {
			log.warn("Old job API keys could not be forgotten: {}", failure.getClass().getSimpleName());
		}
	}
}
