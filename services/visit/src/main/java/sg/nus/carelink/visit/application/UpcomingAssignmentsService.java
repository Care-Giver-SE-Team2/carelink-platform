package sg.nus.carelink.visit.application;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.repository.VisitRepository;

@Service
@Transactional(readOnly = true)
class UpcomingAssignmentsService implements UpcomingAssignments {

	private final VisitRepository visits;

	UpcomingAssignmentsService(VisitRepository visits) {
		this.visits = visits;
	}

	@Override
	public List<Assignment> unstartedBetween(LocalDateTime from, LocalDateTime until) {
		return visits.findScheduledBetween(from, until).stream()
				.filter(visit -> visit.caregiverId() != null && visit.hasNotStarted())
				.map(UpcomingAssignmentsService::toAssignment)
				.toList();
	}

	private static Assignment toAssignment(Visit visit) {
		return new Assignment(visit.id(), visit.caregiverId(), visit.carePlanId(), visit.scheduledStart());
	}
}
