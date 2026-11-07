package sg.nus.carelink.report.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sg.nus.carelink.report.domain.model.ValueAddedService;

/** What a manager may change about a service: everything but its name. */
public record ValueAddedServiceUpdate(
        @Size(max = 1000, message = "Use 1000 characters or fewer.")
        String description,
        @NotNull(message = "Enter how long it lasts.")
        @Min(value = 1, message = "It must last at least a minute.")
        @Max(value = ValueAddedService.MAX_DURATION_MINUTES, message = "It can last 12 hours at most.")
        Integer durationMinutes,
        @NotNull(message = "Choose available or unavailable.")
        ValueAddedService.Status status) {
}
