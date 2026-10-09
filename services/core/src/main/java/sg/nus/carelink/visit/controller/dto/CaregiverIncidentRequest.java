package sg.nus.carelink.visit.controller.dto;

import java.util.UUID;
import jakarta.validation.constraints.*;

public record CaregiverIncidentRequest(@NotNull @Positive Long visitId,
        @NotNull @Pattern(regexp="SOS|MEDICAL|FALL|SERVICE|OTHER") String category,
        @NotNull @Pattern(regexp="LOW|MEDIUM|HIGH") String severity,
        @NotBlank @Size(max=2000) String description,
        @NotNull @PositiveOrZero Integer expectedVersion, @NotNull UUID clientRequestId) {}
