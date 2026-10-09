package sg.nus.carelink.visit.controller.dto;

import java.util.UUID;
import jakarta.validation.constraints.*;

public record CaregiverTaskResultRequest(@NotNull UUID clientRequestId,@NotNull @PositiveOrZero Integer expectedVersion,
        @NotNull @Pattern(regexp="DONE|SKIPPED|REFUSED") String status,@Size(max=255) String outcome,@Size(max=500) String caregiverNote) {
    @AssertTrue(message="Skipped or refused tasks require a reason")
    public boolean isValidReason() { return "DONE".equals(status) || caregiverNote!=null && !caregiverNote.isBlank(); }
}
