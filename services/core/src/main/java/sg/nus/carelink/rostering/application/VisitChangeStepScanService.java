package sg.nus.carelink.rostering.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The re-rostering saga's retries: finds the changes visit has not answered yet whose next try is due,
 * and tries each again with its request key ({@link VisitChangeSaga}). What a change leads to once made
 * is settled the way {@link AbsenceReRosteringService} settles it. A change given up whose manager's
 * incident could not be raised then gets it now.
 *
 * <p>Deliberately not transactional, like the default plan's sweep: each step is tried, and settled, in
 * transactions of its own, so one that fails is logged and tried on the next sweep without holding back
 * the others.
 */
@Service
public class VisitChangeStepScanService {

	private static final Logger log = LoggerFactory.getLogger(VisitChangeStepScanService.class);

	private final VisitChangeSaga saga;
	private final AbsenceReRosteringService reRostering;

	VisitChangeStepScanService(VisitChangeSaga saga, AbsenceReRosteringService reRostering) {
		this.saga = saga;
		this.reRostering = reRostering;
	}

	/**
	 * One sweep.
	 *
	 * @return how many steps were tried
	 */
	public int sweep() {
		int raised = saga.handOverGivenUp();
		if (raised > 0) {
			log.info("Visit change sweep: {} incidents raised for changes given up", raised);
		}
		List<Long> due = saga.due();
		if (due.isEmpty()) {
			return 0;
		}
		for (Long stepId : due) {
			try {
				saga.tryAgain(stepId, reRostering::settle);
			}
			catch (RuntimeException failure) {
				log.error("Visit change step {} could not be tried again", stepId, failure);
			}
		}
		log.info("Visit change sweep: {} steps tried again", due.size());
		return due.size();
	}
}
