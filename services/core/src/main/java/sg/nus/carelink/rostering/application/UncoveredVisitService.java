package sg.nus.carelink.rostering.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.visit.application.VisitScheduling;
import sg.nus.carelink.visit.application.VisitScheduling.UncoveredVisit;

/**
 * UC-MG03: a visit still unassigned when it is due to start has failed to be covered. It
 * becomes an exception, and an incident is raised so the manager's Exceptions queue shows
 * the same thing the roster does.
 *
 * <p>Only visits that started within the last {@link #LOOKBACK} are considered, so the
 * first scan after a deploy does not raise incidents for long-past gaps nobody can act on.
 */
@Service
public class UncoveredVisitService {

	static final Duration LOOKBACK = Duration.ofHours(24);

	private final VisitScheduling visits;
	private final IncidentService incidents;
	private final Clock clock;

	public UncoveredVisitService(VisitScheduling visits, IncidentService incidents, Clock clock) {
		this.visits = visits;
		this.incidents = incidents;
		this.clock = clock;
	}

	/** Visits that have reached their start time with nobody assigned, earliest first. */
	@Transactional(readOnly = true)
	public List<UncoveredVisit> startedUncovered() {
		LocalDateTime now = LocalDateTime.now(clock);
		return visits.findUncoveredStarted(now.minus(LOOKBACK), now);
	}

	/**
	 * Marks one uncovered visit as an exception and raises its incident, together or not at
	 * all. Its own transaction, so one visit's failure leaves the others' alone. False when the
	 * visit was covered or started in the meantime.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean escalate(UncoveredVisit visit) {
		if (!visits.markUncoveredAsException(visit.visitId())) {
			return false;
		}
		incidents.raiseForUncoveredVisit(visit.elderId(), visit.visitId(),
				"%s visit at %s started with no caregiver assigned".formatted(
						visit.serviceType() == null ? "A" : visit.serviceType(),
						visit.start().toLocalTime()));
		return true;
	}
}
