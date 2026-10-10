package sg.nus.carelink.visit.controller.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CaregiverCheckOutRequest(@NotNull UUID clientRequestId,
        @NotNull @PositiveOrZero Integer expectedVersion) {}
