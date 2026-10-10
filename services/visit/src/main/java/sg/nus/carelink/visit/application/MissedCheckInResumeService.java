package sg.nus.carelink.visit.application;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import sg.nus.carelink.incident.application.MissedCheckInPauseEvidence;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.repository.MissedCheckInRepository;
import sg.nus.carelink.visit.domain.repository.VisitStateTransitionRepository;
import sg.nus.carelink.visit.domain.service.MissedCheckInResumePolicy;

/** GET checks never mutate. The check-in command rechecks this evidence while holding the Visit lock. */
@Service
public class MissedCheckInResumeService {
    private final MissedCheckInRepository triggers;
    private final VisitStateTransitionRepository transitions;
    private final MissedCheckInPauseEvidence incidents;
    public MissedCheckInResumeService(MissedCheckInRepository triggers,
            VisitStateTransitionRepository transitions, MissedCheckInPauseEvidence incidents) {
        this.triggers = triggers; this.transitions = transitions; this.incidents = incidents;
    }
    public boolean permits(Visit visit) {
        if (visit.status() != Visit.Status.EXCEPTION) return false;
        var trigger = triggers.find(visit.id());
        return trigger.isPresent() && MissedCheckInResumePolicy.permits(visit, trigger.get(),
                transitions.findAppliedByVisitId(visit.id()),
                incidents.isSoleIncident(visit.elderId(), visit.id(), trigger.get().incidentId()));
    }
    public Visit arrive(Visit visit, LocalDateTime now) {
        if (!permits(visit)) throw new BusinessRuleViolation("VISIT_EXECUTION_NOT_ALLOWED",
                "This exception cannot safely resume through late check-in. Contact your manager.");
        return visit.arrivedAfterMissedCheckIn(now);
    }
}
