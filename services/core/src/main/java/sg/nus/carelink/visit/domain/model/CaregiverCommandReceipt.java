package sg.nus.carelink.visit.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

public record CaregiverCommandReceipt(Long actorUserId, UUID clientRequestId, String action,
        Long visitId, String payloadHash, Long resultId, Integer version, LocalDateTime occurredAt) {
    public void requireSame(String requestedAction, Long requestedVisit, String requestedHash) {
        if (!action.equals(requestedAction) || !visitId.equals(requestedVisit) || !payloadHash.equals(requestedHash)) {
            throw new BusinessRuleViolation("COMMAND_KEY_CONFLICT", "This command identifier was already used for different content.");
        }
    }
}
