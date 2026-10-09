package sg.nus.carelink.profile.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/credentials/{id}/reject: the reason the caregiver is told. */
public record RejectCredentialRequest(@NotBlank @Size(max = 500) String reason) {
}
