package sg.nus.carelink.profile.controller.dto;

import jakarta.validation.constraints.NotNull;

/** Body of PUT /api/elders/{elderId}/primary-caregiver. */
public record PrimaryCaregiverRequest(@NotNull Long caregiverId) {
}
