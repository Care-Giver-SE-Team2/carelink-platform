package sg.nus.carelink.incident.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.repository.IncidentRepository;

/**
 * UC-SYS02: the scan that makes a response countdown mean something.
 *
 * <p>Without it the deadline on an incident would be decoration. It looks for incidents
 * that are still waiting to be taken over and whose countdown has run out, and asks
 * {@link EscalationService} to move each one to the next level of its chain.
 *
 * <p>This class is deliberately <strong>not</strong> transactional. Each incident is
 * escalated in its own transaction, which only works because the call goes out to another
 * bean and therefore through the proxy that applies {@code REQUIRES_NEW}; calling a
 * transactional method on {@code this} would silently do nothing, and one failing incident
 * would roll back every other escalation in the same sweep.
 */
@Service
public class EscalationScanService {

	private static final Logger log = LoggerFactory.getLogger(EscalationScanService.class);

	private final IncidentRepository incidents;
	private final EscalationService escalation;
	private final Clock clock;

	EscalationScanService(IncidentRepository incidents, EscalationService escalation, Clock clock) {
		this.incidents = incidents;
		this.escalation = escalation;
		this.clock = clock;
	}

	/**
	 * One sweep.
	 *
	 * @return how many incidents actually moved on; the rest were taken over in the meantime
	 */
	public int sweep() {
		LocalDateTime now = LocalDateTime.now(clock);
		List<Incident> overdue = incidents.findAwaitingTakeOverPastDeadline(now);

		if (overdue.isEmpty()) {
			return 0;
		}

		int escalated = 0;
		for (Incident candidate : overdue) {
			try {
				if (escalation.escalateIfStillOverdue(candidate.id(), now)) {
					escalated++;
				}
			}
			catch (RuntimeException failure) {
				// One incident that cannot be escalated must not stop the sweep. It stays
				// overdue and the next sweep tries again; the failure is visible in the log
				// with the request id and the incident it belongs to.
				log.error("Escalation failed for incident {}", candidate.id(), failure);
			}
		}

		log.info("Escalation sweep at {}: {} overdue, {} escalated", now, overdue.size(), escalated);
		return escalated;
	}
}
