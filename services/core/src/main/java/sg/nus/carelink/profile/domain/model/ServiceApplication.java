package sg.nus.carelink.profile.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * A service request for an existing elder, retaining the basic details at submission time. Whether
 * it has been planned is not stored: it is worked out from the elder's care plan versions (see
 * ServiceApplicationProgress). Only the manager's decision to decline it is recorded here.
 */
public record ServiceApplication(Long id, Long applicantFamilyMemberId, Long elderId,
        ElderBasicDetails elderSnapshot, List<String> careNeeds, String notes,
        Status status, LocalDateTime createdAt, Decline decline) {

    /** The family-facing reason is kept as short as review remarks elsewhere. */
    public static final int DECLINE_REASON_MAX = 255;

    public ServiceApplication {
        careNeeds = List.copyOf(careNeeds);
    }

    public ServiceApplication(Long id, Long applicantFamilyMemberId, Long elderId,
            ElderBasicDetails elderSnapshot, List<String> careNeeds, String notes,
            Status status, LocalDateTime createdAt) {
        this(id, applicantFamilyMemberId, elderId, elderSnapshot, careNeeds, notes, status, createdAt, null);
    }

    public enum Status { SUBMITTED, DECLINED }

    /** Who declined the application, when, and the reason the family is shown. */
    public record Decline(String reason, Long byUserId, LocalDateTime at) {
    }

    /**
     * The manager won't plan this request. A reason is required, since the family sees it; an
     * application is declined at most once. Whether it is already planned is checked by the caller
     * against the care plan (ServiceApplicationProgress.requireDeclinable), not here.
     */
    public ServiceApplication decline(String reason, Long byUserId, LocalDateTime at) {
        if (status == Status.DECLINED) {
            throw new BusinessRuleViolation("SERVICE_APPLICATION_ALREADY_DECLINED",
                    "Service application [%s] has already been declined".formatted(id));
        }
        if (blank(reason) || reason.strip().length() > DECLINE_REASON_MAX) {
            throw new BusinessRuleViolation("SERVICE_APPLICATION_DECLINE_REASON_REQUIRED",
                    "Give the family a reason of up to %d characters".formatted(DECLINE_REASON_MAX));
        }
        return new ServiceApplication(id, applicantFamilyMemberId, elderId, elderSnapshot, careNeeds, notes,
                Status.DECLINED, createdAt, new Decline(reason.strip(), byUserId, at));
    }

    public static ServiceApplication submit(Long familyId, Elder elder, List<String> careNeeds,
            String notes, LocalDateTime at) {
        // Check the saved profile rather than accepting a second, client-supplied identity or address.
        if (blank(elder.fullName()) || blank(elder.address()) || elder.postalCode() == null
                || !elder.postalCode().matches("[0-9]{6}")) {
            throw new BusinessRuleViolation("ELDER_PROFILE_INCOMPLETE",
                    "Complete the elder's full name, home address and six-digit postal code in My elders before applying.");
        }
        if (careNeeds == null || careNeeds.isEmpty() || careNeeds.stream().anyMatch(ServiceApplication::blank)) {
            throw new BusinessRuleViolation("CARE_NEEDS_REQUIRED", "Select or describe at least one care service.");
        }
        List<String> normalized = careNeeds.stream().map(String::strip).distinct().toList();
        var snapshot = new ElderBasicDetails(elder.fullName(), elder.gender(), elder.dateOfBirth(),
                elder.phone(), elder.address(), elder.postalCode(), elder.preferredDialects(),
                elder.livesAlone(), elder.mobilityLevel());
        return new ServiceApplication(null, familyId, elder.id(), snapshot, normalized,
                blank(notes) ? null : notes.strip(), Status.SUBMITTED, at);
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
