package sg.nus.carelink.careplan.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** The whole tree the manager built in the editor, snapshotted at the moment they publish. */
public record PublishCarePlanRequest(@NotNull LocalDate startDate, @NotEmpty @Valid List<PlanNodeRequest> nodes) {
}
