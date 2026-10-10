package sg.nus.carelink.incident.infrastructure.lookup;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import sg.nus.carelink.incident.domain.repository.SpotCheckLookups.VisitFacts;
import sg.nus.carelink.platform.VisitOutsideCore;
import sg.nus.carelink.visitapi.VisitApi;

/** {@link SpotCheckVisits} from visit's internal API, once {@code carelink.visit-api.base-url} is set. */
@Component
@VisitOutsideCore
class VisitApiSpotCheckVisits implements SpotCheckVisits {

	private final VisitApi visit;

	VisitApiSpotCheckVisits(VisitApi visit) {
		this.visit = visit;
	}

	@Override
	public Optional<VisitFacts> visit(Long visitId) {
		return visit.findVisit(visitId).map(slot -> new VisitFacts(slot.visitId(), slot.elderId(), slot.caregiverId(),
				slot.start(), slot.status(), slot.serviceType()));
	}

	@Override
	public List<VisitFacts> upcomingVisits(Long elderId, LocalDateTime from, LocalDateTime until) {
		return visit.upcomingVisits(elderId, from, until).stream()
				.map(upcoming -> new VisitFacts(upcoming.visitId(), upcoming.elderId(), upcoming.caregiverId(),
						upcoming.start(), upcoming.status(), upcoming.serviceType()))
				.toList();
	}

}
