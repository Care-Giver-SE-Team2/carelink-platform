package sg.nus.carelink.profile.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why the manager won't plan a service application; the family is shown it. */
public record DeclineServiceApplicationRequest(@NotBlank @Size(max = 255) String reason) {
}
