package sg.nus.carelink.profile.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/** Admission decisions never change certificates or create visits. */
public record CaregiverApplication(Long id, Long caregiverId, Status status, int roundNumber,
        Details details, LocalDateTime submittedAt, Long reviewedByUserId,
        LocalDateTime reviewedAt, String reviewReason) {
    public enum Status { PENDING, APPROVED, REJECTED }
    public record Details(String fullName, String phone, String declaredDialects, String requestedSector) { }
    public record History(Long actorUserId, String action, long version,
            CaregiverApplication snapshot, LocalDateTime occurredAt) { }
    public record View(Long caregiverId, Long userId, String username, String workStatus, String phone, long version,
            CaregiverApplication application, List<History> history, boolean canWork, boolean canEdit,
            boolean canResubmit, String compatibility) { }
    public record Receipt(String payloadHash, View result) { }

    public CaregiverApplication edit(Details replacement) {
        if (status == Status.APPROVED) throw rule("APPLICATION_NOT_EDITABLE", "An approved application is immutable.");
        return new CaregiverApplication(id, caregiverId, status, roundNumber, replacement, submittedAt,
                reviewedByUserId, reviewedAt, reviewReason);
    }
    public CaregiverApplication resubmit(LocalDateTime now) {
        if (status != Status.REJECTED) throw rule("APPLICATION_NOT_REJECTED", "Only a rejected application can be resubmitted.");
        return new CaregiverApplication(id, caregiverId, Status.PENDING, roundNumber + 1, details, now, null, null, null);
    }
    public CaregiverApplication decide(boolean approved, Long reviewerId, String reason, LocalDateTime now) {
        if (status != Status.PENDING) throw rule("APPLICATION_ALREADY_REVIEWED", "Refresh: this application has already been reviewed.");
        String note = reason == null || reason.isBlank() ? null : reason.strip();
        if (!approved && note == null) throw rule("APPLICATION_REASON_REQUIRED", "A rejection reason is required.");
        if (note != null && note.length() > 500) throw rule("APPLICATION_REASON_TOO_LONG", "Use at most 500 characters.");
        return new CaregiverApplication(id, caregiverId, approved ? Status.APPROVED : Status.REJECTED,
                roundNumber, details, submittedAt, reviewerId, now, note);
    }
    private static BusinessRuleViolation rule(String code, String message) { return new BusinessRuleViolation(code, message); }
}
