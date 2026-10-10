package sg.nus.carelink.rostering.infrastructure.visit;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import sg.nus.carelink.platform.VisitInCore;
import sg.nus.carelink.rostering.domain.repository.VisitScheduling;

/**
 * {@link VisitScheduling} from visit's service, in the same process, while visit runs inside
 * core. Deleted when visit moves out; {@link VisitApiVisitScheduling} takes over.
 */
@Component
@VisitInCore
class InProcessVisitScheduling implements VisitScheduling {

	private final sg.nus.carelink.visit.application.VisitScheduling visit;

	InProcessVisitScheduling(sg.nus.carelink.visit.application.VisitScheduling visit) {
		this.visit = visit;
	}

	@Override
	public Outcome schedule(List<PlannedVisit> visits) {
		sg.nus.carelink.visit.application.VisitScheduling.Outcome outcome = visit.schedule(visits.stream()
				.map(planned -> new sg.nus.carelink.visit.application.VisitScheduling.PlannedVisit(planned.elderId(),
						planned.caregiverId(), planned.carePlanId(), planned.carePlanNodeId(), planned.serviceType(),
						planned.start(), planned.end()))
				.toList());
		return new Outcome(outcome.created(), outcome.covered());
	}

	@Override
	public int cancelUntouchedFrom(Long carePlanId, LocalDateTime from) {
		return visit.cancelUntouchedFrom(carePlanId, from);
	}

	@Override
	public List<UncoveredVisit> findUncoveredStarted(LocalDateTime since, LocalDateTime now) {
		return visit.findUncoveredStarted(since, now).stream()
				.map(uncovered -> new UncoveredVisit(uncovered.visitId(), uncovered.elderId(), uncovered.start(),
						uncovered.serviceType()))
				.toList();
	}

	@Override
	public boolean markUncoveredAsException(Long visitId) {
		return visit.markUncoveredAsException(visitId);
	}

}
