package sg.nus.carelink.rostering.infrastructure.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sg.nus.carelink.rostering.application.AbsenceCoverageService;
import sg.nus.carelink.rostering.application.LeaveCoverService;
import sg.nus.carelink.rostering.application.LeaveReminderService;
import sg.nus.carelink.rostering.application.RecurringRosterService;
import sg.nus.carelink.rostering.application.UncoveredVisitService;

/**
 * The nightly trigger of UC-MG03: rolls every elder's visit window forward a day, so a plan
 * published once keeps producing visits. It also runs once at start-up, so plans published
 * before this feature existed, or while the application was down over the nightly run, get
 * their visits straight away instead of at the next midnight. What each refresh does is decided in
 * {@link RecurringRosterService}; this file is the only one that knows a timer is involved.
 * Each elder's refresh is followed by {@link LeaveCoverService}: visits it gave to a primary
 * caregiver who is on leave go to whoever already covers the elder for that absence.
 * After the refresh, absences whose coverage was confirmed are checked again, since the refresh
 * may have put new visits on their days; any it did are re-rostered ({@link AbsenceCoverageService}).
 *
 * <p>Each elder is refreshed in a transaction of their own, so one elder's failure is logged
 * and skipped rather than holding back everyone else's visits; the next run tries again.
 * Midnight in Singapore by default ({@code carelink.roster.schedule-cron},
 * {@code carelink.roster.schedule-zone}); a cron of {@code -} switches the run off.
 */
@Component
class RosterScheduler {

	private static final Logger log = LoggerFactory.getLogger(RosterScheduler.class);

	private final RecurringRosterService roster;
	private final UncoveredVisitService uncovered;
	private final AbsenceCoverageService coverage;
	private final LeaveCoverService leaveCover;
	private final LeaveReminderService leaveReminders;

	RosterScheduler(RecurringRosterService roster, UncoveredVisitService uncovered, AbsenceCoverageService coverage,
			LeaveCoverService leaveCover, LeaveReminderService leaveReminders) {
		this.roster = roster;
		this.uncovered = uncovered;
		this.coverage = coverage;
		this.leaveCover = leaveCover;
		this.leaveReminders = leaveReminders;
	}

	/**
	 * Every minute by default: any visit that has reached its start time with nobody
	 * assigned becomes an exception, with an incident in the manager's queue.
	 */
	@SchedulerLock(name = "rostering.uncovered-visits", lockAtMostFor = "PT5M", lockAtLeastFor = "PT50S")
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

	/**
	 * Every 15 minutes by default: a visit on approved leave that starts within a day and is
	 * still with the caregiver who is away gets an incident, once, so a manager re-rosters it.
	 */
	@SchedulerLock(name = "rostering.leave-reminders", lockAtMostFor = "PT15M", lockAtLeastFor = "PT14M")
	@Scheduled(
			fixedDelayString = "${carelink.roster.leave-reminder-interval:PT15M}",
			initialDelayString = "${carelink.roster.leave-reminder-initial-delay:PT2M}")
	void remindOfLeaveVisits() {
		int raised = 0;
		for (var due : leaveReminders.dueStillOnLeave()) {
			try {
				if (leaveReminders.remind(due)) {
					raised++;
				}
			} catch (RuntimeException failure) {
				log.error("Could not raise the leave reminder for visit {}", due.visitId(), failure);
			}
		}
		if (raised > 0) {
			log.info("Visits on leave still not re-rostered, incidents raised: {}", raised);
		}
	}

	@SchedulerLock(name = "rostering.nightly-refresh", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
	@Scheduled(
			cron = "${carelink.roster.schedule-cron:0 0 0 * * *}",
			zone = "${carelink.roster.schedule-zone:Asia/Singapore}")
	void refreshNightly() {
		refreshAllElders();
	}

	@SchedulerLock(name = "rostering.nightly-refresh", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
	@EventListener(ApplicationReadyEvent.class)
	void catchUpOnStartup() {
		refreshAllElders();
	}

	private void refreshAllElders() {
		int created = 0;
		int cancelled = 0;
		int covered = 0;
		for (Long elderId : roster.eldersToRefresh()) {
			try {
				var refresh = roster.refreshElder(elderId);
				created += refresh.created();
				cancelled += refresh.cancelled();
			} catch (RuntimeException failure) {
				log.error("Roster refresh failed for elder {}", elderId, failure);
				continue;
			}
			try {
				covered += leaveCover.continueCover(elderId);
			} catch (RuntimeException failure) {
				log.error("Could not give elder {}'s visits on leave days to their cover", elderId, failure);
			}
		}
		log.info("Roster refresh: {} visits created, {} cancelled, {} given to a cover during leave", created, cancelled,
				covered);
		recheckConfirmedAbsences();
	}

	/** The refresh may have put new visits on the days of an absence a manager already confirmed. */
	private void recheckConfirmedAbsences() {
		int reopened = 0;
		for (Long absenceId : coverage.confirmedAbsencesStillAhead()) {
			try {
				if (coverage.rerosterAddedVisits(absenceId)) {
					reopened++;
				}
			} catch (RuntimeException failure) {
				log.error("Could not recheck coverage of absence {}", absenceId, failure);
			}
		}
		if (reopened > 0) {
			log.info("Absences with added visits re-rostered and reopened for review: {}", reopened);
		}
	}
}
