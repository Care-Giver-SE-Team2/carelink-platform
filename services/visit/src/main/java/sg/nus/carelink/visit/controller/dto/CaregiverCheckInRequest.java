package sg.nus.carelink.visit.controller.dto;

import java.util.UUID;
import jakarta.validation.constraints.*;

public record CaregiverCheckInRequest(@NotNull UUID clientRequestId, @NotNull @PositiveOrZero Integer expectedVersion) {}
