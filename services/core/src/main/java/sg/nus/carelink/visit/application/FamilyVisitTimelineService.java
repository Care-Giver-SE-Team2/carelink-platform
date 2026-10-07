package sg.nus.carelink.visit.application;

import java.util.List;

import org.springframework.stereotype.Service;

import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.FamilyReadAudit;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.model.VisitStateTransition;
import sg.nus.carelink.visit.domain.repository.VisitRepository;
import sg.nus.carelink.visit.domain.repository.VisitStateTransitionRepository;

/**
 * Reads applied history under the family's current binding and audits the outcome.
 *
 * @author Wang Zhili
 */
@Service
public class FamilyVisitTimelineService {

	private final VisitRepository visits;
	private final VisitStateTransitionRepository transitions;
	private final FamilyAccessQuery access;
	private final FamilyReadAudit audit;

	public FamilyVisitTimelineService(VisitRepository visits, VisitStateTransitionRepository transitions,
			FamilyAccessQuery access, FamilyReadAudit audit) {
		this.visits = visits;
		this.transitions = transitions;
		this.access = access;
		this.audit = audit;
	}

	/** Checks the parent visit even when no transitions have been stored. */
	public List<VisitStateTransition> findTimeline(String username, Long visitId) {
		return audit.read(username, FamilyReadAudit.Resource.VISIT_TIMELINE, visitId, "", () -> {
			var visit = visits.findById(visitId).orElseThrow(() -> new ResourceNotFound("Visit", visitId));
			access.requireReadableElder(username, visit.elderId());
			return transitions.findAppliedByVisitId(visitId);
		});
	}
}
