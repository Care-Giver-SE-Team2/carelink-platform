package sg.nus.carelink.profile.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import jakarta.validation.constraints.AssertTrue;

public record ElderAccountRegistrationRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{2,63}") String username,
        @NotBlank @Size(min = 8, max = 72) String password) {
    @AssertTrue(message = "Password must be at most 72 UTF-8 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public ElderAccountRegistrationRequest {
        username = username == null ? null : username.strip();
    }
}
