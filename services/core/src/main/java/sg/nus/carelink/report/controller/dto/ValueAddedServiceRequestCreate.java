package sg.nus.carelink.report.controller.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

public record ValueAddedServiceRequestCreate(
        @NotNull Long valueAddedServiceId,
        @NotNull LocalDateTime requestedSchedule,
        String specialInstructions) {
}
