package sg.nus.carelink.visit.domain.model;

import java.time.LocalDateTime;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/** State objects expose only the commands currently legal for the aggregate. */
public interface VisitExecutionState {
    default Visit arrive(Visit visit, LocalDateTime now) { throw notAllowed(); }
    default Visit start(Visit visit) { throw notAllowed(); }
    default void requireTaskResult() { throw notAllowed(); }
    private static BusinessRuleViolation notAllowed() { return new BusinessRuleViolation("VISIT_EXECUTION_NOT_ALLOWED", "This visit cannot currently execute this command."); }
}
