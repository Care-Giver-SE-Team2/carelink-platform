package sg.nus.carelink.rostering.infrastructure.visit;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import sg.nus.carelink.platform.VisitInCore;
import sg.nus.carelink.rostering.domain.model.VisitsAtRisk.Booking;
import sg.nus.carelink.rostering.domain.repository.BookedVisits;
import sg.nus.carelink.visit.application.UpcomingAssignments;

/**
 * {@link BookedVisits} from visit's service, in the same process, while visit runs inside core.
 * Deleted when visit moves out; {@link VisitApiBookedVisits} takes over.
 */
@Component
@VisitInCore
class InProcessBookedVisits implements BookedVisits {

	private final UpcomingAssignments assignments;

	InProcessBookedVisits(UpcomingAssignments assignments) {
		this.assignments = assignments;
	}

	@Override
	public List<Booking> unstartedBetween(LocalDateTime from, LocalDateTime until) {
		return assignments.unstartedBetween(from, until).stream()
				.map(assignment -> new Booking(assignment.caregiverId(), assignment.carePlanId(), assignment.start()))
				.toList();
	}

}
