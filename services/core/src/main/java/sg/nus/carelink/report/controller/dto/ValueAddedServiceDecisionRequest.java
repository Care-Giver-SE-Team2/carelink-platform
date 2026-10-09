package sg.nus.carelink.report.controller.dto;

import jakarta.validation.constraints.NotNull;
import sg.nus.carelink.report.application.ValueAddedServiceRequestService.Decision;

public record ValueAddedServiceDecisionRequest(@NotNull Decision decision) {
}
