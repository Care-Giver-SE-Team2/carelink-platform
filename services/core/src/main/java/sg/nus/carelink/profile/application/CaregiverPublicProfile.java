package sg.nus.carelink.profile.application;

import java.util.List;

/**
 * Carries public caregiver details across application modules.
 *
 * @author Wang Zhili
 */
public record CaregiverPublicProfile(Long id, String fullName, List<String> dialects) {

	public CaregiverPublicProfile {
		dialects = List.copyOf(dialects);
	}
}
