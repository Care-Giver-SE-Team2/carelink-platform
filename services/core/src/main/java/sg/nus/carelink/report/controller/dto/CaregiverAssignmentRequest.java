package sg.nus.carelink.report.controller.dto;

import jakarta.validation.constraints.NotNull;

public record CaregiverAssignmentRequest(@NotNull Long caregiverId) {
}
