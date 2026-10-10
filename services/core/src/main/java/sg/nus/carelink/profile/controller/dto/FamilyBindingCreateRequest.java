package sg.nus.carelink.profile.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;

/**
 * Elder-side request for creating a family binding.
 */
public record FamilyBindingCreateRequest(

        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[a-z0-9][a-z0-9._-]{2,63}",
                message = "Use 3 to 64 lower-case letters, digits, dots, dashes or underscores")
        String familyUsername,

        @NotNull
        ElderFamilyBinding.Relationship relationship,

        boolean primaryContact,

        @NotNull
        ElderFamilyBinding.AccessScope accessScope
) {
    /** Usernames are lower-case, so "Lim.WeiLing " still finds "lim.weiling". */
    public FamilyBindingCreateRequest {
        familyUsername = familyUsername == null ? null : familyUsername.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
