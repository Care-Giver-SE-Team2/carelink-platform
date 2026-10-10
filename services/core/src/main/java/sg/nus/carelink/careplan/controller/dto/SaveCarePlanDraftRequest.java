package sg.nus.carelink.careplan.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * The editor's work in progress on a draft: the start date so far (null until the manager picks
 * one) and the whole task list, which may be empty and whose tasks may not be scheduled yet.
 */
public record SaveCarePlanDraftRequest(LocalDate startDate, @NotNull @Valid List<PlanNodeRequest> nodes) {
}
