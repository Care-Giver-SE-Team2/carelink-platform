package sg.nus.carelink.rostering.infrastructure.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import sg.nus.carelink.careplan.application.CarePlanScheduleChanged;
import sg.nus.carelink.profile.application.PrimaryCaregiverChanged;
import sg.nus.carelink.rostering.application.RecurringRosterService;

/**
 * Refreshes an elder's visits as soon as the change behind them is committed: a care plan
 * published or stopped, or a primary caregiver named.
 *
 * <p>Runs after the change commits, and each refresh commits on its own (see
 * {@link RecurringRosterService#refreshElder}), so a roster failure never undoes or fails the
 * manager's publish. It is logged instead, and the nightly run catches the elder up.
 */
@Component
class RosterRefreshListener {

	private static final Logger log = LoggerFactory.getLogger(RosterRefreshListener.class);

	private final RecurringRosterService roster;

	RosterRefreshListener(RecurringRosterService roster) {
		this.roster = roster;
	}

	@TransactionalEventListener
	void onCarePlanScheduleChanged(CarePlanScheduleChanged event) {
		refresh(event.elderId());
	}

	@TransactionalEventListener
	void onPrimaryCaregiverChanged(PrimaryCaregiverChanged event) {
		refresh(event.elderId());
	}

	private void refresh(Long elderId) {
		try {
			roster.refreshElder(elderId);
		} catch (RuntimeException failure) {
			log.error("Roster refresh failed for elder {}; the nightly run will retry", elderId, failure);
		}
	}
}
