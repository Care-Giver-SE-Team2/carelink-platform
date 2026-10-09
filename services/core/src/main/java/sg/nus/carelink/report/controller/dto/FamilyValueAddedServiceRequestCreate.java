package sg.nus.carelink.report.controller.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FamilyValueAddedServiceRequestCreate(
        @NotNull Long elderId,
        @NotNull Long valueAddedServiceId,
        @NotNull LocalDateTime requestedSchedule,
        @Size(max = 1000) String specialInstructions) {
}
