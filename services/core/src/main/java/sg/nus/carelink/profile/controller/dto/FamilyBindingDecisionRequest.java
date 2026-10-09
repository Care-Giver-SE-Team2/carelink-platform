package sg.nus.carelink.profile.controller.dto;

import jakarta.validation.constraints.NotNull;

public record FamilyBindingDecisionRequest(@NotNull Boolean approve) {}
