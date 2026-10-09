package sg.nus.carelink.profile.application;

import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;

/** A family member the elder is currently bound to, as the care plan's elder panel shows them. */
public record ElderFamilyContact(String fullName, ElderFamilyBinding.Relationship relationship,
		boolean primaryContact) {
}
