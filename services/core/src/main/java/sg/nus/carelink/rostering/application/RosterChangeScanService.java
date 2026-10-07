package sg.nus.carelink.rostering.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.repository.RosterChangeRepository;

/**
 * UC-MG04 alternative 4a: the scan that makes a family's deadline mean something. It finds the
 * changes whose family did not answer in time and has the default plan run for each.
 *
 * <p>Deliberately not transactional, like the escalation scan: each change is settled in its
 * own transaction through {@link AbsenceReRosteringService}'s proxy, so one change that cannot be
 * settled is logged and retried on the next sweep without undoing the others.
 */
@Service
public class RosterChangeScanService {

	private static final Logger log = LoggerFactory.getLogger(RosterChangeScanService.class);

	private final RosterChangeRepository changes;
	private final AbsenceReRosteringService reRostering;
	private final Clock clock;

	RosterChangeScanService(RosterChangeRepository changes, AbsenceReRosteringService reRostering, Clock clock) {
		this.changes = changes;
		this.reRostering = reRostering;
		this.clock = clock;
	}

	/**
	 * One sweep.
	 *
	 * @return how many default plans ran; the others were answered in the meantime
	 */
	public int sweep() {
		LocalDateTime now = LocalDateTime.now(clock);
		List<RosterChange> due = changes.findAwaitingFamilyDueBy(now);
		if (due.isEmpty()) {
			return 0;
		}
		int applied = 0;
		for (RosterChange change : due) {
			try {
				if (reRostering.applyDefaultIfStillDue(change.id())) {
					applied++;
				}
			}
			catch (RuntimeException failure) {
				log.error("Default plan failed for roster change {}", change.id(), failure);
			}
		}
		log.info("Roster change sweep at {}: {} due, {} default plans applied", now, due.size(), applied);
		return applied;
	}
}
