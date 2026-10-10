package sg.nus.carelink.rostering.infrastructure.visit;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import sg.nus.carelink.platform.VisitOutsideCore;
import sg.nus.carelink.rostering.domain.model.VisitsAtRisk.Booking;
import sg.nus.carelink.rostering.domain.repository.BookedVisits;
import sg.nus.carelink.visitapi.VisitApi;

/**
 * {@link BookedVisits} from visit's internal API ({@code GET /internal/v1/visits/upcoming-assignments}),
 * once {@code carelink.visit-api.base-url} is set. The worked example of a core module calling
 * visit over HTTP: the port and the code that calls it stay, and only this class is new.
 */
@Component
@VisitOutsideCore
class VisitApiBookedVisits implements BookedVisits {

	private final VisitApi visit;

	VisitApiBookedVisits(VisitApi visit) {
		this.visit = visit;
	}

	@Override
	public List<Booking> unstartedBetween(LocalDateTime from, LocalDateTime until) {
		return visit.unstartedBetween(from, until).stream()
				.map(assignment -> new Booking(assignment.caregiverId(), assignment.carePlanId(), assignment.start()))
				.toList();
	}

}
