package sg.nus.carelink.profile.infrastructure.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sg.nus.carelink.profile.application.CredentialExpiryService;

/**
 * The daily trigger of SYS01: midnight in Singapore by default
 * ({@code carelink.caregiver.expiry-scan-cron}, {@code carelink.caregiver.expiry-scan-zone});
 * a cron of {@code -} switches it off. What the scan does is {@link CredentialExpiryService}.
 *
 * <p>No start-up run, unlike the roster refresh: nothing is lost by waiting for the next one,
 * because a certificate whose date has passed is still found by it, and a run on every start
 * would write notifications into whatever database a test or demo brings up.
 */
@Component
class CredentialExpiryScheduler {

	private static final Logger log = LoggerFactory.getLogger(CredentialExpiryScheduler.class);

	private final CredentialExpiryService expiry;

	CredentialExpiryScheduler(CredentialExpiryService expiry) {
		this.expiry = expiry;
	}

	@SchedulerLock(name = "profile.credential-expiry-scan", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
	@Scheduled(
			cron = "${carelink.caregiver.expiry-scan-cron:0 0 0 * * *}",
			zone = "${carelink.caregiver.expiry-scan-zone:Asia/Singapore}")
	void scanDaily() {
		var result = expiry.scanToday();
		log.info("Credential expiry scan: {} expiring, {} expired, {} notifications",
				result.expiring(), result.expired(), result.notified());
	}
}
