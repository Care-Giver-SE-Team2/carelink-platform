package sg.nus.carelink.profile.application;

/**
 * Published after an elder sends a family member a binding request, or sends one again after it
 * was rejected or revoked, so the family member is told there is a request waiting for them.
 */
public record FamilyBindingRequested(Long bindingId, Long elderId, Long familyMemberId) {
}
