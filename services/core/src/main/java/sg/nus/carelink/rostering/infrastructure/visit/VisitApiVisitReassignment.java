package sg.nus.carelink.rostering.infrastructure.visit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import sg.nus.carelink.rostering.domain.repository.VisitReassignment;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visitapi.VisitApi;
import sg.nus.carelink.visitapi.VisitNotFound;
import sg.nus.carelink.visitapi.VisitRuleViolation;

/**
 * {@link VisitReassignment} from visit's internal API. visit's 404 and 409 become the errors visit
 * threw when it ran in core, with the same code, so re-rostering answers the manager as before. A
 * change's request key goes in the {@value VisitApi#IDEMPOTENCY_KEY} header.
 *
 * <p>Each change is its own call, and so its own transaction in visit: a run that changes several
 * visits is not all-or-nothing. The re-rostering saga (VisitChangeSaga) keeps core and visit in step:
 * a refusal (409, 404) is final, and anything else - no connection, a read timeout, a 5xx, a 403 - is
 * passed on as it came, for the saga to send the change again with the same key.
 */
@Component
class VisitApiVisitReassignment implements VisitReassignment {

	private final VisitApi visit;

	VisitApiVisitReassignment(VisitApi visit) {
		this.visit = visit;
	}

	@Override
	public List<VisitSlot> unstartedFor(Long caregiverId, LocalDateTime from, LocalDateTime until) {
		return visit.unstartedFor(caregiverId, from, until).stream().map(VisitApiVisitReassignment::slot).toList();
	}

	@Override
	public Optional<VisitSlot> find(Long visitId) {
		return visit.findVisit(visitId).map(VisitApiVisitReassignment::slot);
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
	public void reassign(Long visitId, Long toCaregiverId, Change why, String requestKey) {
		translated(visitId, () -> {
			visit.reassign(visitId, new VisitApi.Reassignment(toCaregiverId, change(why)), requestKey);
			return null;
		});
	}

	@Override
	public void markUncovered(Long visitId, Change why, String requestKey) {
		translated(visitId, () -> {
			visit.markUncovered(visitId, change(why), requestKey);
			return null;
		});
	}

	@Override
	public void callOff(Long visitId, Change why, String requestKey) {
		translated(visitId, () -> {
			visit.callOff(visitId, change(why), requestKey);
			return null;
		});
	}

	@Override
	public Long moveTo(Long visitId, LocalDateTime newStart, Long toCaregiverId, Change why, String requestKey) {
		return translated(visitId,
				() -> visit.moveTo(visitId, new VisitApi.Move(newStart, toCaregiverId, change(why)), requestKey).visitId());
	}

	/** visit's answer, or its 404 and 409 as the errors visit throws in process. */
	private static <T> T translated(Long visitId, Supplier<T> call) {
		try {
			return call.get();
		}
		catch (VisitNotFound missing) {
			throw new ResourceNotFound("Visit", visitId);
		}
		catch (VisitRuleViolation refused) {
			throw new BusinessRuleViolation(refused.code(), refused.getMessage());
		}
	}

	private static VisitSlot slot(VisitApi.VisitSlot slot) {
		return new VisitSlot(slot.visitId(), slot.elderId(), slot.caregiverId(), slot.carePlanId(), slot.serviceType(),
				slot.start(), slot.end(), slot.status(), slot.absenceId());
	}

	private static VisitApi.Change change(Change why) {
		return new VisitApi.Change(why.absenceId(), why.byUserId(), why.rosteringCandidateId(), why.reason());
	}

}
