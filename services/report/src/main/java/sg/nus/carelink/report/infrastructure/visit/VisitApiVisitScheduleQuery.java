package sg.nus.carelink.report.infrastructure.visit;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import sg.nus.carelink.report.application.VisitScheduleQuery;
import sg.nus.carelink.visitapi.VisitApi;

/** {@link VisitScheduleQuery} from visit's internal API. */
@Component
class VisitApiVisitScheduleQuery implements VisitScheduleQuery {

	private final VisitApi visit;

	VisitApiVisitScheduleQuery(VisitApi visit) {
		this.visit = visit;
	}

	@Override
	public boolean hasAssignedVisit(Set<Long> elderIds, Long caregiverId) {
		return visit.hasAssignedVisit(elderIds, caregiverId).assigned();
	}

	@Override
	public List<Long> caregiverIdsForElder(Long elderId) {
		return visit.caregiverIdsForElder(elderId);
	}

}
