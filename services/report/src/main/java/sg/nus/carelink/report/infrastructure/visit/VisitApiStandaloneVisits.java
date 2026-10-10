package sg.nus.carelink.report.infrastructure.visit;

import java.util.Optional;

import org.springframework.stereotype.Component;
import sg.nus.carelink.report.application.StandaloneVisits;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.visitapi.VisitApi;
import sg.nus.carelink.visitapi.VisitRuleViolation;

/** {@link StandaloneVisits} from visit's internal API. */
@Component
class VisitApiStandaloneVisits implements StandaloneVisits {

	private final VisitApi visit;

	VisitApiStandaloneVisits(VisitApi visit) {
		this.visit = visit;
	}

	@Override
	public Long schedule(NewVisit planned) {
		try {
			return visit.scheduleStandalone(new VisitApi.NewVisit(planned.elderId(), planned.caregiverId(),
					planned.serviceType(), planned.start(), planned.end(), planned.instructions())).visitId();
		}
		catch (VisitRuleViolation refused) {
			throw new BusinessRuleViolation(refused.code(), refused.getMessage());
		}
	}

	@Override
	public Optional<State> find(Long visitId) {
		return visit.findVisitState(visitId)
				.map(state -> new State(state.visitId(), state.caregiverId(), state.status(), state.checkedIn()));
	}

}
