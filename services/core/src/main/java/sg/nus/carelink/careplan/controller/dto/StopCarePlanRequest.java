package sg.nus.carelink.careplan.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record StopCarePlanRequest(@NotNull LocalDate effectiveDate, @NotBlank String reason) {
}
