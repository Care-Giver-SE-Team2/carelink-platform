package sg.nus.carelink.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

class ValueAddedServiceRequestTest {
    private static final LocalDateTime SCHEDULE = LocalDateTime.of(2026, 10, 10, 10, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 17, 0);

    @Test void elderRequestStartsPending() {
        var request = ValueAddedServiceRequest.requestedByElder(1L, 2L, SCHEDULE, "  Need help  ");
        assertThat(request.status()).isEqualTo(ValueAddedServiceRequest.Status.PENDING_APPROVAL);
        assertThat(request.specialInstructions()).isEqualTo("Need help");
        assertThat(request.visitId()).isNull();
    }

    @Test void blankInstructionsBecomeNull() {
        assertThat(ValueAddedServiceRequest.requestedByElder(1L, 2L, SCHEDULE, " ").specialInstructions()).isNull();
    }

    @Test void scheduleIsRequired() {
        assertThatThrownBy(() -> ValueAddedServiceRequest.requestedByElder(1L, 2L, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void approvalDispatchesAndStoresVisit() {
        var result = pending().approveAndDispatch(8L, 99L, NOW);
        assertThat(result.status()).isEqualTo(ValueAddedServiceRequest.Status.DISPATCHED);
        assertThat(result.approvingFamilyMemberId()).isEqualTo(8L);
        assertThat(result.visitId()).isEqualTo(99L);
        assertThat(result.decidedAt()).isEqualTo(NOW);
    }

    @Test void rejectionDoesNotCreateVisitReference() {
        var result = pending().reject(8L, NOW);
        assertThat(result.status()).isEqualTo(ValueAddedServiceRequest.Status.REJECTED);
        assertThat(result.visitId()).isNull();
    }

    @Test void decidedRequestCannotBeDecidedAgain() {
        var rejected = pending().reject(8L, NOW);
        assertThatThrownBy(() -> rejected.reject(8L, NOW.plusMinutes(1)))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test void pendingOrDispatchedRequestCanBeCancelled() {
        assertThat(pending().cancelled().status()).isEqualTo(ValueAddedServiceRequest.Status.CANCELLED);
        var dispatched = pending().approveAndDispatch(8L, 99L, NOW);
        var cancelled = dispatched.cancelled();
        assertThat(cancelled.status()).isEqualTo(ValueAddedServiceRequest.Status.CANCELLED);
        assertThat(cancelled.visitId()).as("the called-off visit stays on record").isEqualTo(99L);
        assertThat(cancelled.decidedAt()).isEqualTo(NOW);
    }

    @Test void rejectedOrCompletedRequestCannotBeCancelled() {
        assertThatThrownBy(() -> pending().reject(8L, NOW).cancelled())
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> pending().approveAndDispatch(8L, 99L, NOW).completed().cancelled())
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test void onlyDispatchedRequestCanBeCompleted() {
        assertThat(pending().approveAndDispatch(8L, 99L, NOW).completed().status())
                .isEqualTo(ValueAddedServiceRequest.Status.COMPLETED);
        assertThatThrownBy(() -> pending().completed()).isInstanceOf(BusinessRuleViolation.class);
    }

    private ValueAddedServiceRequest pending() {
        return new ValueAddedServiceRequest(5L, 1L, 2L, null, null, null, SCHEDULE, "Need help",
                ValueAddedServiceRequest.Status.PENDING_APPROVAL, null, NOW.minusDays(1), NOW.minusDays(1));
    }
}
