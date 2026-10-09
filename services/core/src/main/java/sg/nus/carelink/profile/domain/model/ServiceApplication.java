package sg.nus.carelink.profile.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/** A service request for an existing elder, retaining the basic details at submission time. */
public record ServiceApplication(Long id, Long applicantFamilyMemberId, Long elderId,
        ElderBasicDetails elderSnapshot, List<String> careNeeds, String notes,
        Status status, LocalDateTime createdAt) {

    public ServiceApplication {
        careNeeds = List.copyOf(careNeeds);
    }

    public enum Status { SUBMITTED }

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
