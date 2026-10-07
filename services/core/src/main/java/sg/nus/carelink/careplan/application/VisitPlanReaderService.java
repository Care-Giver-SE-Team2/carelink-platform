package sg.nus.carelink.careplan.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.careplan.domain.repository.CarePlanRepository;
import sg.nus.carelink.careplan.domain.repository.CarePlanNodeRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

@Service
@Transactional(readOnly = true)
class VisitPlanReaderService implements VisitPlanReader {
    private final CarePlanRepository plans;
    private final CarePlanNodeRepository nodes;
    VisitPlanReaderService(CarePlanRepository plans, CarePlanNodeRepository nodes) {
        this.plans = plans;
        this.nodes = nodes;
    }
    public Snapshot read(Long planId, Long elderId) {
        var plan = plans.findById(planId).orElseThrow(() -> new ResourceNotFound("Visit care plan", planId));
        if (!elderId.equals(plan.elderId()) || plan.publishedAt() == null) {
            throw new BusinessRuleViolation("VISIT_PLAN_UNAVAILABLE", "This visit does not reference a published plan for its elder.");
        }
        return new Snapshot(plan.id(), plan.version(), nodes.findByCarePlanId(planId).stream()
                .map(n -> new Task(n.id(), n.name(), n.evidenceType() == null ? "NONE" : n.evidenceType().name())).toList());
    }
}
