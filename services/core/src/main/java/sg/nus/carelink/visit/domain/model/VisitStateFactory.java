package sg.nus.carelink.visit.domain.model;

import java.time.LocalDateTime;
import java.util.Set;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

public final class VisitStateFactory {
    private VisitStateFactory() {}
    private static final Set<String> LEGACY_BASIC_TYPES = Set.of("A", "CARE", "PERSONAL_CARE", "DEMO_MORNING_CARE", "DEMO_COMPANIONSHIP", "DEMO_REASSIGNED_CARE");
    public static VisitExecutionState forVisit(Visit visit) {
        // MG03 stores the task NAME (possibly truncated) as service_type, not a clinical
        // taxonomy. A validated assigned plan node selects the plan-task basic strategy;
        // never infer clinical rules from the displayed text. A standalone visit (an extra
        // service's work order) runs the same basic strategy on its one service task.
        if (visit.carePlanNodeId() == null && !visit.standalone() && !LEGACY_BASIC_TYPES.contains(visit.serviceType() == null ? "" : visit.serviceType())) {
            throw new BusinessRuleViolation("VISIT_SERVICE_UNSUPPORTED", "This service has no supported execution strategy. Ask your manager.");
        }
        return switch (visit.status()) {
            case SCHEDULED -> Scheduled.INSTANCE;
            case ARRIVED -> Arrived.INSTANCE;
            case IN_PROGRESS -> InProgress.INSTANCE;
            default -> Blocked.INSTANCE;
        };
    }
    private enum Scheduled implements VisitExecutionState {
        INSTANCE;
        @Override
        public Visit arrive(Visit visit, LocalDateTime now) { return visit.arrivedAt(now); }
    }
    private enum Arrived implements VisitExecutionState {
        INSTANCE;
        @Override
        public Visit start(Visit visit) { return visit.started(); }
    }
    private enum InProgress implements VisitExecutionState {
        INSTANCE;
        @Override
        public void requireTaskResult() { /* The state permits this command; task rules still apply. */ }
    }
    private enum Blocked implements VisitExecutionState { INSTANCE }
}
