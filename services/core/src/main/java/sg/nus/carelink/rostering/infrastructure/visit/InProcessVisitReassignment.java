package sg.nus.carelink.rostering.infrastructure.visit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import sg.nus.carelink.platform.VisitInCore;
import sg.nus.carelink.rostering.domain.repository.VisitReassignment;

/**
 * {@link VisitReassignment} from visit's service, in the same process, while visit runs inside
 * core. Deleted when visit moves out; {@link VisitApiVisitReassignment} takes over.
 */
@Component
@VisitInCore
class InProcessVisitReassignment implements VisitReassignment {

	private final sg.nus.carelink.visit.application.VisitReassignment visit;

	InProcessVisitReassignment(sg.nus.carelink.visit.application.VisitReassignment visit) {
		this.visit = visit;
	}

	@Override
	public List<VisitSlot> unstartedFor(Long caregiverId, LocalDateTime from, LocalDateTime until) {
		return visit.unstartedFor(caregiverId, from, until).stream().map(InProcessVisitReassignment::slot).toList();
	}

	@Override
	public Optional<VisitSlot> find(Long visitId) {
		return visit.find(visitId).map(InProcessVisitReassignment::slot);
	}

	@Override
	public List<Booking> bookingsBetween(LocalDateTime from, LocalDateTime until) {
		return visit.bookingsBetween(from, until).stream()
				.map(booking -> new Booking(booking.visitId(), booking.elderId(), booking.caregiverId(), booking.start(),
						booking.end()))
				.toList();
	}

	@Override
	public Map<Long, Integer> finishedVisitsWith(Long elderId, LocalDateTime since, LocalDateTime until) {
		return visit.finishedVisitsWith(elderId, since, until);
	}

	@Override
	public void reassign(Long visitId, Long toCaregiverId, Change why) {
		visit.reassign(visitId, toCaregiverId, change(why));
	}

	@Override
	public void markUncovered(Long visitId, Change why) {
		visit.markUncovered(visitId, change(why));
	}

	@Override
	public void callOff(Long visitId, Change why) {
		visit.callOff(visitId, change(why));
	}

	@Override
	public Long moveTo(Long visitId, LocalDateTime newStart, Long toCaregiverId, Change why) {
		return visit.moveTo(visitId, newStart, toCaregiverId, change(why));
	}

	private static VisitSlot slot(sg.nus.carelink.visit.application.VisitReassignment.VisitSlot slot) {
		return new VisitSlot(slot.visitId(), slot.elderId(), slot.caregiverId(), slot.carePlanId(), slot.serviceType(),
				slot.start(), slot.end(), slot.status(), slot.absenceId());
	}

	private static sg.nus.carelink.visit.application.VisitReassignment.Change change(Change why) {
		return new sg.nus.carelink.visit.application.VisitReassignment.Change(why.absenceId(), why.byUserId(),
				why.rosteringCandidateId(), why.reason());
	}

}
