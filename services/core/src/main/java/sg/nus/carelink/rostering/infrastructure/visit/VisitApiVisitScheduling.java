package sg.nus.carelink.rostering.infrastructure.visit;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import sg.nus.carelink.platform.VisitOutsideCore;
import sg.nus.carelink.rostering.domain.repository.VisitScheduling;
import sg.nus.carelink.visitapi.VisitApi;

/**
 * {@link VisitScheduling} from visit's internal API, once {@code carelink.visit-api.base-url} is
 * set. Both changes are safe to repeat, so a call that failed half-way can simply be made again.
 */
@Component
@VisitOutsideCore
class VisitApiVisitScheduling implements VisitScheduling {

	private final VisitApi visit;

	VisitApiVisitScheduling(VisitApi visit) {
		this.visit = visit;
	}

	@Override
	public Outcome schedule(List<PlannedVisit> visits) {
		VisitApi.Outcome outcome = visit.schedule(visits.stream()
				.map(planned -> new VisitApi.PlannedVisit(planned.elderId(), planned.caregiverId(), planned.carePlanId(),
						planned.carePlanNodeId(), planned.serviceType(), planned.start(), planned.end()))
				.toList());
		return new Outcome(outcome.created(), outcome.covered());
	}

	@Override
	public int cancelUntouchedFrom(Long carePlanId, LocalDateTime from) {
		return visit.cancelUntouchedFrom(new VisitApi.CancelUntouched(carePlanId, from)).count();
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
		return visit.markUncoveredAsException(visitId).marked();
	}

}
