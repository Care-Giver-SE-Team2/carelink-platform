package sg.nus.carelink.incident.infrastructure.notify;

import java.util.Optional;

import org.springframework.stereotype.Component;
import sg.nus.carelink.visitapi.VisitApi;

/** {@link LatestCaregivers} from visit's internal API. */
@Component
class VisitApiLatestCaregivers implements LatestCaregivers {

	private final VisitApi visit;

	VisitApiLatestCaregivers(VisitApi visit) {
		this.visit = visit;
	}

	@Override
	public Optional<Long> latestCaregiverId(Long elderId) {
		return visit.findLatestCaregiverId(elderId);
	}

}
