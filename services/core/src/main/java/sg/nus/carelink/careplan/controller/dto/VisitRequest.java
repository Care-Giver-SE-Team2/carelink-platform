package sg.nus.carelink.careplan.controller.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * One scheduled day for a task, e.g. {"day": "Mon", "startTime": "08:00", "minutes": 30}. The end
 * time is start + minutes; clients work it out rather than it being sent.
 */
public record VisitRequest(@NotBlank String day, @NotNull LocalTime startTime, @NotNull @Min(1) Integer minutes) {
}
