package sg.nus.carelink.profile.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;

/**
 * Elder-side request for creating a family binding.
 */
public record FamilyBindingCreateRequest(

        @NotBlank
        @Size(max = 64)
        String familyUsername,

        @NotNull
        ElderFamilyBinding.Relationship relationship,

        boolean primaryContact,

        @NotNull
        ElderFamilyBinding.AccessScope accessScope
) {
}