package sg.nus.carelink.rostering.infrastructure.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sg.nus.carelink.rostering.application.RecurringRosterService;
import sg.nus.carelink.rostering.application.UncoveredVisitService;

/**
 * The nightly trigger of UC-MG03: rolls every elder's visit window forward a day, so a plan
 * published once keeps producing visits. It also runs once at start-up, so plans published
 * before this feature existed, or while the application was down over the nightly run, get
 * their visits straight away instead of at the next 02:30. What each refresh does is decided in
 * {@link RecurringRosterService}; this file is the only one that knows a timer is involved.
 *
 * <p>Each elder is refreshed in a transaction of their own, so one elder's failure is logged
 * and skipped rather than holding back everyone else's visits; the next run tries again.
 * Early morning in Singapore by default ({@code carelink.roster.schedule-cron},
 * {@code carelink.roster.schedule-zone}); a cron of {@code -} switches the run off.
 */
@Component
class RosterScheduler {

	private static final Logger log = LoggerFactory.getLogger(RosterScheduler.class);

	private final RecurringRosterService roster;
	private final UncoveredVisitService uncovered;

	RosterScheduler(RecurringRosterService roster, UncoveredVisitService uncovered) {
		this.roster = roster;
		this.uncovered = uncovered;
	}

	/**
	 * Every minute by default: any visit that has reached its start time with nobody
	 * assigned becomes an exception, with an incident in the manager's queue.
	 */
	@Scheduled(
			fixedDelayString = "${carelink.roster.uncovered-scan-interval:PT60S}",
			initialDelayString = "${carelink.roster.uncovered-scan-initial-delay:PT30S}")
	void flagUncoveredVisits() {
		int flagged = 0;
		for (var visit : uncovered.startedUncovered()) {
			try {
				if (uncovered.escalate(visit)) {
					flagged++;
				}
			} catch (RuntimeException failure) {
				log.error("Could not flag uncovered visit {}", visit.visitId(), failure);
			}
		}
		if (flagged > 0) {
			log.info("Uncovered visits flagged as exceptions: {}", flagged);
		}
	}

	@Scheduled(
			cron = "${carelink.roster.schedule-cron:0 30 2 * * *}",
			zone = "${carelink.roster.schedule-zone:Asia/Singapore}")
	void refreshNightly() {
		refreshAllElders();
	}

	@EventListener(ApplicationReadyEvent.class)
	void catchUpOnStartup() {
		refreshAllElders();
	}

	private void refreshAllElders() {
		int created = 0;
		int cancelled = 0;
		for (Long elderId : roster.eldersToRefresh()) {
			try {
				var refresh = roster.refreshElder(elderId);
				created += refresh.created();
				cancelled += refresh.cancelled();
			} catch (RuntimeException failure) {
				log.error("Roster refresh failed for elder {}", elderId, failure);
			}
		}
		log.info("Roster refresh: {} visits created, {} cancelled", created, cancelled);
	}
}
