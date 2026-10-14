package sg.nus.carelink.rostering.infrastructure.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import sg.nus.carelink.careplan.application.CarePlanScheduleChanged;
import sg.nus.carelink.profile.application.PrimaryCaregiverChanged;
import sg.nus.carelink.rostering.application.LeaveCoverService;
import sg.nus.carelink.rostering.application.RecurringRosterService;

/**
 * Refreshes an elder's visits as soon as the change behind them is committed: a care plan
 * published or stopped, or a primary caregiver named, replaced or removed. A primary caregiver
 * who was replaced or removed is first taken off the visits nobody has started, so the refresh
 * gives those to the new one, or leaves them to be covered.
 *
 * <p>Runs after the change commits, and each refresh commits on its own (see
 * {@link RecurringRosterService#refreshElder}), so a roster failure never undoes or fails the
 * manager's publish. It is logged instead, and the nightly run catches the elder up. Each
 * refresh is followed by {@link LeaveCoverService}, so a visit it gave to a primary caregiver
 * who is on leave goes to whoever already covers the elder, rather than waiting for midnight.
 */
@Component
class RosterRefreshListener {

	private static final Logger log = LoggerFactory.getLogger(RosterRefreshListener.class);

	private final RecurringRosterService roster;
	private final LeaveCoverService leaveCover;

	RosterRefreshListener(RecurringRosterService roster, LeaveCoverService leaveCover) {
		this.roster = roster;
		this.leaveCover = leaveCover;
	}

	@TransactionalEventListener
	void onCarePlanScheduleChanged(CarePlanScheduleChanged event) {
		refresh(event.elderId());
	}

	@TransactionalEventListener
	void onPrimaryCaregiverChanged(PrimaryCaregiverChanged event) {
		if (event.previousCaregiverId() != null) {
			release(event.elderId(), event.previousCaregiverId());
		}
		refresh(event.elderId());
	}

	/**
	 * Unlike a refresh, the nightly run cannot redo this: nothing left behind says the caregiver
	 * was ever the primary. A failure is logged loudly so a manager can hand the visits over.
	 */
	private void release(Long elderId, Long caregiverId) {
		try {
			roster.releaseFormerPrimary(elderId, caregiverId);
		} catch (RuntimeException failure) {
			log.error("Could not take former primary caregiver {} off elder {}'s visits; they keep them until "
					+ "reassigned by hand", caregiverId, elderId, failure);
		}
	}

	private void refresh(Long elderId) {
		try {
			roster.refreshElder(elderId);
		} catch (RuntimeException failure) {
			log.error("Roster refresh failed for elder {}; the nightly run will retry", elderId, failure);
			return;
		}
		try {
			leaveCover.continueCover(elderId);
		} catch (RuntimeException failure) {
			log.error("Could not give elder {}'s visits on leave days to their cover; the nightly run will retry",
					elderId, failure);
		}
	}
}
