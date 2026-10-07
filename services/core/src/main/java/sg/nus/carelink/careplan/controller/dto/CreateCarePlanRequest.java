package sg.nus.carelink.careplan.controller.dto;

import jakarta.validation.constraints.NotNull;

public record CreateCarePlanRequest(@NotNull Long elderId) {
}
