package sg.nus.carelink.profile.controller.dto;

import java.time.LocalDateTime;

import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.domain.model.FamilyMember;

/**
 * Elder-facing representation of a family binding.
 */
public record FamilyBindingResponse(
        Long id,
        Long familyMemberId,
        String familyMemberName,
        ElderFamilyBinding.Relationship relationship,
        boolean primaryContact,
        ElderFamilyBinding.AccessScope accessScope,
        ElderFamilyBinding.Status status,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt
) {

    public static FamilyBindingResponse from(
            ElderFamilyBinding binding,
            FamilyMember familyMember) {

        return new FamilyBindingResponse(
                binding.id(),
                binding.familyMemberId(),
                familyMember.fullName(),
                binding.relationship(),
                binding.isPrimaryContact(),
                binding.accessScope(),
                binding.status(),
                binding.confirmedAt(),
                binding.createdAt()
        );
    }
}