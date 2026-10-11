package sg.nus.carelink.report.infrastructure.visit;

import org.springframework.stereotype.Component;
import sg.nus.carelink.report.application.VisitReassignment;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visitapi.VisitApi;
import sg.nus.carelink.visitapi.VisitNotFound;
import sg.nus.carelink.visitapi.VisitRuleViolation;

/**
 * {@link VisitReassignment} from visit's internal API. visit's 404 and 409 become the errors visit
 * threw in process before, with the same code. report does not send a call-off again, so it sends no
 * request key and the call behaves as before.
 */
@Component
class VisitApiVisitReassignment implements VisitReassignment {

	private final VisitApi visit;

	VisitApiVisitReassignment(VisitApi visit) {
		this.visit = visit;
	}

	@Override
	public void callOff(Long visitId, Change why) {
		try {
			visit.callOff(visitId, new VisitApi.Change(why.absenceId(), why.byUserId(), why.rosteringCandidateId(),
					why.reason()), null);
		}
		catch (VisitNotFound missing) {
			throw new ResourceNotFound("Visit", visitId);
		}
		catch (VisitRuleViolation refused) {
			throw new BusinessRuleViolation(refused.code(), refused.getMessage());
		}
	}

}
