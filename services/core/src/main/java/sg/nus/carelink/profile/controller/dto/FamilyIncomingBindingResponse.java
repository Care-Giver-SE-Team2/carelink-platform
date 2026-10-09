package sg.nus.carelink.profile.controller.dto;

import java.time.LocalDateTime;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;

public record FamilyIncomingBindingResponse(
        Long id, Long elderId, String elderName,
        ElderFamilyBinding.Relationship relationship,
        boolean primaryContact, ElderFamilyBinding.AccessScope accessScope,
        ElderFamilyBinding.Status status, LocalDateTime confirmedAt, LocalDateTime createdAt) {
    public static FamilyIncomingBindingResponse from(ElderFamilyBinding binding, String elderName) {
        return new FamilyIncomingBindingResponse(binding.id(), binding.elderId(), elderName,
                binding.relationship(), binding.isPrimaryContact(), binding.accessScope(),
                binding.status(), binding.confirmedAt(), binding.createdAt());
    }
}
